package com.textml.threadpool.queue;

import java.util.concurrent.BlockingQueue;

/**
 * @author psj
 * @date 2019/03/06
 * <p>
 * 动态调整任务队列的长度
 */
public class TextResizableBlockingQueue<E> extends TextSizeBlockingQueue<E> {

    private volatile int capacity;

    public TextResizableBlockingQueue(BlockingQueue<E> queue, int initalCapacity) {
        super(queue, initalCapacity);
        this.capacity = initalCapacity;
    }

    @Override
    public int capacity() {
        return this.capacity;
    }

    @Override
    public int remainingCapacity() {
        return Math.max(0, this.capacity());
    }

    /**
     * 调整队列的长度(这里的长度是指capacity, 即: limit of this queue)
     * @param optimalCapacity 根据little's law 等一些法则计算得到的最优队列容量
     * @param adjustmentAmount 调整量 默认 50
     * @param minCapacity 本次调整的下限,调整后队列容量最小也不能小于maxCapacity
     * @param maxCapacity 本次调整的上限,调整后队列容量最大也不能大于maxCapacity
     * @return
     */
    public synchronized int adjustCapacity(int optimalCapacity, int adjustmentAmount, int minCapacity, int maxCapacity) {
        assert adjustmentAmount > 0 : "adjust amount should be a positive value";
        assert optimalCapacity >= 0 : "desired capacity can not be negative";
        assert minCapacity >= 0 : "can not have min capacity smaller than 0";
        assert maxCapacity >= minCapacity : "can not have max capacity smaller than min capacity";

        if (optimalCapacity == capacity) {
            return this.capacity;
        }

        if (optimalCapacity > capacity + adjustmentAmount) {
            //adjust up
            final int newCapacity = Math.min(maxCapacity, capacity + adjustmentAmount);
            this.capacity = newCapacity;
            return newCapacity;
        } else if (optimalCapacity < capacity - adjustmentAmount) {
            //adjust down
            final int newCapacity = Math.max(minCapacity, capacity - adjustmentAmount);
            this.capacity = newCapacity;
            return newCapacity;
        }else {
            return this.capacity;
        }
    }
}
