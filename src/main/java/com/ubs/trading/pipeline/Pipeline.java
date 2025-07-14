package com.ubs.trading.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable, thread‑safe pipeline whose input and output are the same type.
 *
 * <p>Construction patterns:
 *
 * <pre>{@code
 * // 1) Var‑args factory – build the entire chain in one line
 * Pipeline<String> p = Pipeline.build(
 *         "demo",          // pipeline name
 *         true,            // short‑circuit on error
 *         String::trim,
 *         s -> s.toUpperCase(),
 *         s -> s + "!");
 *
 * // 2) Step‑by‑step builder – add actions fluently
 * Pipeline<String> q = Pipeline.named("builder")
 *         .addAction(String::strip)
 *         .addAction(s -> s.repeat(2));
 * }</pre>
 *
 * @param <T> the homogeneous type that flows through every pipeline stage
 */
public final class Pipeline<T> {

  /* ------------------------------------------------------------------ */
  /*  instance state                                                    */
  /* ------------------------------------------------------------------ */

  private final String name;
  private final boolean shortCircuit;
  private final List<Function<T, T>> actions;

  /* ------------------------------------------------------------------ */
  /*  public factories                                                  */
  /* ------------------------------------------------------------------ */

  /** Build a full pipeline in one call. */
  @SafeVarargs
  public static <T> Pipeline<T> build(String name, boolean shortCircuit, Function<T, T>... steps) {
    Pipeline<T> p = new Pipeline<>(name, shortCircuit, List.of());
    for (Function<T, T> s : steps) {
      p = p.addAction(s);
    }
    return p;
  }

  /** Named pipeline, <i>shortCircuit = true</i> by default. */
  public static <T> Pipeline<T> named(String name) {
    return new Pipeline<>(name, true, List.of());
  }

  public static <T> Pipeline<T> named(String name, boolean shortCircuit) {
    return new Pipeline<>(name, shortCircuit, List.of());
  }

  /* ------------------------------------------------------------------ */
  /*  builder                                                           */
  /* ------------------------------------------------------------------ */

  /** Adds a transformation step and returns a <em>new</em> pipeline. */
  public Pipeline<T> addAction(Function<T, T> step) {
    Objects.requireNonNull(step, "step");
    List<Function<T, T>> next = new ArrayList<>(actions);
    next.add(step);
    return new Pipeline<>(name, shortCircuit, List.copyOf(next));
  }

  /* ------------------------------------------------------------------ */
  /*  execution                                                         */
  /* ------------------------------------------------------------------ */

  /**
   * Execute the pipeline.
   *
   * @throws RuntimeException if a step throws and {@code shortCircuit} is true
   */
  public T execute(T input) {
    T value = input;
    for (Function<T, T> step : actions) {
      try {
        value = step.apply(value);
      } catch (Exception ex) {
        if (shortCircuit) throw wrap(ex); // halt & propagate
        // else: swallow and keep last good value
      }
    }
    return value;
  }

  /* ------------------------------------------------------------------ */
  /*  metadata                                                          */
  /* ------------------------------------------------------------------ */

  public String name() {
    return name;
  }

  public int size() {
    return actions.size();
  }

  /* ------------------------------------------------------------------ */
  /*  internal                                                         */
  /* ------------------------------------------------------------------ */

  private Pipeline(String name, boolean shortCircuit, List<Function<T, T>> actions) {
    this.name = Objects.requireNonNull(name, "name");
    this.shortCircuit = shortCircuit;
    this.actions = actions; // already immutable
  }

  @Override
  public String toString() {
    return "Pipeline["
        + name
        + ", steps="
        + actions.size()
        + ", shortCircuit="
        + shortCircuit
        + ']';
  }

  // Modern pattern‑matching syntax requires JDK16+
  private static RuntimeException wrap(Exception ex) {
    return (ex instanceof RuntimeException rte) ? rte : new RuntimeException(ex);
  }
}
