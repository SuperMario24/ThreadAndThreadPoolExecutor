package com.example.saber.threadandthreadpooltest;

import android.app.IntentService;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * Created by saber on 2017/6/14.
 */

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
