package io.infectnet.server.engine.content.system.spawn;


import io.infectnet.server.engine.content.system.creation.EntityCreationRequest;
import io.infectnet.server.engine.core.entity.Category;
import io.infectnet.server.engine.core.entity.EntityManager;
import io.infectnet.server.engine.core.entity.component.TypeComponent;
import io.infectnet.server.engine.core.entity.type.TypeRepository;
import io.infectnet.server.engine.core.entity.wrapper.Action;
import io.infectnet.server.engine.core.script.Request;
import io.infectnet.server.engine.core.system.ActionOnlyProcessor;
import io.infectnet.server.engine.core.util.ListenableQueue;
import io.infectnet.server.engine.core.world.World;

import java.util.Optional;

public class SpawnSystem extends ActionOnlyProcessor {

  private final ListenableQueue<Request> requestQueue;

  private final TypeRepository typeRepository;

  private final EntityManager entityManager;

  private final World world;

  public SpawnSystem(ListenableQueue<Request> requestQueue, TypeRepository typeRepository,
                     EntityManager entityManager, World world) {
    this.requestQueue = requestQueue;
    this.typeRepository = typeRepository;
    this.entityManager = entityManager;
    this.world = world;
  }

  @Override
  public void registerActionListeners(ListenableQueue<Action> actionQueue) {
    actionQueue.addListener(SpawnAction.class, this::consumeSpawnAction);
  }

  private void consumeSpawnAction(Action action) {
    SpawnAction spawnAction = (SpawnAction) action;

    Optional<TypeComponent> entityTypeComponent =
        typeRepository.getTypeByName(spawnAction.getEntityType())
            .filter(typeComponent -> typeComponent.getCategory() == Category.FIGHTER
                || typeComponent.getCategory() == Category.WORKER);

    entityTypeComponent.ifPresent((typeComponent) -> {
      requestQueue.add(new EntityCreationRequest(spawnAction.getSource(), spawnAction, typeComponent));
    });

    //TODO: incorrect type name was given?
  }

}
