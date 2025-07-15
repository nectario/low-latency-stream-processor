package com.ubs.trading.core;

/**
 * Pre‑allocated event wrapper used by the Disruptor ring buffer.
 *
 * @param <T> the payload type carried through the pipeline
 */
public final class EventEnvelope<T> {

    private T    payload;
    private long ingestNanos;

    /* package‑private mutator: set both fields in one go */
    void set(T payload, long ingestNanos) {
        this.payload     = payload;
        this.ingestNanos = ingestNanos;
    }

    /* getters */

    public T getPayload() {
        return payload;
    }

    public long getIngestNanos() {
        return ingestNanos;
    }

    /* Clear references so the object can be safely reused by the ring buffer */
    void clear() {
        payload = null;
    }
}
