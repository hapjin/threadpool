package com.textml.threadpool.util;

/**
 * @author psj
 * @date 2019/08/28
 */
public enum TaskPriority {
    //最高优先级
    IMMEDIATE((byte) 0),
    URGENT((byte) 1),
    HIGH((byte) 2),
    NORMAL((byte) 3),
    LOW((byte) 4),
    LANGUID((byte)5);

    private final byte value;

    TaskPriority(byte value) {
        this.value = value;
    }

    public static TaskPriority fromByte(byte b) {
        switch (b) {
            case 0:
                return IMMEDIATE;
            case 1:
                return URGENT;
            case 2:
                return HIGH;
            case 3:
                return NORMAL;
            case 4:
                return LOW;
            case 5:
                return LANGUID;
            default:
                throw new IllegalArgumentException("can't find priority for [" + b + "]");
        }
    }

    public boolean after(TaskPriority taskPriority) {
        return this.compareTo(taskPriority) > 0;
    }

    public boolean sameOrAfter(TaskPriority taskPriority) {
        return this.compareTo(taskPriority) >= 0;
    }

}
