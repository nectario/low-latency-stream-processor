package com.ubs.trading.pipeline;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread‑safe registry that maps a symbolic name to a {@link Pipeline}.
 *
 * <p>Intended usage:
 *
 * <pre>{@code
 * PipelineRegistry registry = new PipelineRegistry();
 * registry.register("hello", helloPipeline);
 *
 * Pipeline<String> p = registry.lookup("hello")
 *                              .orElseThrow(() -> new IllegalArgumentException("Unknown key"));
 * }</pre>
 *
 * <p>The registry never exposes its internal map; callers receive either
 *
 * <ul>
 *   <li>an {@code Optional<Pipeline<String>>} via {@link #lookup}, or
 *   <li>an <i>immutable snapshot</i> via {@link #asMap} (useful for debugging).
 * </ul>
 */
public final class PipelineRegistry {

  /** Underlying storage – high‑concurrency friendly. */
  private final Map<String, Pipeline<String>> map = new ConcurrentHashMap<>();

  /* ------------------------------------------------------------------ *
   *  Registration API
   * ------------------------------------------------------------------ */

  /**
   * Register or replace a pipeline.
   *
   * @param key unique human‑readable identifier (case sensitive)
   * @param pipeline non‑null pipeline instance
   * @return {@code true} if a new key was inserted, {@code false} if an existing key was replaced
   */
  public boolean register(String key, Pipeline<String> pipeline) {
    return map.put(key, pipeline) == null;
  }

  /** Remove a pipeline; harmless if the key does not exist. */
  public void remove(String key) {
    map.remove(key);
  }

  /* ------------------------------------------------------------------ *
   *  Lookup API
   * ------------------------------------------------------------------ */

  /**
   * @return an {@code Optional} containing the pipeline, or empty if not registered.
   */
  public Optional<Pipeline<String>> lookup(String key) {
    return Optional.ofNullable(map.get(key));
  }

  /** Shorthand that throws if the key is missing. */
  public Pipeline<String> require(String key) {
    return lookup(key)
        .orElseThrow(() -> new IllegalArgumentException("No pipeline for key: " + key));
  }

  /* ------------------------------------------------------------------ *
   *  Introspection / debug
   * ------------------------------------------------------------------ */

  /** Immutable snapshot of current registry contents. */
  public Map<String, Pipeline<String>> asMap() {
    return Collections.unmodifiableMap(map);
  }

  /** Number of registered pipelines. */
  public int size() {
    return map.size();
  }
}
