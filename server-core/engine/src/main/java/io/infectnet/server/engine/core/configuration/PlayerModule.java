package io.infectnet.server.engine.core.configuration;

import io.infectnet.server.engine.core.player.Player;
import io.infectnet.server.engine.core.player.PlayerService;
import io.infectnet.server.engine.core.player.PlayerServiceImpl;
import io.infectnet.server.engine.core.player.storage.PlayerStorageService;
import io.infectnet.server.engine.core.player.storage.PlayerStorageServiceImpl;

import java.util.function.Function;
import javax.inject.Singleton;
import dagger.Module;
import dagger.Provides;

@Module
public class PlayerModule {
  @Provides
  @Singleton
  public static PlayerService providesPlayerService(Function<Player, Player> playerInitializer,
                                                    PlayerStorageService playerStorageService) {
    return new PlayerServiceImpl(playerInitializer, playerStorageService);
  }

  @Provides
  @Singleton
  public static PlayerStorageService providesPlayerStorageService() {
    return new PlayerStorageServiceImpl();
  }
}
