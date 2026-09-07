/**
 * Matching core and ingress. A single dedicated thread owns the book and balances; producers offer to a bounded MPSC ring buffer.
 */
package dev.kaloyanyordanov.exchange.engine;
