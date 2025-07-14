package com.ubs.trading.core;

import com.lmax.disruptor.EventHandler;
import com.ubs.trading.metrics.MetricsRecorder;
import com.ubs.trading.pipeline.Pipeline;
import com.ubs.trading.statemachine.StateMachine;
import java.util.Objects;
import java.util.function.Function;

/**
 * Consumes {@link EventEnvelope}s, chooses the appropriate {@link Pipeline}, records latency
 * metrics, and forwards the transformed message to a downstream channel via a {@link
 * MessageSender}.
 *
 * <p>Two operating modes:
 *
 * <ol>
 *   <li><b>Fixed pipeline</b> – traditional constructor taking a single pipeline.
 *   <li><b>State‑machine driven</b> – constructor that injects a {@link StateMachine} and a
 *       message‑to‑event classifier; the pipeline comes from the FSM.
 * </ol>
 */
public final class Dispatcher<S, E> implements EventHandler<EventEnvelope> {

  /* Downstream abstraction */
  @FunctionalInterface
  public interface MessageSender {
    void send(String message);
  }

  /* --- invariant components --- */
  private final MetricsRecorder metrics;
  private final MessageSender sender;

  /* --- mode‑specific components (exactly one will be non‑null) --- */
  private final Pipeline<String> fixedPipeline;
  private final StateMachine<S, E> stateMachine;
  private final Function<? super String, E> classifier;

  /* ------------------------------------------------------------------ *
   * 1. Fixed‑pipeline constructor (keeps original functionality)
   * ------------------------------------------------------------------ */
  public Dispatcher(Pipeline<String> pipeline, MetricsRecorder metrics, MessageSender sender) {
    this.fixedPipeline = Objects.requireNonNull(pipeline);
    this.metrics = Objects.requireNonNull(metrics);
    this.sender = Objects.requireNonNull(sender);
    this.stateMachine = null;
    this.classifier = null;
  }

  /* ------------------------------------------------------------------ *
   * 2. State‑machine constructor
   * ------------------------------------------------------------------ */
  public Dispatcher(
      StateMachine<S, E> stateMachine,
      Function<? super String, E> classifier,
      MetricsRecorder metrics,
      MessageSender sender) {
    this.stateMachine = Objects.requireNonNull(stateMachine);
    this.classifier = Objects.requireNonNull(classifier);
    this.metrics = Objects.requireNonNull(metrics);
    this.sender = Objects.requireNonNull(sender);
    this.fixedPipeline = null;
  }

  /* ------------------------------------------------------------------ *
   * Event handling
   * ------------------------------------------------------------------ */
  @Override
  public void onEvent(EventEnvelope env, long seq, boolean endOfBatch) {
    long t0 = env.getIngestNanos();
    long tStart = metrics.markProcessingStart();

    /* ----- choose the pipeline ----- */
    Pipeline<String> pipeline;
    if (stateMachine != null) {
      E event = classifier.apply(env.getMessage());
      pipeline = stateMachine.onEvent(event);
    } else {
      pipeline = fixedPipeline;
    }

    /* ----- transform & record metrics ----- */
    String transformed = pipeline.execute(env.getMessage());

    metrics.recordProcessing(tStart);
    metrics.recordEndToEnd(t0);

    /* ----- forward downstream ----- */
    sender.send(transformed);
    env.clear();
  }
}
