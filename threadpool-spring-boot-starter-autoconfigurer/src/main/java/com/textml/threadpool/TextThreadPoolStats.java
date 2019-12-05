package com.textml.threadpool;

import java.util.Collections;
import java.util.List;

/**
 * @author psj
 * @date 2019/10/28
 */
public class TextThreadPoolStats {

    private List<Stats> stats;

    public TextThreadPoolStats(List<Stats> stats) {
        Collections.sort(stats);
        this.stats = stats;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Stats stat : stats) {
            sb.append(stat);
            sb.append(System.getProperty("line.separator"));
        }
        return sb.toString();
    }

    public static class Stats implements Comparable<Stats>{
        private final String name;
        private final int threads;
        private final int queue;
        private final int active;
        private final long rejected;
        private final int largest;
        private final long completed;

        public Stats(String name, int threads, int queue, int active, long rejected, int largest, long completed) {
            this.name = name;
            this.threads = threads;
            this.queue = queue;
            this.active = active;
            this.rejected = rejected;
            this.largest = largest;
            this.completed = completed;
        }

        public String getName() {
            return name;
        }

        public int getThreads() {
            return threads;
        }

        public int getActive() {
            return active;
        }

        public long getRejected() {
            return rejected;
        }

        public int getLargest() {
            return largest;
        }

        public long getCompleted() {
            return completed;
        }

        public int getQueue() {
            return queue;
        }

        @Override
        public int compareTo(Stats o) {
            if (getName() == null && o.getName() == null) {
                return 0;
            } else if (getName() != null && o.getName() == null) {
                return 1;
            } else if (getName() == null) {
                return -1;
            }else {
                int compare = getName().compareTo(o.getName());
                if (compare == 0) {
                    compare = Integer.compare(getThreads(), o.getThreads());
                }
                return compare;
            }
        }

        /**
         * 打印线程池的当前状态
         * @return
         */
        @Override
        public String toString() {
            return String.format("name:%s, current threads:%d, current queue size:%d, active threads:%d, rejected tasks %d, max pool size:%d, completed tasks:%d",
                    name, threads, queue, active, rejected, largest, completed);
        }
    }
}
