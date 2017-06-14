package com.example.saber.threadandthreadpooltest;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button btnStartDownload;
    private Button btnPauseDownload;
    private Button btnCancelDownload;

    private Button btnStartAsyncTask;
    private Button btnStartIntentService;
    private Button btnStartThreadPoolExecutor;

    private DownloadService.DownloadBind binder;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (DownloadService.DownloadBind) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartDownload = (Button) findViewById(R.id.btn_start_download);
        btnPauseDownload = (Button) findViewById(R.id.btn_pause_download);
        btnCancelDownload = (Button) findViewById(R.id.btn_cancel_download);

        btnStartAsyncTask = (Button) findViewById(R.id.btn_start_asynctask);
        btnStartIntentService = (Button) findViewById(R.id.btn_start_intentservice);
        btnStartThreadPoolExecutor = (Button) findViewById(R.id.btn_start_ThreadPoolExecutor);

        //启动服务
        Intent intent = new Intent(this,DownloadService.class);
        startService(intent);
        bindService(intent,connection,BIND_AUTO_CREATE);

        //运行时权限
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }


        /**
         * 开始下载
         */
        btnStartDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(binder == null){
                    return;
                }
                String url = "http://raw.githubusercontent.com/guolindev/eclipse/master/eclipse-inst-win64.exe";
                binder.startDownload(url);

            }
        });

        /**
         * 暂停下载
         */
        btnPauseDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(binder == null){
                    return;
                }
                binder.pauseDownload();
            }
        });

        /**
         * 取消下载
         */
        btnCancelDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(binder == null){
                    return;
                }
                binder.cancelDownload();
            }
        });


        /**
         * 开启多个AsyncTask任务
         */
        btnStartAsyncTask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //Android 3.0以上是串行的，3.0以下是并行的
//                new MyAsyncTask("AsyncTask#1").execute();
//                new MyAsyncTask("AsyncTask#2").execute();
//                new MyAsyncTask("AsyncTask#3").execute();
//                new MyAsyncTask("AsyncTask#4").execute();
//                new MyAsyncTask("AsyncTask#5").execute();


                //采用AsyncTask的executeOnExecutor方法可以让Android3.0以上并行,AsyncTask.THREAD_POOL_EXECUTOR是AsyncTask真正执行任务的线程池
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){

                    new MyAsyncTask("AsyncTask#1").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#2").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#3").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#4").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");
                    new MyAsyncTask("AsyncTask#5").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"");

                }

            }
        });

        /**
         * 启动IntentService
         */
        btnStartIntentService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent service = new Intent(MainActivity.this,LocalIntentService.class);
                service.putExtra("task_action","TASK1");
                startService(service);
                service.putExtra("task_action","TASK2");
                startService(service);
                service.putExtra("task_action","TASK3");
                startService(service);
            }
        });


        /**
         * 开启线程池
         */
        btnStartThreadPoolExecutor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        Log.d(TAG, "正在执行耗时任务："+sdf.format(new Date()));
                        SystemClock.sleep(5000);
                        Log.d(TAG, "任务结束："+sdf.format(new Date()));


                    }
                };

//                ExecutorService fixedThreadPool = Executors.newFixedThreadPool(4);
//                fixedThreadPool.execute(runnable);

//                ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
//                cachedThreadPool.execute(runnable);

                ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(4);
                //2000ms后执行runnable
                scheduledThreadPool.schedule(runnable,2000, TimeUnit.MILLISECONDS);
                //延迟10ms后，每隔1000ms执行一次runnable
                scheduledThreadPool.scheduleAtFixedRate(runnable,10,1000,TimeUnit.MILLISECONDS);



//                ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
//                singleThreadExecutor.execute(runnable);


            }
        });

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if(grantResults.length>0 && grantResults[0] != PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this, "拒绝权限将无法使用程序", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {

        unbindService(connection);
        super.onDestroy();

    }

    class MyAsyncTask extends AsyncTask<String,Integer,String>{

        private String mName = "AsyncTask";

        public MyAsyncTask(String name){
            super();
            mName = name;
        }

        @Override
        protected String doInBackground(String... params) {

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return mName;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Log.e(TAG,s+"execute finish at"+sdf.format(new Date()));
        }
    }



}
