package io.infectnet.server.controller.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.infectnet.server.controller.rest.RestController;
import io.infectnet.server.controller.rest.admin.AuthenticationController;
import io.infectnet.server.controller.rest.exception.ExceptionMapperController;
import io.infectnet.server.controller.rest.info.InfoController;
import io.infectnet.server.controller.rest.token.TokenController;
import io.infectnet.server.controller.rest.user.RegistrationController;
import io.infectnet.server.controller.rest.user.RegistrationDetails;
import io.infectnet.server.controller.rest.user.UserDTOSerializer;
import io.infectnet.server.controller.rest.user.UserListingController;
import io.infectnet.server.controller.utils.json.DateTimeJsonSerializer;
import io.infectnet.server.controller.websocket.messaging.SocketMessage;
import io.infectnet.server.service.admin.AuthenticationService;
import io.infectnet.server.service.configuration.ServiceModule;
import io.infectnet.server.service.token.TokenService;
import io.infectnet.server.service.user.UserDTO;
import io.infectnet.server.service.user.UserService;

import java.time.LocalDateTime;
import javax.inject.Singleton;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes = { ServiceModule.class, WebSocketModule.class })
public class ControllerModule {

  @Provides
  @Singleton
  public static Gson providesGson() {
    GsonBuilder gsonBuilder = new GsonBuilder();

    gsonBuilder = setupTypeAdapters(gsonBuilder);

    return gsonBuilder.create();
  }

  @Provides
  @Singleton
  public static ExceptionMapperController providesExceptionMapperController(Gson gson) {
    return new ExceptionMapperController(gson);
  }

  @Provides
  @IntoSet
  @Singleton
  public static RestController providesTokenController(TokenService tokenService, Gson gson) {
    return new TokenController(tokenService, gson);
  }

  @Provides
  @IntoSet
  @Singleton
  public static RestController providesRegistrationController(UserService userService, Gson gson) {
    return new RegistrationController(userService, gson);
  }

  @Provides
  @IntoSet
  @Singleton
  public static RestController providesUserListingController(UserService userService, Gson gson) {
    return new UserListingController(userService, gson);
  }

  @Provides
  @IntoSet
  @Singleton
  public static RestController providesAuthenticationController(
      AuthenticationService authenticationService, Gson gson) {
    return new AuthenticationController(authenticationService, gson);
  }

  @Provides
  @IntoSet
  @Singleton
  public static RestController providesInfoController(Gson gson) {
    return new InfoController(gson);
  }

  private static GsonBuilder setupTypeAdapters(GsonBuilder gsonBuilder) {
    gsonBuilder
        .registerTypeAdapter(LocalDateTime.class, new DateTimeJsonSerializer())
        .registerTypeAdapter(RegistrationDetails.class, new RegistrationDetails.Deserializer())
        .registerTypeAdapter(UserDTO.class, new UserDTOSerializer())
        .registerTypeAdapter(SocketMessage.class, new SocketMessage.Serializer());

    return gsonBuilder;
  }

}
