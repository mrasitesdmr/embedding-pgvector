package com.embedding.pgvector.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Testlerde her okunuşta sabit bir adım kadar ilerleyen saattir; süre ölçümlerini önceden hesaplanabilir
 * yapar.
 */
public final class SteppingClock extends Clock {

    private final AtomicLong millis = new AtomicLong(1_000_000L);
    private final long stepMillis;

    public SteppingClock(long stepMillis) {
        this.stepMillis = stepMillis;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public long millis() {
        return millis.getAndAdd(stepMillis);
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(1_760_000_000_000L);
    }
}
