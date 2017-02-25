package com.baidu.bce.livecamera.demo;

import java.util.TimerTask;

import com.baidu.bce.livecamera.utils.NetworkUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

public class SplashActivity extends Activity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        if (!checkNetwork()) {
            return;
        }
        new Handler().postDelayed(new TimerTask() {
            @Override
            public void run() {
                SplashActivity.this.startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        }, 700);
        // checkUpdate();
    }

    private boolean checkNetwork() {
        if (!NetworkUtils.isConnected(this)) {
            Toast.makeText(this, "请检查网络状态！", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void checkUpdate() {
        Toast.makeText(this, "检查新版本...", Toast.LENGTH_SHORT).show();
    }
}
