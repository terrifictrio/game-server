package io.infectnet.server.engine.core;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.infectnet.server.engine.core.entity.wrapper.Action;
import io.infectnet.server.engine.core.script.Request;
import io.infectnet.server.engine.core.script.code.Code;
import io.infectnet.server.engine.core.script.code.CodeRepository;
import io.infectnet.server.engine.core.script.execution.ScriptExecutor;
import io.infectnet.server.engine.core.status.StatusConsumer;
import io.infectnet.server.engine.core.status.StatusPublisher;
import io.infectnet.server.engine.core.util.ListenableQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class around the heart of the engine, the game loop. The actual loop is executed on a
 * separate thread which can be started and stopped by methods of this class.
 */
public class GameLoop {

  private static final Logger logger = LoggerFactory.getLogger(GameLoop.class);

  private static final long NO_DELAY = 0L;

  private final ListenableQueue<Action> actionQueue;

  private final ListenableQueue<Request> requestQueue;

  private final CodeRepository codeRepository;

  private final ScriptExecutor scriptExecutor;

  private final StatusPublisher statusPublisher;

  private ScheduledExecutorService gameLoopExecutorService;

  private Duration desiredTickDuration;

  private AtomicBoolean isLoopRunning;

  private StatusConsumer statusConsumer;

  /**
   * Constructs a new instance that works on the specified queues and executes the code pulled from
   * the specified {@code CodeRepository} with the passes {@code ScriptExecutor}.
   * @param actionQueue the queue that stores the {@code Action}s to be processed
   * @param requestQueue the queue in which the {@code Request}s will be put and will be pulled
   * from
   * @param codeRepository the repository storing the codes submitted by the {@link
   * io.infectnet.server.engine.core.player.Player}s
   * @param scriptExecutor the executor that will run the DSL code
   * @param statusPublisher publisher service responsible for sending out updates
   */
  public GameLoop(ListenableQueue<Action> actionQueue, ListenableQueue<Request> requestQueue,
                  CodeRepository codeRepository, ScriptExecutor scriptExecutor,
                  StatusPublisher statusPublisher) {
    this.actionQueue = actionQueue;

    this.requestQueue = requestQueue;

    this.codeRepository = codeRepository;

    this.scriptExecutor = scriptExecutor;

    this.statusPublisher = statusPublisher;

    this.isLoopRunning = new AtomicBoolean(false);
  }

  /**
   * Sets the consumer this loop will call when a tick has finished execution.
   * @param statusConsumer the consumer to be used
   * @throws NullPointerException if the passed consumer is {@code null}
   */
  public void setStatusConsumer(StatusConsumer statusConsumer) {
    this.statusConsumer = Objects.requireNonNull(statusConsumer);
  }

  /**
   * Starts the game loop in a separate thread. Subsequent invocations of this method have no
   * effect.
   * @param desiredTickDuration the minimal duration between the ticks of the game loop
   * @throws IllegalStateException if the status consumer has not been set
   */
  public void start(long desiredTickDuration) {
    if (isLoopRunning.get()) {
      return;
    }

    if (statusConsumer == null) {
      throw new IllegalStateException("StateConsumer member wasn't set before start!");
    }

    setDesiredTickDuration(Duration.ofMillis(desiredTickDuration));

    gameLoopExecutorService = Executors.newSingleThreadScheduledExecutor();

    isLoopRunning.set(true);

    logger.info("Game loop started!");

    gameLoopExecutorService.schedule(this::loop, NO_DELAY, MILLISECONDS);
  }

  /**
   * Requests the game loop to stop and returns immediately. Please be aware that the thread which
   * the game loop is being executed on might not be shut down immediately.
   * <p>
   * <b>Note</b> that even though the game loop might not stop immediately, {@link #isRunning()}
   * will return {@code false} after calling this method.
   * </p>
   */
  public void stop() {
    if (!isLoopRunning.get()) {
      return;
    }

    gameLoopExecutorService.shutdown();

    isLoopRunning.set(false);

    logger.info("Game loop stopped asynchronously!");
  }

  /**
   * Requests the game loop to stop and blocks. This method waits for the game loop to stop and
   * returns whether the shut down process was successful. The interrupt status of the thread this
   * method is called from is preserved and can be inspected, if the thread was interrupted while
   * waiting for the game loop to shut down.
   * <p>
   * <b>Note</b> that even though the game loop might not be stopped, {@link #isRunning()}will
   * return {@code false} after calling this method.
   * </p>
   * @return whether the game loop was successfully terminated
   */
  public boolean stopAndWait() {
    if (!isLoopRunning.get()) {
      return true;
    }

    isLoopRunning.set(false);

    /*
     * New tasks will not be accepted therefore we have to wait for 0 to 1 game ticks to complete
     * at maximum.
     */
    gameLoopExecutorService.shutdown();

    try {
      /*
       * Wait three game ticks. This must be enough is most cases.
       */
      if (!gameLoopExecutorService
          .awaitTermination(3 * desiredTickDurationMillis(), MILLISECONDS)) {
        /*
         * Force shutdown by cancelling the currently executed task.
         */
        gameLoopExecutorService.shutdownNow();

        logger.info("Waiting to game loop to be stopped...");

        /*
         * Wait a game tick again, just to be sure.
         */
        return gameLoopExecutorService.awaitTermination(desiredTickDurationMillis(), MILLISECONDS);
      }

      return true;
    } catch (InterruptedException e) {
      /*
       *  Re-cancel, if interrupted while waiting.
       */
      gameLoopExecutorService.shutdownNow();

      /*
       * Preserve interrupt status, so callers can inspect it.
       */
      Thread.currentThread().interrupt();

      logger.warn("Game loop stop was interrupted!");

      return false;
    }
  }

  /**
   * Gets whether the game loop is running.
   * @return {@code true} if the game loop is running, {@code false} otherwise
   */
  public boolean isRunning() {
    return isLoopRunning.get();
  }

  private long desiredTickDurationMillis() {
    return desiredTickDuration.toMillis();
  }

  private void loop() {
    Instant startTime = Instant.now();

    /*
     * #1 Run Scripts
     *
     * Execute the DSL code written by the players. The action queue will be filled with Action
     * instances created by the executed Scripts.
     */
    for (Code code : codeRepository.getAllCodes()) {
      if (code.isRunnable()) {
        try {
          scriptExecutor.execute(code.getScript().get(), code.getOwner());
        } catch (Exception e) {
          logger.warn("Exception during player ({}) code execution: {}", code.getOwner(),
              e);
        }
      }
    }

    /*
     * #2 Process Actions
     *
     * The Actions will be dispatched towards the various systems via the registered listeners. The
     * systems will create Requests and place them in the request queue.
     * Actions and Requests are separated because Actions depend on the previous state of the World
     * as well as they may operate on more than one Entity.
     */
    actionQueue.processAll();

    /*
     * #3 Process Requests
     *
     * Requests crafted from Actions have no dependencies on the previous state of the World or the
     * Entity System. The request queue dispatches the requests to the various systems which will
     * modify the World and the Entities.
     */
    requestQueue.processAll();

    /*
     * #4 Send results
     *
     */
    statusPublisher.publish(statusConsumer);

    /*
     * #5 Reschedule Loop
     *
     * Once we're done with all our processing job, we will reschedule ourselves with respect to the
     * desired tick duration. If we've failed to deliver, the rescheduling will mean an instant
     * execution. This is exactly an infinite loop.
     */
    rescheduleLoop(startTime);
  }

  private void rescheduleLoop(Instant startTime) {
    Instant endTime = Instant.now();

    Duration actualTickDuration = Duration.between(startTime, endTime);

    Duration waitTime = desiredTickDuration.minus(actualTickDuration);

    logger.info("Tick time: {}", actualTickDuration);
    logger.info("Next tick after: {}", waitTime);

    /*
     * If the current tick was longer than desired, we will schedule the loop with no delay.
     *
     * Otherwise we will wait for the time between the actual and desired tick duration.
     */
    if (waitTime.isNegative()) {
      gameLoopExecutorService.schedule(this::loop, NO_DELAY, MILLISECONDS);
    } else {
      gameLoopExecutorService.schedule(this::loop, waitTime.toMillis(), MILLISECONDS);
    }
  }

  /**
   * Sets the desired duration of a game tick. This is the <b>minimal</b> length of a game tick,
   * it's guaranteed, that no game tick will be shorter than this duration. However, upon high load
   * or too many calculations, game ticks might get longer and longer.
   * <p>
   * Can only be set before starting the game loop.
   * </p>
   * @param desiredTickDuration the desired/minimal duration of a game tick
   * @throws NullPointerException if the duration is {@code null}
   * @throws IllegalArgumentException if the duration is negative
   * @throws IllegalStateException if the game loop is currently running
   */
  private void setDesiredTickDuration(Duration desiredTickDuration) {
    Duration duration = Objects.requireNonNull(desiredTickDuration);

    if (duration.isNegative()) {
      throw new IllegalArgumentException("The duration must not be negative!");
    }

    if (isLoopRunning.get()) {
      throw new IllegalStateException("Cannot modify duration while the loop is running!");
    }

    this.desiredTickDuration = duration;
  }
}
