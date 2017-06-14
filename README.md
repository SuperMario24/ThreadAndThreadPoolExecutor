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



























 
 
 
 
 
    
    
    
    
    
    
    
    
    
