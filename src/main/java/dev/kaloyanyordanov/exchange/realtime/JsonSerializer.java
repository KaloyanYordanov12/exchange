package dev.kaloyanyordanov.exchange.realtime;

/**
 * Serializes a message to a JSON string. A thin seam over the JSON library so the
 * broadcaster holds an immutable function rather than a configurable mapper.
 */
@FunctionalInterface
public interface JsonSerializer {

  /**
   * Serializes a value to JSON.
   *
   * @param value the value to serialize
   * @return the JSON string
   */
  String write(Object value);
}
