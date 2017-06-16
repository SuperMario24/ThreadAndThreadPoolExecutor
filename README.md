# ThreadAndThreadPoolExecutor

一.概述

1.Android中除了Thread以外，可以扮演线程角色的还有很多，比如AsyncTask，IntentService，HandlerThread等等。
（1）AsyncTask封装了线程池和Handler，方便开发者在子线程中更新UI.
（2）HandlerThread是一个具有消息循环的线程，在他内部可以使用Handler。
（3）IntentService是一个服务，内部采用HandlerThread来执行任务，执行完毕后IntentService会自动退出。

二.Android中线程形态

1.AsyncTask

（1）AsyncTask是一个轻量级的异步任务类，不适用进行特别耗时的后台任务，对于特别耗时的任务来说，建议使用线程池。

（2）AsyncTask提供了4个核心方法：

---onPreExecute.在主线程中执行，在异步任务之前调用，用于做一些准备工作。

---doInBackground.执行异步任务，此方法中通过publishProgress方法来更新任务的进度，publishProgress会调用onProgressUpdate方法。

---onProgressUpdate（Progress...values），在主线程中执行，用于更新异步操作的进度。

---onPostExecute（Result result），主线程中执行，在异步任务执行之后，此方法会被调用，result是后台任务的返回值，即doInBackground方法的返回值。

AsyncTask还提供了onCancelled方法，同样在主线程中调用，当异步任务被取消时，onCancelled方法会被调用。


   private class DownloadFilesTask extends AsyncTask<URL, Integer, Long> {
        protected Long doInBackground(URL... urls) {
            int count = urls.length;
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                // totalSize += Downloader.downloadFile(urls[i]);
                publishProgress((int) ((i / (float) count) * 100));
                // Escape early if cancel() is called
                if (isCancelled())
                    break;
            }
            return totalSize;
        }

        protected void onProgressUpdate(Integer... progress) {
            // setProgressPercent(progress[0]);
        }

        protected void onPostExecute(Long result) {
            // showDialog("Downloaded " + result + " bytes");
        }
    }
    
 其中doInBackground是在线程池中进行的，onProgressUpdate运行在主线程。
 
 （3）AsyncTask的一些条件限制：
 
 ---AsyncTask的类必须在主线程中加载。
 
 ---AsyncTask的对象必须在主线程中创建。
 
 ---execute方法必须在主线程中调用。
 
 ---AsyncTask只能执行一次。
 
 ---在Android3.0之前，AsyncTask采用线程池处理并行任务，3.0之后改成串行。
 如果要遭3.0之后也用并行，必须使用executeOnExecutor方法。
 
   //采用AsyncTask的executeOnExecutor方法可以让Android3.0以上并行,AsyncTask.THREAD_POOL_EXECUTOR是AsyncTask真正执行任务的线程池
               
               if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){

                    new MyAsyncTask("AsyncTask#1").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#2").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#3").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#4").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#5").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");

                }
 
 （4）AsyncTask的工作原理：
 
 我们从AsyncTask的execute方法开始分析：
  
    @MainThread
    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
        return executeOnExecutor(sDefaultExecutor, params);-------sDefaultExecutor是一个线程池，异步任务在这个线程池中排队执行
    }
调用了 executeOnExecutor 方法：

    @MainThread
    public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec,
            Params... params) {
        if (mStatus != Status.PENDING) {
            switch (mStatus) {
                case RUNNING:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task is already running.");
                case FINISHED:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task has already been executed "
                            + "(a task can be executed only once)");
            }
        }

        mStatus = Status.RUNNING;
        //----------------------最先执行
        onPreExecute();

        mWorker.mParams = params;
        exec.execute(mFuture);

        return this;
    }

在executeOnExecutor方法中，onPreExecute()最先执行，然后线程池开始执行。下面开始分析线程池的执行过程：

    private static class SerialExecutor implements Executor {
        final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
        Runnable mActive;

        public synchronized void execute(final Runnable r) {
            mTasks.offer(new Runnable() {
                public void run() {
                    try {
                        r.run();
                    } finally {
                        //----------执行下一个任务
                        scheduleNext(); 
                    }
                }
            });
            if (mActive == null) {
                //----------执行下一个任务
                scheduleNext(); 
            }
        }

        protected synchronized void scheduleNext() {
            if ((mActive = mTasks.poll()) != null) {
                THREAD_POOL_EXECUTOR.execute(mActive);
            }
        }
    }
从SerialExecutor的实现可以分析AsyncTask的排队执行过程。首先系统会把AsyncTask的Params的参数封装为FutureTask对象，FutureTask是一个并发类，
在这里它充当了Runnable的作用。接着FutureTask会交给SerialExecutor的execute方法去处理，execute首先会把FutureTask插入到任务队列mTasks中，
如果这时候没有正在活动的AsyncTask任务，那么就会调用SerialExecutor的scheduleNext方法来执行下一个AsyncTask任务，同时当一个执行完后，会继续执行
下一个AsyncTask任务，直到所有任务都被执行为止，从这一点可以看出，AsyncTask是串行执行的。

AsyncTask中有两个线程池--------SerialExecutor和THREAD_POOL_EXECUTOR  和一个Handler。

SerialExecutor---------用于任务的排队。
THREAD_POOL_EXECUTOR--------真正执行任务的线程池。
InternalHandler-------------将执行环境从线程池切换到主线程。


在AsyncTask的构造方法中有如下这么一段代码：

      mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
                mTaskInvoked.set(true);

                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                //noinspection unchecked
                Result result = doInBackground(mParams);
                Binder.flushPendingCommands();
                return postResult(result);
            }
        };
由于FutureTask的run方法会调用mWorker的call方法，因此mWorker的call方法最终会在线程池中执行，在mWorker的call方法中，首先将 mTaskInvoked
设为true，表示当前任务已经被调用过了，然后执行AsyncTask的doInBackground方法，将其返回值传递给postResult方法，它的实现如下：

    private Result postResult(Result result) {
        @SuppressWarnings("unchecked")
        Message message = getHandler().obtainMessage(MESSAGE_POST_RESULT,
                new AsyncTaskResult<Result>(this, result));
        message.sendToTarget();
        return result;
    }
    
 postResule方法会通过sHandler（InternalHandler）发送一个MESSAGE_POST_RESULT的消息，这个sHandler的定义如下：

      private static class InternalHandler extends Handler {
              public InternalHandler() {
                  super(Looper.getMainLooper());
              }

              @SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
              @Override
              public void handleMessage(Message msg) {
                  AsyncTaskResult<?> result = (AsyncTaskResult<?>) msg.obj;
                  switch (msg.what) {
                      case MESSAGE_POST_RESULT:
                          // There is only one result
                          result.mTask.finish(result.mData[0]);
                          break;
                      case MESSAGE_POST_PROGRESS:
                          result.mTask.onProgressUpdate(result.mData);
                          break;
                  }
              }
          }

为了能够将执行环境切换到主线程，这就要求sHandler必须在主线程中创建，所有要求AsyncTask的类必须在主线程中加载，sHandler收到消息后会调用AsyncTask
的finish方法：

      private void finish(Result result) {
           if (isCancelled()) {
               onCancelled(result);
           } else {
               onPostExecute(result);
           }
           mStatus = Status.FINISHED;
       }
到这里完成了AsyncTask的回调，切换到主线程执行。


2.HandlerThread

HandlerThread是一个可以使用Handler的Thread，它的实现很简单，就是在run方法中通过Looper.prepare来创建消息队列，并通过Looper.loop()来开启消息循环.

          public void run() {
              mTid = Process.myTid();
              Looper.prepare();
              synchronized (this) {
                  mLooper = Looper.myLooper();
                  notifyAll();
              }
              Process.setThreadPriority(mPriority);
              onLooperPrepared();
              Looper.loop();
              mTid = -1;
          }
他和普通的Thread明显不同，普通的Thread主要用于在run方法中执行一个耗时任务，而HandlerThread在内部创建了消息队列，外界需要通过Handler的消息方式
来通知HandlerThread执行一个具体的任务。HandlerThread一个具体的使用场景是IntentService。HandlerThread的run方法是一个无限循环，所以当不需要再使用HandlerThread时，可以通过它的quit或者quitSafrly方法来终止线程执行。



2.HandlerThread

HandlerThread继承了Thread，他是一种可以使用Handler的Thread，它的实现原理也很简单，就是在run方法中通过Looper.prepare来创建消息队列，并通过
Looper.loop()来开启消息循环。

       @Override
          public void run() {
              mTid = Process.myTid();
              //创建消息队列
              Looper.prepare();
              synchronized (this) {
                  mLooper = Looper.myLooper();
                  notifyAll();
              }
              Process.setThreadPriority(mPriority);
              onLooperPrepared();
              //开启消息循环
              Looper.loop();
              mTid = -1;
          }

HandlerThread的具体使用场景是IntentService，HandlerThread的run方法是一个无线循环，因此当明确不在使用ThreadThread时，应调用它的quit或者
quitSafely来终止线程的执行。




3.IntnetService


IntentService继承了Service并且是一个抽象类，必须创建它的子类才能使用IntentService，IntentService封装了HandlerThread和Handler，这一点可以
通过它的onCreate方法可以看出来：

    @Override
       public void onCreate() {
           // TODO: It would be nice to have an option to hold a partial wakelock
           // during processing, and to have a static startService(Context, Intent)
           // method that would launch the service & hand off a wakelock.

           super.onCreate();
           //创建HandlerThread
           HandlerThread thread = new HandlerThread("IntentService[" + mName + "]");
           thread.start();
           //获取HandlerThread的Looper
           mServiceLooper = thread.getLooper();
           //通过Looper创建HandlerThread的Handler
           mServiceHandler = new ServiceHandler(mServiceLooper);
       }

当IntentService第一次启动时，它的onCreate方法会被调用，onCreate方法会创建一个HandlerThread，并且获取它的Looper，再通过它的Looper构造一个
Handler对象mServiceHandler这样通过mServiceHandler发送的消息都会在HandlerThread中执行。每次启动IntentService，它的onStartCommand方法
都会被调用一次，IntentService在onStartCommand中处理每个后台任务。onStartCommand调用了onStart：

          @Override
          public void onStart(Intent intent, int startId) {
              Message msg = mServiceHandler.obtainMessage();
              msg.arg1 = startId;
              msg.obj = intent;
              mServiceHandler.sendMessage(msg);
          }

可以看出IntentService仅仅是通过mServiceHandler发送了一个消息，这个消息会在HandlerThread中被处理。mServiceHandler收到消息后，会将Intent
传递给onHandleIntent处理。这个Intent和startService（intent）中的intent的内容是完全一致的，通过这个Intent就可以解析出外界启动IntentService
时所传的参数，通过这些参数来区分具体的后台任务。
onHandleIntent方法执行结束后，IntentService会通过stopSelf（int startId）方法来尝试停止服务，这里不使用stopSelf时因为stopSelf会立刻停止服务，stopSelf（int startId）则会等待所有的消息都处理完毕后才会终止服务。一般来说，stopSelf（int startId）在停止服务之前会判断最近启动的服务次数是否和startId相等，如果相等，则立刻停止服务，不相等则不停止。

ServiceHandler的实现如下：也就是HandlerThread的Handler。

       private final class ServiceHandler extends Handler {
              public ServiceHandler(Looper looper) {
                  super(looper);
              }

              @Override
              public void handleMessage(Message msg) {
                  onHandleIntent((Intent)msg.obj);
                  stopSelf(msg.arg1);
              }
          }
          
另外，每执行一个后台任务就必须启动一次IntentService，而IntentService内部会通过消息的方式向HandlerThread请求执行任务，Handler中的Looper是顺序处理消息的，这就意味着IntentService也是顺序执行后台任务的。

下面举个例子说明IntentService的工作方式：

         public class LocalIntentService extends IntentService{

             private static final String TAG = "LocalIntentService";

             public LocalIntentService() {
                 super(TAG);
             }

             @Override
             protected void onHandleIntent(Intent intent) {
                 String action = intent.getStringExtra("task_action");
                 Log.d(TAG, "receive task: "+action);
                 SystemClock.sleep(3000);
                 if("TASK1".equals(action)){
                     Log.d(TAG, "handle task:"+action);
                 }
             }

             @Override
             public void onDestroy() {
                 Log.d(TAG, "service onDestroy");
                 super.onDestroy();
             }
         }
         
启动代码：

                Intent service = new Intent(MainActivity.this,LocalIntentService.class);
                service.putExtra("task_action","TASK1");
                startService(service);
                service.putExtra("task_action","TASK2");
                startService(service);
                service.putExtra("task_action","TASK3");
                startService(service);
          
三个后台任务是排队执行的，他们的执行顺序就是它们发起请求的顺序。



三.Android中的线程池

1.线程池的优点：
（1）重用线程池中的线程，减少性能开销
（2）能有效控制线程池的最大并发数，避免大量线程池之间相互抢占系统资源而导致阻塞的现象
（3）能够对线程池进行简单的管理

2.线程池的概念源于Java中的Executor，Executor是一个接口，真正的线程池的实现为ThreadPoolExecutor。


3.ThreadPoolExecutor

线程池的真正实现，它的构造方法提供了一系列的参数来配置线程池：

      public ThreadPoolExecutor(int corePoolSize,-----------------------核心线程数
                                 int maximumPoolSize,---------------------线程池所能容纳的最大线程数
                                 long keepAliveTime,------------------------超时时长
                                 TimeUnit unit,-------------------------超时时长的单位
                                 BlockingQueue<Runnable> workQueue,-----------线程池中的任务队列
                                 ThreadFactory threadFactory) {----------------为线程池提供创建新线程的功能
           this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, defaultHandler);
       }


如果将ThreadPoolExecutor的allowCoreThreadTimeOut设为true的话，那么闲置的核心线程超过keepAliveTime后也会被终止回收，默认是设为false的。
ThreadFactory是一个接口，他只有一个方法：Thread new Thread（Runnable r）.


除了上面的这些主要参数外，ThreadPoolExecutor还有一个不常用的参数RejectedExecutionHandler Handler，当线程池无法执行任务时，这可能是任务队列
满了了或者是无法成功执行任务，这时ThreadPoolExecutor会调用handler的rejectedExecution方法来通知调用者，默认情况下rejectedExecution方法会直接
抛出一个RejectedExecutionException异常。



ThreadPoolExecutor执行时大致遵循如下规则：
（1）线程池中的线程数未达到核心线程的数量，那么会直接启动一个核心线程来执行任务
（2）如果线程池中的线程数量已经达到或者超过核心线程的数量，那么任务会被插入任务队列等待执行
（3）如果（2）中无法将任务插入任务队列，这往往是由于任务队列已满，这个时候如果线程数量未达到线程池规定的最大值，那么会启动一个非核心线程来执行任务
（4）如果（3）中线程数量已经达到线程池的最大线程数，那么会拒绝执行任务，ThreadPoolExecutor会调用handler的rejectedExecution方法来抛出异常。


ThreadPoolExecutor的参数在AsyncTask中有明显的体现：

       private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
          private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
          private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
          private static final int KEEP_ALIVE = 1;

          private static final ThreadFactory sThreadFactory = new ThreadFactory() {
              private final AtomicInteger mCount = new AtomicInteger(1);

              public Thread newThread(Runnable r) {
                  return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
              }
          };

          private static final BlockingQueue<Runnable> sPoolWorkQueue =
                  new LinkedBlockingQueue<Runnable>(128);

          /**
           * An {@link Executor} that can be used to execute tasks in parallel.
           */
          public static final Executor THREAD_POOL_EXECUTOR
                  = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                          TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);
          
1.核心线程数等于CPU核心数+1
2.线程池的最大线程数为CPU核心数的2倍+1
3.核心线程无超时机制，非核心线程超时时间为1秒
4.队列容量为128






4.线程池的分类：


（1）FixedThreadPool

          public static ExecutorService newFixedThreadPool(int nThreads) {
              return new ThreadPoolExecutor(nThreads, nThreads,
                                            0L, TimeUnit.MILLISECONDS,
                                            new LinkedBlockingQueue<Runnable>());
          }

1.线程数量固定
2.线程都是核心线程
3.当线程处于空闲状态时，不会被回收。除非线程池关闭了
4.任务队列没有大小限制
5.这意味它能够更加快速的响应外界的请求




（2）CachedThreadPool

                public static ExecutorService newCachedThreadPool() {
                    return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                                  60L, TimeUnit.SECONDS,
                                                  new SynchronousQueue<Runnable>());
                }
          
1.线程数量不固定
2.没有核心线程
3.超时时长为60秒
4.适合执行大量的耗时较少的任务，当整个任务处于闲置状态时，线程池中的线程都会因为超时被终止，这个时候CachedThreadPool实际上是没有任何线程的，它几乎
不占用任何系统资源




（3）ScheduledThreadPool
         
      public ScheduledThreadPoolExecutor(int corePoolSize) {
           super(corePoolSize, Integer.MAX_VALUE,
                 DEFAULT_KEEPALIVE_MILLIS, MILLISECONDS,
                 new DelayedWorkQueue());
       }
       
1.核心线程是固定的，非核心线程是没有限制的
2.非核心线程闲置时会被立刻回收
3.主要用于执行定时任务和具有固定周期的重复任务



（4）SingleThreadExecutor
         
           public static ExecutorService newSingleThreadExecutor() {
              return new FinalizableDelegatedExecutorService
                  (new ThreadPoolExecutor(1, 1,
                                          0L, TimeUnit.MILLISECONDS,
                                          new LinkedBlockingQueue<Runnable>()));
          } 
  
  
1.只有核心线程
2.确保所有的任务都在同一个线程池中按顺序执行
3.意义在于统一所有的外界任务到一个线程池中，这使得不需要处理线程同步问题


使用方法：


               Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        Log.d(TAG, "正在执行耗时任务："+sdf.format(new Date()));
                        SystemClock.sleep(5000);
                        Log.d(TAG, "任务结束："+sdf.format(new Date()));


                    }
                };

                ExecutorService fixedThreadPool = Executors.newFixedThreadPool(4);
                fixedThreadPool.execute(runnable);

                ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
                cachedThreadPool.execute(runnable);

                ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(4);
                //2000ms后执行runnable
                scheduledThreadPool.schedule(runnable,2000, TimeUnit.MILLISECONDS);
                //延迟10ms后，每隔1000ms执行一次runnable
                scheduledThreadPool.scheduleAtFixedRate(runnable,10,1000,TimeUnit.MILLISECONDS);



                ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
                singleThreadExecutor.execute(runnable);
          
          
          
          









































 
 
 
 
 
    
    
    
    
    
    
    
    
    
