package com.ubs.trading.core;

import com.lmax.disruptor.*;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.ubs.trading.metrics.MetricsRecorder;
import java.util.concurrent.Executors;

public final class DisruptorEngine {

  private final Disruptor<EventEnvelope> disruptor;
  private final RingBuffer<EventEnvelope> ring;
  private final MetricsRecorder metrics;

  /* ---------- translator eliminates the raw/unchecked call ---------- */
  private static final EventTranslatorTwoArg<EventEnvelope, String, Long> TRANSLATOR =
      (evt, seq, msg, ts) -> evt.set(msg, ts);

  public DisruptorEngine(
      int ringSize, EventHandler<EventEnvelope> handler, MetricsRecorder metrics) {

    this.metrics = metrics;

    disruptor =
        new Disruptor<>(
            EventEnvelope::new,
            ringSize,
            Executors.defaultThreadFactory(),
            ProducerType.SINGLE,
            new BlockingWaitStrategy());

    disruptor.handleEventsWith(handler);
    ring = disruptor.start();
  }

  public void publish(String message) {
    long t0 = metrics.markIngest();
    ring.publishEvent(TRANSLATOR, message, t0); // no unchecked conversion
  }

  public void shutdown() {
    disruptor.shutdown();
  }
}
