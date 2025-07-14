package com.ubs.trading.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/** Records ingest, processing and end‑to‑end latencies. */
public final class MetricsRecorder {

  private final Timer ingest;
  private final Timer processing;
  private final Timer e2e;

  public MetricsRecorder(MeterRegistry reg) {
    ingest = buildTimer("msg.ingest.latency", reg);
    processing = buildTimer("msg.processing.latency", reg);
    e2e = buildTimer("msg.e2e.latency", reg);
  }

  private static Timer buildTimer(String name, MeterRegistry reg) {
    return Timer.builder(name)
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()
        .register(reg);
  }

  /* --- recording helpers ------------------------------------------------ */

  public long markIngest() { // call on arrival, returns t0
    long now = System.nanoTime();
    ingest.record(0, TimeUnit.NANOSECONDS); // count message
    return now;
  }

  public long markProcessingStart() {
    return System.nanoTime();
  }

  public void recordProcessing(long tStart) {
    processing.record(System.nanoTime() - tStart, TimeUnit.NANOSECONDS);
  }

  public void recordEndToEnd(long tIngest) {
    e2e.record(System.nanoTime() - tIngest, TimeUnit.NANOSECONDS);
  }
}
