package com.example.saber.threadandthreadpooltest;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import java.io.File;

/**
 * Created by saber on 2017/3/25.
 */

public class DownloadService extends Service {

    private DownloadTask task;

    private String downloadUrl;

    private DownloadListener listener = new DownloadListener() {
        @Override
        public void onProgress(int progress) {
            getNotificationManager().notify(1,getNotification("Download...",progress));
        }

        @Override
        public void onSuccess() {
            task = null;
            //下载成功时将前台服务通知关闭，并创建一个下载成功的通知
            stopForeground(true);
            getNotificationManager().notify(1,getNotification("Download Success",-1));
            Toast.makeText(DownloadService.this, "Download Success", Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onFailed() {
            task = null;
            //关闭前台服务通知，创建一个下载失败的通知
            stopForeground(true);
            getNotificationManager().notify(1,getNotification("Download Failed",-1));
            Toast.makeText(DownloadService.this, "Download Failed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPaused() {
            task = null;
            Toast.makeText(DownloadService.this, "Paused", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCanceled() {
            task = null;
            stopForeground(true);
            Toast.makeText(DownloadService.this, "Canceld", Toast.LENGTH_SHORT).show();
        }
    };

    private DownloadBind binder = new DownloadBind();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    //Binder内部类，写Service中执行的方法
    class DownloadBind extends Binder{

        /**
         * 开始下载
         * @param url
         */
        public void startDownload(String url){
            if(task == null){
                downloadUrl = url;
                task = new DownloadTask(listener);
                task.execute(downloadUrl);

                startForeground(1,getNotification("Download...",0));
                Toast.makeText(DownloadService.this, "Download...", Toast.LENGTH_SHORT).show();

            }
        }

        /**
         * 暂停下载
         */
        public void pauseDownload(){
            if(task != null){
                task.pauseDownload();//设置isPaused = true;
            }
        }

        /**
         * 取消下载
         */
        public void cancelDownload(){
            if(task != null){
                task.cancelDownload();//设置isCanceled = true;
            }else {
                if(downloadUrl != null){
                    //取消下载时，将文件删除，并关闭通知
                    String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
                    String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                    File file = new File(directory + fileName);
                    if(file.exists()){
                        file.delete();
                    }

                    //关闭通知
                    getNotificationManager().cancel(1);
                    stopForeground(true);
                    Toast.makeText(DownloadService.this, "Canceled", Toast.LENGTH_SHORT).show();


                }
            }
        }
    }


    //获取NotificationManager
    private NotificationManager getNotificationManager(){
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private Notification getNotification(String title,int progress){
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,intent,0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                .setContentIntent(pi)
                .setContentTitle(title);

        if(progress > 0){
            builder.setContentText(progress+"%");
            builder.setProgress(100,progress,false);//第一个参数：最大进度   第二个：当前进度   第三个：是否模糊进度条
        }

        return builder.build();

    }

}
