package io.infectnet.server.engine.content.wrapper

import io.infectnet.server.engine.content.traits.BootTrait
import io.infectnet.server.engine.content.traits.InfectTrait
import io.infectnet.server.engine.content.traits.MoveTrait
import io.infectnet.server.engine.core.entity.Entity
import io.infectnet.server.engine.core.entity.wrapper.Action
import io.infectnet.server.engine.core.entity.wrapper.EntityWrapper
import io.infectnet.server.engine.core.traits.InventoryTrait

import java.util.function.BiConsumer


class WormWrapper extends EntityWrapper implements InfectTrait, MoveTrait, BootTrait, InventoryTrait {

  WormWrapper(Entity wrappedEntity, BiConsumer<io.infectnet.server.engine.core.entity.wrapper.EntityWrapper.WrapperState, Action> actionConsumer) {
    super(wrappedEntity, actionConsumer)
  }

}
