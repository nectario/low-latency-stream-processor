package com.ubs.trading;

import com.ubs.trading.core.*;
import com.ubs.trading.metrics.MetricsRecorder;
import com.ubs.trading.pipeline.Pipeline;
import com.ubs.trading.statemachine.StateMachine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo:
 *   • single‑type pipelines
 *   • table‑driven state machine
 *   • generic Disruptor engine with latency metrics
 *   • synthetic load ≈25kmsg/s
 */
public final class Main {

    /* ========================================================= *
     *  0.  Protocol state
     * ========================================================= */

    private enum ConnState { IDLE, ACTIVE }

    /* ========================================================= *
     *  1.  Pipelines
     * ========================================================= */

    private static final Pipeline<String> HELLO_PIPELINE = Pipeline.build(
            "hello", true,
            String::trim,
            String::toUpperCase);

    private static final Pipeline<String> DATA_PIPELINE = Pipeline.build(
            "data", true,
            String::trim,
            s -> s + " | processed");

    /* ========================================================= *
     *  2.  StateMachine builder
     * ========================================================= */

    private static StateMachine<ConnState, String> buildStateMachine() {
        return new StateMachine<ConnState, String>(ConnState.IDLE)   // <‑‑ add <ConnState,String>
                .add(ConnState.IDLE,   "HELLO", ConnState.ACTIVE, HELLO_PIPELINE)
                .add(ConnState.ACTIVE, "DATA",  ConnState.ACTIVE, DATA_PIPELINE);
    }

    /* --------------------------------------------------------- *
     * Helper: console‑friendly sender
     * --------------------------------------------------------- */
    private static Dispatcher.MessageSender<String> verboseSender() {
        AtomicLong seq = new AtomicLong();
        return msg -> {
            long n = seq.incrementAndGet();
            if (n <= 10 || n % 5_000 == 0) {
                System.out.printf("> msg #%d : %s%n", n, msg);
            }
        };
    }

    /* ========================================================= *
     *  3.  main()
     * ========================================================= */
    public static void main(String[] args) throws InterruptedException {

        /* 3.1  Metrics */
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsRecorder      m  = new MetricsRecorder(reg);

        /* 3.2  Dispatcher driven by the FSM */
        Dispatcher<ConnState,String> dispatcher =
                new Dispatcher<>(buildStateMachine(), m, verboseSender());

        /* 3.3  Disruptor engine */
        DisruptorEngine<String> engine =
                new DisruptorEngine<>(1_024, dispatcher, m);

        /* 3.4  Synthetic load (≈25kmsg/s) */
        engine.publish("HELLO");                    // handshake → ACTIVE

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        Runnable pub = () -> engine.publish("DATA");

        exec.scheduleAtFixedRate(pub, 0, 40_000, TimeUnit.NANOSECONDS); // 40µs

        Thread.sleep(2_000);                       // run 2seconds
        exec.shutdownNow();
        engine.shutdown();

        /* 3.5  Latency snapshot */
        System.out.println(
                "End‑to‑end latency snapshot:\n" +
                        reg.get("msg.e2e.latency").timer().takeSnapshot());
    }
}
