package io.infectnet.server.controller.rest.user;

import static io.infectnet.server.controller.utils.ResponseUtils.sendEmptyOk;
import static spark.Spark.post;

import com.google.gson.Gson;

import io.infectnet.server.controller.rest.RestController;
import io.infectnet.server.service.user.UserService;
import io.infectnet.server.service.user.exception.ValidationException;

import spark.Request;
import spark.Response;

/**
 * REST controller responsible for registration.
 */
public class RegistrationController implements RestController {

  private static final String URL_PATH = "/register";

  private final UserService userService;

  private final Gson gson;

  public RegistrationController(UserService userService, Gson gson) {
    this.userService = userService;

    this.gson = gson;
  }

  @Override
  public void configure() {
    post(URL_PATH, this::registrationEndpoint);
  }

  private Object registrationEndpoint(Request req, Response resp)
      throws RegistrationFailedException {
    RegistrationDetails details = gson.fromJson(req.body(), RegistrationDetails.class);

    try {
      userService.register(details.getToken(), details.getEmail(), details.getUsername(),
        details.getPassword());
    } catch (ValidationException e) {
      throw new RegistrationFailedException(e);
    }

    return sendEmptyOk(resp);
  }
}
