package dev.kaloyanyordanov.exchange.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page app for its client-side routes. The SPA uses History-API
 * routing, so a direct load or refresh of {@code /markets} or {@code /trade/BTC-USD}
 * must return {@code index.html} (the bundle then renders the right view). The API
 * routes ({@code /pairs}, {@code /orders}, {@code /candles}, ...) are not matched here
 * and are served by their controllers; {@code /} is served as the static index.
 */
@Controller
public class SpaForwardController {

  /**
   * Forwards the SPA's client-side routes to the app shell.
   *
   * @return a forward to the static {@code index.html}
   */
  @GetMapping({"/markets", "/trade/**"})
  public String forwardSpaRoutes() {
    return "forward:/index.html";
  }
}
