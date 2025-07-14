package com.ubs.trading.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ubs.trading.core.*;
import com.ubs.trading.metrics.MetricsRecorder;
import com.ubs.trading.pipeline.Pipeline;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThroughputTest {

  @Test
  @DisplayName("System processes >= 20k msgs/s (50k in <= 3s)")
  void handlesTwentyKMessagesPerSecond() throws Exception {

    /* 1. Pass‑through pipeline */
    Pipeline<String> pipe = Pipeline.build("passthrough", true, String::trim);

    /* 2. Metrics */
    SimpleMeterRegistry reg = new SimpleMeterRegistry();
    MetricsRecorder metrics = new MetricsRecorder(reg);

    /* 3. Counting sender */
    AtomicLong counter = new AtomicLong();
    Dispatcher.MessageSender sender = m -> counter.incrementAndGet();

    Dispatcher<?, ?> dispatcher = new Dispatcher<>(pipe, metrics, sender);
    DisruptorEngine engine =
        new DisruptorEngine(
            1_048_576, // 1‑M slot ring buffer
            dispatcher,
            metrics);

    /* 4. Publish */
    final int TARGET = 50_000;
    long t0 = System.nanoTime(); // start stopwatch

    for (int i = 0; i < TARGET; i++) {
      engine.publish("msg");
    }

    /* 5. Await until every message has reached the sender */
    Awaitility.await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(counter.get()).isEqualTo(TARGET));

    long elapsedNs = System.nanoTime() - t0; // stop stopwatch

    /* 6. Compute throughput */
    double throughput = TARGET * 1_000_000_000.0 / elapsedNs;
    System.out.printf("Throughput = %.1f msg/s%n", throughput);

    assertThat(throughput).isGreaterThanOrEqualTo(20_000);

    engine.shutdown();
  }
}
