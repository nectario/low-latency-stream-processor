package com.ubs.trading.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ubs.trading.metrics.MetricsRecorder;
import com.ubs.trading.pipeline.Pipeline;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DispatcherTest {

  @Test
  @DisplayName("Dispatcher executes pipeline and forwards transformed message")
  void dispatcherTransformsAndSends() throws Exception {
    // 1. Pipeline that appends "-X"
    Pipeline<String> p = Pipeline.build("append", true, s -> s + "-X");

    // 2. Metrics stub
    MetricsRecorder metrics = new MetricsRecorder(new SimpleMeterRegistry());

    // 3. Capturing sender (no mocking framework)
    AtomicReference<String> captured = new AtomicReference<>();
    Dispatcher.MessageSender sender = captured::set;

    // 4. Dispatcher under test (fixed‑pipeline mode)
    Dispatcher<?, ?> dispatcher = new Dispatcher<>(p, metrics, sender);

    // 5. Fire a fake Disruptor event
    EventEnvelope env = new EventEnvelope();
    env.set("test", System.nanoTime());
    dispatcher.onEvent(env, 0, false);

    // 6. Verify
    assertThat(captured.get()).isEqualTo("test-X");
  }
}
