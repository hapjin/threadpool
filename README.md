# threadpool

## 功能介绍

1. 线程池在执行任务过程中，如果抛出了运行时异常，能不能再次执行该任务？
任务失败可重试
2. 如何统计任务的排队时间、执行时间？如何记录一个任务的处理时间。任务的处理时间等于排队时间加上处理时间
3. 线程池参数如何配置才合理？
    - core pool size 小于 max pool size时，需要等到任务队列满时，才会创建工作线程数到 max pool size。能不能优先创建工作线程数到max pool size后，再有任务提交过来排队入队列？
    - 线程池任务队列的长度设置成多大合适？统计任务的执行时间、设置任务响应时间，应用利特尔法则计算出合理的任务队列长度，然后动态调整任务的队列长度。
    - 
4. 支持优先级任务队列。
5. 更灵活的任务拒绝策略。如果采用有界的LinkedBlockingQueue，任务队列满时会导致任务拒绝；若采用无界队列LinkedTransferQueue，任务一直堆积又导致内存OOM或者不能及时处理任务。那么，能不能为任务定义一个属性(force execution)，强制执行的任务一定能入队列成功从而不会被拒绝？


## How To
mvn install 到本地仓库,然后项目中导入依赖即可
```html
    <dependency>
      <groupId>com.yy.textml.threadpool</groupId>
      <artifactId>threadpool-spring-boot-starter</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
```

### examples
1. 记录任务的执行时间，参考：com.textml.threadpool.runnable.TextTimedRunnable
```java
/**
 * @author psj
 * @date 2019/11/26
 */
public class TextThreadPoolTest {
    private static final Random random = new Random();

    /**
     * 任务失败自动重启
     */
    @Test
    public void testRunnableAutoRestart() {

        TextThreadPool threadPool = new TextThreadPool();
        TextAbstractRunnable restartRunnable = new TextAbstractRunnable() {
            @Override
            public boolean isForceExecution() {
                return true;
            }

            @Override
            public void onFailure(Exception t) {
                throw new RuntimeException("", t);
            }

            @Override
            public void doRun() throws Exception {
                //模拟任务执行耗时
                System.out.println("restartRunnable start...");
                sleepMills(2000);
                //模拟程序Runnable过程中出现了异常
                throw new RuntimeException("something unknown happen");
            }
        };
        threadPool.executor(TextThreadPool.Names.GENERIC).execute(restartRunnable);
        //
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {

        }
    }

    /**
     * 记录任务的执行时间
     */
    @Test
    public void testLoggingDuration() {
        //测试记录任务的执行时间
        TextThreadPool threadPool = new TextThreadPool();
        TextTimedRunnable timedRunnable = new TextTimedRunnable(new TextAbstractRunnable() {
            @Override
            public void onFailure(Exception t) {

            }

            @Override
            public void doRun() throws Exception {
                System.out.println("timed runnable start...");
                sleepMills(randInt(1000, 3000));
            }
        });

        threadPool.executor(TextThreadPool.Names.SINGLE).execute(timedRunnable);
        //
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {

        }
    }

    private static void sleepMills(long mills) {
        try {
            TimeUnit.MILLISECONDS.sleep(mills);
        } catch (InterruptedException e) {
            //ignore
        }
    }
    public static int randInt(int min, int max) {
        return random.ints(min, (max + 1)).limit(1).findFirst().getAsInt();
    }
}
```

2. 优化线程池的执行模式、
JDK默认线程池Executors：newFix 和 newSingle 创建的线程池任务队列长度是无界队列。newCache 创建的线程池创建线程个数是Integer.MAX_VALUE

当线程池的 core pool size 与 max pool size 不相等时，线程池执行任务的模式如下：
当线程个数小于 core pool size时，创建新线程执行任务，当线程个数创建到 core pool size个时，再有任务的到来，就会入任务队列排队等待。直到任务队列满了时，才会继续创建新线程直到max pool size个。此后，再有任务到来，就会被拒绝。
这种执行模式不能较好地利用CPU线程核数，并且对于长时间执行的任务（或者需要阻塞任务）不太友好。
com.textml.threadpool.queue.TextExecutorScalingQueue.offer
```java
    /**
     * LinkedTransferQueue 是无界队列, 重写offer方法 使之能够支持 core pool size 和 max pool size
     * 这样保证线程池优先创建 max pool size 个线程处理任务,后续的任务就入队列等待
     * @param e
     * @return
     */
    public boolean offer(E e) {
        //first try to transfer to a waiting worker thread
        //如果线程池中有空闲线程tryTransfer立即成功
        if (!tryTransfer(e)) {
         //检查线程池是否还可以继续创建新线程
            int leftThread = threadPoolExecutor.getMaximumPoolSize() - threadPoolExecutor.getCorePoolSize();
            if (leftThread > 0) {
                //线程池还可以继续创建线程,因此返回false触发新建线程
                //{@see java.util.concurrent.ThreadPoolExecutor.addWorker}
                return false;
            }else {
                return super.offer(e);
            }
        }else {
            return true;
        }
    }
```




## Reference
ElasticSearch 6.x 线程池模块
