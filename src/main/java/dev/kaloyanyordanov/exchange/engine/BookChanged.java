package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;

/**
 * Emitted after a command changes the book, carrying an immutable snapshot for
 * downstream broadcast.
 *
 * @param snapshot the post-command book snapshot
 */
public record BookChanged(BookSnapshot snapshot) implements EngineEvent {

  /** Validates the event. */
  public BookChanged {
    if (snapshot == null) {
      throw new IllegalArgumentException("snapshot must be provided");
    }
  }
}
