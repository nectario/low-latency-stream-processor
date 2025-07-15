package com.ubs.trading.core;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.ubs.trading.metrics.MetricsRecorder;

import java.util.concurrent.Executors;

/**
 * Generic Disruptor wrapper:
 *
 *   • {@code T} is the payload type carried by {@code EventEnvelope<T>}.
 *   • Publishes events in a type-safe way, no raw casts.
 */
public final class DisruptorEngine<T> {

    private final Disruptor<EventEnvelope<T>> disruptor;
    private final RingBuffer<EventEnvelope<T>> ring;
    private final MetricsRecorder metrics;

    /** Re-usable translator eliminates unchecked calls. */

    private final class Translator implements EventTranslatorTwoArg<EventEnvelope<T>, T, Long> {

        @Override
        public void translateTo(EventEnvelope<T> evt, long seq, T msg, Long ts) {
            evt.set(msg, ts);
        }
    }

    private final EventTranslatorTwoArg<EventEnvelope<T>, T, Long> translator = new Translator();


    /* ------------------------------------------------------------- */

    public DisruptorEngine(int ringSize,
                           EventHandler<EventEnvelope<T>> handler,
                           MetricsRecorder metrics) {

        this.metrics = metrics;

        disruptor = new Disruptor<>(
                EventEnvelope::new,           // factory supplies generic envelope
                ringSize,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new BlockingWaitStrategy());

        disruptor.handleEventsWith(handler);
        ring = disruptor.start();
    }

    /** Publish a payload of type {@code T}. */
    public void publish(T message) {
        long t0 = metrics.markIngest();
        ring.publishEvent(translator, message, t0);
    }

    public void shutdown() {
        disruptor.shutdown();
    }
}
