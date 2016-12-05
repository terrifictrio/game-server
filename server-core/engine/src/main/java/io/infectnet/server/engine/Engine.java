package io.infectnet.server.engine;

import groovy.lang.Script;
import io.infectnet.server.engine.content.configuration.ContentModule;
import io.infectnet.server.engine.core.GameLoop;
import io.infectnet.server.engine.core.configuration.CoreModule;
import io.infectnet.server.engine.core.player.Player;
import io.infectnet.server.engine.core.player.PlayerService;
import io.infectnet.server.engine.core.script.code.Code;
import io.infectnet.server.engine.core.script.code.CodeRepository;
import io.infectnet.server.engine.core.script.generation.CompilationError;
import io.infectnet.server.engine.core.script.generation.ScriptGenerationFailedException;
import io.infectnet.server.engine.core.script.generation.ScriptGenerator;
import io.infectnet.server.engine.core.status.StatusConsumer;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import dagger.Component;

public class Engine {

  private static final Logger logger = LoggerFactory.getLogger(Engine.class);

  private final Bootstrapper bootstrapper;

  @Singleton
  @Component(modules = {CoreModule.class, ContentModule.class})
  interface Bootstrapper {

    EngineConfigurator getEngineConfigurator();

    GameLoop getGameLoop();

    CodeRepository getCodeRepository();

    ScriptGenerator getScriptGenerator();

    PlayerService getPlayerService();

  }

  public static Engine create(StatusConsumer statusConsumer) {
    return new Engine(Objects.requireNonNull(statusConsumer));
  }

  /**
   * Cannot be instantiated directly.
   */
  private Engine(StatusConsumer statusConsumer) {
    this.bootstrapper = DaggerEngine_Bootstrapper.create();

    bootstrapper.getGameLoop().setStatusConsumer(statusConsumer);

    EngineConfigurator configurator = DaggerEngine_Bootstrapper.create().getEngineConfigurator();

    configurator.configure();
  }

  /**
   * Starts the game engine.
   */
  public void start(long desiredTickDuration) {
    bootstrapper.getGameLoop().start(desiredTickDuration);
  }

  /**
   * Stops the game engine and wait until it is done.
   * @return true if the engine stops, false otherwise
   */
  public boolean stopBlocking() {
    return bootstrapper.getGameLoop().stopAndWait();
  }

  /**
   * Stops the game engine asynchronously.
   * @return always true
   */
  public boolean stopAsync() {
    bootstrapper.getGameLoop().stop();
    return true;
  }

  /**
   * Compiles and stores the compiled script under the name of the player.
   * The source will be stored even if it cannot be compiled.
   * @param player the player who uploaded the source
   * @param source the source to be compiled
   */
  public List<CompilationError> compileAndUploadForPlayer(Player player, String source) {
    try {
      Script script = bootstrapper.getScriptGenerator().generateFromCode(source);

      bootstrapper.getCodeRepository().addCode(player, new Code(player, source, script));

      return Collections.emptyList();

    } catch (ScriptGenerationFailedException e) {
      bootstrapper.getCodeRepository().addCode(player, new Code(player, source));

      return getErrorListFromScriptGeneration(e);
    }
  }

  /**
   * Returns a list of compilation errors extracted from the given exception.
   * @param e the exception generated by Groovy compilation
   */
  private List<CompilationError> getErrorListFromScriptGeneration(
      ScriptGenerationFailedException e) {
    if (e.getCause() instanceof MultipleCompilationErrorsException) {
      MultipleCompilationErrorsException
          compilationErrorsException =
          (MultipleCompilationErrorsException) e.getCause();

      /*
      * The safety of this cast is guaranteed by the implementation of the addError method
      * in the ErrorCollector class.
      */
      @SuppressWarnings("unchecked")
      List<Message> syntaxExceptions =
          compilationErrorsException.getErrorCollector().getErrors();

      return syntaxExceptions.stream()
          .filter(message -> message instanceof SyntaxErrorMessage)
          .map(message -> (SyntaxErrorMessage) message)
          .map(SyntaxErrorMessage::getCause)
          .map(syntaxException -> new CompilationError(
              syntaxException.getLine(),
              syntaxException.getStartColumn(),
              syntaxException.getMessage())
          )
          .collect(Collectors.toList());

    } else {
      logger.warn("Compilation errors couldn't be mapped!");

      throw new IllegalStateException("Compilation errors couldn't be mapped");
    }
  }

  /**
   * Gets the specified player's current source code.
   * @param player the owner player
   * @return the source code
   */
  public Optional<String> getSourceCodeForPlayer(Player player) {
    Optional<Code> userCode = bootstrapper.getCodeRepository().getCodeByPlayer(player);

    return userCode.map(Code::getSource);
  }

  /**
   * Returns a {@link Player} with the specified player name and if necessary creates it.
   * @param playerName the player's name
   * @return the player
   */
  public Player createOrGetPlayer(String playerName) {

    Optional<Player> player = bootstrapper.getPlayerService().getPlayerByUsername(playerName);

    if (!player.isPresent()) {

      player = bootstrapper.getPlayerService().createPlayer(playerName);

      if (!player.isPresent()) {
        logger.warn("Player cannot be created with name: {}", playerName);

        throw new IllegalArgumentException("Player cannot be created with name: " + playerName);
      }

    }

    return player.get();
  }

  /**
   * Sets the {@code Player} as observed. For the meaning of being observed, please refer to
   * {@link PlayerService#isPlayerObserved(Player)}.
   * @param player the {@code Player} to be set as observed
   */
  public void setPlayerAsObserved(Player player) {
    bootstrapper.getPlayerService().setPlayerAsObserved(player);
  }

  /**
   * Removes the {@code Player} from the list of observed {@code Player}s. For the meaning of being
   * observed, please refer to {@link PlayerService#isPlayerObserved(Player)}.
   * @param player the {@code Player} to be removed
   */
  public void removePlayerFromObserved(Player player) {
    bootstrapper.getPlayerService().removePlayerFromObserved(player);
  }

}
