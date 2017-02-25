package com.baidu.bce.livecamera.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "SettingActivity";

    // TODO:: update your rtmp here url
    // private String mStreamKey =
    // "rtmp://push.bcelive.com/live/xxxxxxxxxxxxxxxx";
    private String mStreamKey = "rtmp://push.bcelive.com/live/trtsifeoo14ik8bsd0";

    private LinearLayout mLoadingAnimation = null;
    private EditText mStreamUrlET = null;

    private Button mStartButton = null;

    private SharedPreferences mSharedPreferences = null;

    /**
     * 横屏竖屏
     */
    private boolean isOritationSwitcherChecked = false;

    // Bitrate related params
    private int mSupportedBitrateValues[] = new int[] { 2000, 1200, 800, 600 };

    /**
     * Resolution related params
     * 分辨率相关的参数
      */
    private int mSupportedResolutionValues[] = new int[] { 1920, 1080, 1280, 720, 640, 480, 480, 360 };
    /**选择分辨率的索引*/
    private int mSelectedResolutionIndex = 1;

    // Frame rate ralated params
    private int mSupportedFramerateValues[] = new int[] { 18, 15, 15, 15 };

    private Handler mUIEventHandler = null;
    private static final int UI_EVNET_HIDE_LOADING_ANIMATION = 0;
    private static final int UI_EVENT_SHOW_STREAMING_ACTIVITY = 1;
    private static final int UI_EVENT_HINT_NO_STREAMING_URL = 2;

    private long mLastPressBackTime = 0;
    private static final int INTERVAL_OF_TWO_CLICK_TO_QUIT = 1000; // 1 seconde

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Calling onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        mSharedPreferences = getApplication().getSharedPreferences("BCELive", Context.MODE_PRIVATE);

        initUIHandler();
        initUIElements();
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - mLastPressBackTime < INTERVAL_OF_TWO_CLICK_TO_QUIT) {
            saveStreamParams();
//            Intent intent = new Intent();
//            intent.putExtra("has_logout", false);
//            setResult(RESULT_OK, intent);
            finish();
        } else {
            Toast.makeText(this, "再次按下返回键将退出应用！", Toast.LENGTH_SHORT).show();
            mLastPressBackTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Calling onStart()");
        super.onStart();
    }
    
    @Override
    protected void onResume() {
        Log.i(TAG, "Calling onResume()");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "Calling onPause()");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "Calling onStop()");
        saveStreamParams();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Calling onDestroy()");
        mUIEventHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void initUIElements() {
        mLoadingAnimation = (LinearLayout) findViewById(R.id.loading_anim);

        fetchStreamParams();

        mStreamUrlET = (EditText) findViewById(R.id.et_streamurl);
        mStreamUrlET.setText(mStreamKey);

        RadioGroup orientationRadioGroup = (RadioGroup) findViewById(R.id.radioGroup1);
        final RadioButton radioLandscape = (RadioButton) findViewById(R.id.radioLandscape);
        final RadioButton radioPortrait = (RadioButton) findViewById(R.id.radioPortrait);
        orientationRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioLandscape) {
                    isOritationSwitcherChecked = true;
                    radioLandscape.setTextColor(Color.WHITE);
                    radioPortrait.setTextColor(0xff666666);
                } else {
                    isOritationSwitcherChecked = false;
                    radioLandscape.setTextColor(0xff666666);
                    radioPortrait.setTextColor(Color.WHITE);
                }
            }
        });
        if (isOritationSwitcherChecked) {
            radioLandscape.setChecked(true);
            radioLandscape.setTextColor(Color.WHITE);
        } else {
            radioPortrait.setChecked(true);
            radioPortrait.setTextColor(Color.WHITE);
        }

        RadioGroup resolutionRadioGroup = (RadioGroup) findViewById(R.id.radioGroup0);
        final RadioButton radio1080P = (RadioButton) findViewById(R.id.radio1080p);
        final RadioButton radio720P = (RadioButton) findViewById(R.id.radio720p);
        final RadioButton radio480P = (RadioButton) findViewById(R.id.radio480p);
        final RadioButton radio360P = (RadioButton) findViewById(R.id.radio360p);
        resolutionRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                radio1080P.setTextColor(0xff666666);
                radio720P.setTextColor(0xff666666);
                radio480P.setTextColor(0xff666666);
                radio360P.setTextColor(0xff666666);
                switch (checkedId) {
                case R.id.radio1080p:
                    mSelectedResolutionIndex = 0;
                    radio1080P.setTextColor(Color.WHITE);
                    break;
                case R.id.radio720p:
                    mSelectedResolutionIndex = 1;
                    radio720P.setTextColor(Color.WHITE);
                    break;
                case R.id.radio480p:
                    mSelectedResolutionIndex = 2;
                    radio480P.setTextColor(Color.WHITE);
                    break;
                case R.id.radio360p:
                    mSelectedResolutionIndex = 3;
                    radio360P.setTextColor(Color.WHITE);
                    break;
                }
            }
        });

        switch (mSelectedResolutionIndex) {
        case 0:
            radio1080P.setChecked(true);
            radio1080P.setTextColor(Color.WHITE);
            break;
        case 1:
            radio720P.setChecked(true);
            radio720P.setTextColor(Color.WHITE);
            break;
        case 2:
            radio480P.setChecked(true);
            radio480P.setTextColor(Color.WHITE);
            break;
        case 3:
            radio360P.setChecked(true);
            radio360P.setTextColor(Color.WHITE);
            break;
        }

        mStartButton = (Button) findViewById(R.id.btn_start);
    }

    private void initUIHandler() {
        mUIEventHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case UI_EVNET_HIDE_LOADING_ANIMATION:
                    mLoadingAnimation.setVisibility(View.GONE);
                    mStartButton.setEnabled(true);
                    break;
                case UI_EVENT_SHOW_STREAMING_ACTIVITY:
                    /**
                     * 点击开始推流
                     */
                    Intent intent = new Intent(MainActivity.this, StreamingActivity.class);
                    /**保存摄像头方向*/
                    saveStreamParams();
                    /**推流地址*/
                    intent.putExtra("push_url", mStreamKey.trim());
                    /**推流视频宽度*/
                    intent.putExtra("res_w", mSupportedResolutionValues[mSelectedResolutionIndex * 2]);
                    /**推流视频高度*/
                    intent.putExtra("res_h", mSupportedResolutionValues[mSelectedResolutionIndex * 2 + 1]);
                    /**视频帧率*/
                    intent.putExtra("frame_rate", mSupportedFramerateValues[mSelectedResolutionIndex]);
                    /**视频码率*/
                    intent.putExtra("bitrate", mSupportedBitrateValues[mSelectedResolutionIndex]);
                    /**摄像头方向*/
                    intent.putExtra("oritation_landscape", isOritationSwitcherChecked);
                    startActivity(intent);
                    break;
                case UI_EVENT_HINT_NO_STREAMING_URL:
                    mStreamKey = mSharedPreferences.getString("user_avatar", "");
                    Toast.makeText(MainActivity.this, "未获取到有效的推流地址，已使用上次推流的地址！", Toast.LENGTH_SHORT).show();
                    mUIEventHandler.sendEmptyMessage(UI_EVENT_SHOW_STREAMING_ACTIVITY);
                    break;
                default:
                    break;
                }
                super.handleMessage(msg);
            }

        };

    }

    public void onClickQuit(View v) {
        saveStreamParams();
    }

    /**
     * 获取流的配置参数（分辨和横竖屏）
     */
    private void fetchStreamParams() {
        mSelectedResolutionIndex = mSharedPreferences.getInt("resolution", 1);
        isOritationSwitcherChecked = mSharedPreferences.getBoolean("oritation_landscape", false);
    }

    private void saveStreamParams() {
        Editor editor = mSharedPreferences.edit();
        editor.putInt("resolution", mSelectedResolutionIndex);
        editor.putBoolean("oritation_landscape", isOritationSwitcherChecked);
        editor.commit();
    }

    public void onClickStart(View v) {
        mStreamKey = mStreamUrlET.getText().toString();
        if (!TextUtils.isEmpty(mStreamKey)) {
            mUIEventHandler.sendEmptyMessage(UI_EVENT_SHOW_STREAMING_ACTIVITY);
        } else {
            Toast.makeText(this, "注意：推流地址不能为空！！", Toast.LENGTH_SHORT).show();
        }
    }
}
