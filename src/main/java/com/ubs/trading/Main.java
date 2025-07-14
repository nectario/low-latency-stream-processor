package com.ubs.trading;

import com.ubs.trading.core.*;
import com.ubs.trading.metrics.MetricsRecorder;
import com.ubs.trading.pipeline.Pipeline;
import com.ubs.trading.statemachine.StateMachine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Demonstrates: • Single‑type pipelines • StateMachine‑driven pipeline selection • Disruptor event
 * loop with latency metrics • Synthetic load generator ≈25k msg/s
 */
public final class Main {

  /* ========================================================= *
   *  0.  Protocol enums & classifier
   * ========================================================= */

  /** Connection/session states for this toy protocol. */
  private enum ConnState {
    IDLE,
    ACTIVE
  }

  /** Message categories (events) derived from inbound text. */
  private enum MsgEvent {
    HELLO,
    DATA
  }

  /** Classifier: raw string → MsgEvent. */
  private static final Function<String, MsgEvent> CLASSIFIER =
      m -> m.toUpperCase(Locale.ROOT).startsWith("HELLO") ? MsgEvent.HELLO : MsgEvent.DATA;

  /* ========================================================= *
   *  1.  Pipelines
   * ========================================================= */

  /** Runs once during handshake. */
  private static final Pipeline<String> HELLO_PIPELINE =
      Pipeline.build("hello", true, String::trim, String::toUpperCase);

  /** Processes data messages. */
  private static final Pipeline<String> DATA_PIPELINE =
      Pipeline.build("data", true, String::trim, s -> s + " | processed");

  /* ========================================================= *
   *  2.  StateMachine builder
   * ========================================================= */

  private static StateMachine<ConnState, MsgEvent> buildStateMachine() {
    StateMachine<ConnState, MsgEvent> statemachine = new StateMachine<>(ConnState.IDLE);

    statemachine
        .add(ConnState.IDLE, MsgEvent.HELLO, ConnState.ACTIVE, HELLO_PIPELINE)
        .add(ConnState.ACTIVE, MsgEvent.DATA, ConnState.ACTIVE, DATA_PIPELINE);
    return statemachine;
  }

  /* --------------------------------------------------------- *
   * Helper: console‑friendly sender
   * --------------------------------------------------------- */
  private static Dispatcher.MessageSender verboseSender() {
    AtomicLong seq = new AtomicLong();
    return msg -> {
      long n = seq.incrementAndGet();
      if (n <= 10 || n % 5_000 == 0) { // 1‑10, 5000, …
        System.out.printf("> msg #%d : %s%n", n, msg);
      }
    };
  }

  /* ========================================================= *
   *  3.  main()
   * ========================================================= */

  public static void main(String[] args) throws InterruptedException {

    /* --------------------------------------------------------- *
     * 3.1  Metrics setup
     * --------------------------------------------------------- */
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MetricsRecorder metrics = new MetricsRecorder(meterRegistry);

    /* --------------------------------------------------------- *
     * 3.2  StateMachine‑driven Dispatcher
     * --------------------------------------------------------- */
    StateMachine<ConnState, MsgEvent> fsm = buildStateMachine();

    /* NEW: log every outbound message */
    Dispatcher.MessageSender sender = verboseSender();

    Dispatcher<ConnState, MsgEvent> dispatcher = new Dispatcher<>(fsm, CLASSIFIER, metrics, sender);

    /* --------------------------------------------------------- *
     * 3.3  Disruptor engine
     * --------------------------------------------------------- */
    DisruptorEngine engine =
        new DisruptorEngine(
            1_024, // ring buffer size
            dispatcher,
            metrics);

    /* --------------------------------------------------------- *
     * 3.4  Synthetic load generator (≈25kmsg/s)
     * --------------------------------------------------------- */
    engine.publish("HELLO handshake"); // push FSM to ACTIVE

    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    Runnable publisher = () -> engine.publish("data-msg");

    long periodNanos = 40_000; // 40µs → 25k msg/s

    exec.scheduleAtFixedRate(publisher, 0, periodNanos, TimeUnit.NANOSECONDS);

    Thread.sleep(2_000); // run for 2 seconds
    exec.shutdownNow();
    engine.shutdown();

    /* --------------------------------------------------------- *
     * 3.5  Print latency histogram
     * --------------------------------------------------------- */

    System.out.println(
        "End-to-end latency snapshot:\n"
            + meterRegistry.get("msg.e2e.latency").timer().takeSnapshot());
  }
}
