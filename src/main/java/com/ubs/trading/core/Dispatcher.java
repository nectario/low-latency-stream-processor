package com.ubs.trading.core;

import com.lmax.disruptor.EventHandler;
import com.ubs.trading.metrics.MetricsRecorder;
import com.ubs.trading.pipeline.Pipeline;
import com.ubs.trading.statemachine.StateMachine;

import java.util.Objects;
import java.util.function.Function;

/**
 * Consumes {@link EventEnvelope}s, chooses the appropriate {@link Pipeline},
 * records latency metrics, and forwards the transformed payload to a downstream
 * channel via a {@link MessageSender}.
 *
 * Two operating modes:
 *  1. fixed pipeline               – ctor with a single Pipeline<T>
 *  2. state‑machine‑driven pipeline – ctor with StateMachine<S,E>
 *
 * @param <S> state-machine state type
 * @param <T> payload type flowing through the pipeline
 */
public final class Dispatcher<S, T> implements EventHandler<EventEnvelope<T>> {

    @FunctionalInterface
    public interface MessageSender<T> { void send(T msg); }

    private final MetricsRecorder metrics;
    private final MessageSender<T> sender;

    private final Pipeline<T>          fixedPipeline;
    private final StateMachine<S, T>   stateMachine;

    /* fixed‑pipeline ctor */
    public Dispatcher(Pipeline<T> pipeline,
                      MetricsRecorder metrics,
                      MessageSender<T> sender) {
        this.fixedPipeline = Objects.requireNonNull(pipeline);
        this.metrics = Objects.requireNonNull(metrics);
        this.sender  = Objects.requireNonNull(sender);
        this.stateMachine = null;
    }

    /* state‑machine ctor */
    public Dispatcher(StateMachine<S,T> fsm,
                      MetricsRecorder metrics,
                      MessageSender<T> sender) {
        this.stateMachine = Objects.requireNonNull(fsm);
        this.metrics = Objects.requireNonNull(metrics);
        this.sender  = Objects.requireNonNull(sender);
        this.fixedPipeline = null;
    }

    @Override
    public void onEvent(EventEnvelope<T> env, long seq, boolean endOfBatch) {
        long t0 = env.getIngestNanos();
        long tStart = metrics.markProcessingStart();

        Pipeline<T> pipeline =
                stateMachine != null ? stateMachine.onEvent(env.getPayload())
                        : fixedPipeline;

        T out = pipeline.execute(env.getPayload());

        metrics.recordProcessing(tStart);
        metrics.recordEndToEnd(t0);

        sender.send(out);
        env.clear();
    }
}
