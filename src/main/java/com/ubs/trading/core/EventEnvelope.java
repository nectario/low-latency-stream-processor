package com.ubs.trading.core;

/** Pre‑allocated Disruptor event. */
public final class EventEnvelope {

  private String message; // ← renamed
  private long ingestNanos;

  /* package‑private mutator */
  void set(String message, long ingestNanos) {
    this.message = message;
    this.ingestNanos = ingestNanos;
  }

  /* getters */
  public String getMessage() {
    return message;
  }

  public long getIngestNanos() {
    return ingestNanos;
  }

  void clear() {
    message = null;
  }
}
