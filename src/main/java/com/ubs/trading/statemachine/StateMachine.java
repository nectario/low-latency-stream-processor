package com.ubs.trading.statemachine;

import com.ubs.trading.pipeline.Pipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Table‑driven state machine whose transitions return a Pipeline&lt;T&gt;.
 *
 * @param <S> state   type (enum or String recommended)
 * @param <E> event type *and* payload type flowing through the pipeline
 */
public final class StateMachine<S, E> {

    public static record Transition<S, T>(S nextState, Pipeline<T> pipeline) {}

    private final Map<S, Map<E, Transition<S, E>>> table = new ConcurrentHashMap<>();
    private volatile S current;

    public StateMachine(S initialState) { this.current = initialState; }

    /* builder --------------------------------------------------------- */

    public StateMachine<S, E> add(S from, E event, S to, Pipeline<E> pipeline) {
        table.computeIfAbsent(from, k -> new ConcurrentHashMap<>())
                .put(event, new Transition<>(to, pipeline));
        return this;
    }

    /* runtime --------------------------------------------------------- */

    public Pipeline<E> onEvent(E event) {
        Map<E, Transition<S, E>> row = table.get(current);
        if (row == null || !row.containsKey(event))
            throw new IllegalStateException("No transition for %s / %s".formatted(current, event));

        Transition<S, E> t = row.get(event);
        current = t.nextState();
        return t.pipeline();
    }

    public S state() { return current; }
}
