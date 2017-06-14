package com.example.saber.threadandthreadpooltest;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by saber on 2017/3/25.
 *异步执行下载任务，在工作线程中执行
 *
 */

public class DownloadTask extends AsyncTask<String,Integer,Integer> {

    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;

    private DownloadListener listener;

    private boolean isPaused = false;
    private boolean isCanceled = false;

    private int lastProgress;

    public DownloadTask(DownloadListener listener){
        this.listener = listener;
    }



    @Override
    protected Integer doInBackground(String... params) {
        InputStream is = null;
        File file = null;
        RandomAccessFile savedFile = null;


        try {
            //下载的字节数
            long downloadedLength = 0;
            //下载的url
            String url = params[0];
            //创建下载文件
            String fileName = url.substring(url.lastIndexOf("/"));
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            file = new File(directory+fileName);

            if(file.exists()){
                downloadedLength = file.length();//读取文件的字节数
            }
            long contentLength = getContentLength(url);//获取url文件的总字节数
            //如果获取到的文件总字节数为0 下载失败
            if(contentLength == 0){
                return TYPE_FAILED;
            }else if(contentLength == downloadedLength){
                //已下载的字节和文件总字节相等
                return TYPE_SUCCESS;
            }

            //okHttp请求
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .addHeader("RANGE","bytes="+downloadedLength+"-")//断点下载，指定从哪个字节下载
                    .url(url)
                    .build();
            Response response = client.newCall(request).execute();

            if(response != null){
                is = response.body().byteStream();//服务器返回的数据，转换为字节
                savedFile = new RandomAccessFile(file,"rw");//保存记录数据的文件
                savedFile.seek(downloadedLength);//跳过已下载的字节
                byte[] b = new byte[1024];
                int total = 0;
                int len;

                //在下载的过程中判断用户是否点击了暂停或者取消
                while((len = is.read(b))!=-1){
                    if(isCanceled){
                        return TYPE_CANCELED;
                    }else if(isPaused){
                        return  TYPE_PAUSED;
                    }else {
                        total += len;
                        savedFile.write(b,0,len);

                        //计算已下载的百分比
                        int progress = (int) ((total + downloadedLength)*100/contentLength);

                        //更新进度，相当于发消息
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }


        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try{
                if(is != null){
                    is.close();
                }
                if(savedFile != null){
                    savedFile.close();
                }
                if(isCanceled && file != null){
                    file.delete();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        return TYPE_FAILED;
    }


    /**
     * 更新下载进度
     * @param values
     */
    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        if(progress > lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    @Override
    protected void onPostExecute(Integer integer) {
        switch (Integer.valueOf(integer)){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;

        }
    }

    public void pauseDownload(){
        isPaused = true;
    }

    public void cancelDownload(){
        isCanceled = true;
    }
    /**
     * okhttp获取数据
     * @param url
     * @return
     */
    private long getContentLength(String url) {


        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = client.newCall(request).execute();
            if(response != null && response.isSuccessful()){
                long contentLength = response.body().contentLength();
                response.close();
                return contentLength;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }



}
