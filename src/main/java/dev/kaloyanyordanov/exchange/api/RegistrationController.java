package dev.kaloyanyordanov.exchange.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public account registration. Anyone can register a name and receive a trader
 * API key <em>once</em>; new accounts start with zero balances and must fund via
 * the deposit path. Registration is the whole identity story (no passwords,
 * profiles, or email).
 */
@RestController
public class RegistrationController {

  private final TraderRegistry registry;

  /**
   * Creates the controller.
   *
   * @param registry the trader registry
   */
  public RegistrationController(TraderRegistry registry) {
    this.registry = registry;
  }

  /**
   * Registers an account.
   *
   * @param request the registration request
   * @return 201 with the account id and one-time API key, or 400 on bad input
   */
  @PostMapping("/register")
  public ResponseEntity<Object> register(@RequestBody RegistrationRequest request) {
    try {
      RegistrationResult result = registry.register(request == null ? null : request.name());
      return ResponseEntity.status(HttpStatus.CREATED).body(result);
    } catch (IllegalArgumentException invalid) {
      return ResponseEntity.badRequest().body(Map.of("error", invalid.getMessage()));
    }
  }
}
