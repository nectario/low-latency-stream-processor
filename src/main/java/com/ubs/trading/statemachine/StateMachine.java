package com.ubs.trading.statemachine;

import com.ubs.trading.pipeline.Pipeline;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic, thread‑safe state machine whose <em>action</em> is a {@link Pipeline}.
 *
 * <p>Each transition is defined by: <code>(currentState, event) -> nextState, pipeline</code>
 *
 * @param <S> the State type (enum or String recommended)
 * @param <E> the Event type (enum or String recommended)
 */
public final class StateMachine<S, E> {

  /** Immutable transition record. */
  public static record Transition<S>(S nextState, Pipeline<String> pipeline) {}

  private final Map<S, Map<E, Transition<S>>> table = new ConcurrentHashMap<>();
  private volatile S current;

  public StateMachine(S initialState) {
    this.current = initialState;
  }

  /* ------------------------------------------------------------------ *
   *  Builder API
   * ------------------------------------------------------------------ */

  public StateMachine<S, E> add(S from, E event, S to, Pipeline<String> pipeline) {
    table
        .computeIfAbsent(from, k -> new ConcurrentHashMap<>())
        .put(event, new Transition<>(to, pipeline));
    return this;
  }

  /* ------------------------------------------------------------------ *
   *  Runtime API
   * ------------------------------------------------------------------ */

  /**
   * Fires an event, returns the pipeline associated with the transition, and mutates the current
   * state.
   *
   * @throws IllegalStateException if no transition is defined.
   */
  public Pipeline<String> onEvent(E event) {
    Map<E, Transition<S>> row = table.get(current);
    if (row == null || !row.containsKey(event))
      throw new IllegalStateException("No transition for %s / %s".formatted(current, event));

    Transition<S> t = row.get(event);
    current = t.nextState();
    return t.pipeline();
  }

  public S state() {
    return current;
  }
}
