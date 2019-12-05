package com.textml.threadpool.util;

import java.util.concurrent.TimeUnit;

/**
 * @author psj
 * @date 2019/03/07
 */
public class TextTimeValue {
    public static final long NSEC_PER_MSEC = TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS);

    private final long duration;
    private final TimeUnit timeUnit;


    public TextTimeValue(long duration, TimeUnit timeUnit) {
        this.duration = duration;
        this.timeUnit = timeUnit;
    }

    public TextTimeValue(long millis) {
        this(millis, TimeUnit.MILLISECONDS);
    }
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public long getDuration() {
        return duration;
    }

    public static TextTimeValue timeValueNanos(long nanos) {
        return new TextTimeValue(nanos, TimeUnit.NANOSECONDS);
    }

    public static TextTimeValue timeValueMillis(long millis) {
        return new TextTimeValue(millis, TimeUnit.MILLISECONDS);
    }

    public static TextTimeValue timeValueSeconds(long seconds) {
        return new TextTimeValue(seconds, TimeUnit.SECONDS);
    }


    public long getSeconds() {
        return seconds();
    }

    private long seconds() {
        return timeUnit.toSeconds(duration);
    }
    public long getMillis() {
        return millis();
    }
    private long millis() {
        return timeUnit.toMillis(duration);
    }

    public long getNanos() {
        return nanos();
    }

    public long nanos() {
        return timeUnit.toNanos(duration);
    }

    @Override
    public String toString() {
        return "duration: " + timeUnit.toMillis(duration) + "ms";
    }
}
