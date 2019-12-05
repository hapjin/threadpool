package com.textml.threadpool.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author psj
 * @date 2019/03/07
 */
public class ExponentiallyWeightedMovingAverage {

    private final double alpha;
    private final AtomicLong averageBits;

    public ExponentiallyWeightedMovingAverage(double alpha, double initialAvg) {
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("alpha must be greater or equal to 0 and less than or equal to 1");
        }
        this.alpha = alpha;
        this.averageBits = new AtomicLong(Double.doubleToLongBits(initialAvg));
    }

    public double getAverage() {
        return Double.longBitsToDouble(this.averageBits.get());
    }

    public void addValue(double newValue) {
        boolean successful = false;
        do {
            final long currentBits = this.averageBits.get();
            final double currentAvg = getAverage();
            final double newAvg = (alpha * newValue) + (1 - alpha) * currentAvg;
            final long newBits = Double.doubleToLongBits(newAvg);
            successful = averageBits.compareAndSet(currentBits, newBits);
        } while (successful == false);
    }
}
