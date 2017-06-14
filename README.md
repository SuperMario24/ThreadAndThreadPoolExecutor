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

        onPreExecute();----------------------最先执行

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
                        scheduleNext(); ----------执行下一个任务
                    }
                }
            });
            if (mActive == null) {
                scheduleNext(); ----------执行下一个任务
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















 
 
 
 
 
    
    
    
    
    
    
    
    
    
