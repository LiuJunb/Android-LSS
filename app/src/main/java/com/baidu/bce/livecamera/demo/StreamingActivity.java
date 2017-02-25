package com.baidu.bce.livecamera.demo;

import com.baidu.recorder.api.LiveConfig;
import com.baidu.recorder.api.LiveSession;
import com.baidu.recorder.api.LiveSessionHW;
import com.baidu.recorder.api.LiveSessionSW;
import com.baidu.recorder.api.SessionStateListener;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.GestureDetectorCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class StreamingActivity extends Activity
        implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    private static final String TAG = "StreamingActivity";
    private LiveSession mLiveSession = null;
    private Button mRecorderButton = null;
    private ImageView mFocusIcon = null;
    private Button mFlashStateButton = null;
    private Button mBeautyEffectStateButton = null;
    private TextView statusView = null;
    private LinearLayout mLoadingAnimation = null;
    private boolean isSessionReady = false;
    private boolean isSessionStarted = false;
    private boolean isConnecting = false;
    private boolean needRestartAfterStopped = false;

    private static final int UI_EVENT_RECORDER_CONNECTING = 0;
    private static final int UI_EVENT_RECORDER_STARTED = 1;
    private static final int UI_EVENT_RECORDER_STOPPED = 2;
    private static final int UI_EVENT_SESSION_PREPARED = 3;
    private static final int UI_EVENT_HIDE_FOCUS_ICON = 4;
    private static final int UI_EVENT_RESTART_STREAMING = 5;
    private static final int UI_EVENT_RECONNECT_SERVER = 6;
    private static final int UI_EVENT_STOP_STREAMING = 7;
    private static final int UI_EVENT_SHOW_TOAST_MESSAGE = 8;
    private static final int UI_EVENT_RESIZE_CAMERA_PREVIEW = 9;
    private static final int TEST_EVENT_SHOW_UPLOAD_BANDWIDTH = 10;
    private Handler mUIEventHandler = null;
    private SurfaceView mCameraView = null;
    private SessionStateListener mStateListener = null;
    private GestureDetectorCompat mDetector = null;
    // private SurfaceHolder mHolder = null;
    private int mCurrentCamera = -1;
    private boolean isFlashOn = false;
    private boolean hasBueatyEffect = false;

    private int mVideoWidth = 1280;
    private int mVideoHeight = 720;
    private int mFrameRate = 15;
    private int mBitrate = 1024000;
    private String mStreamingUrl = null;
    private boolean isOritationLanscape = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        /** NO_TITLE  SCREEN_ON   FULLSCREEN */
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        win.requestFeature(Window.FEATURE_NO_TITLE);
        /**摄像头方向*/
        isOritationLanscape = getIntent().getBooleanExtra("oritation_landscape", false);
        if (isOritationLanscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setContentView(R.layout.activity_streaming_landscape);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            setContentView(R.layout.activity_streaming_portrait);
        }
        /**推流视频宽度*/
        mVideoWidth = getIntent().getIntExtra("res_w", 1280);
        /**推流视频高度*/
        mVideoHeight = getIntent().getIntExtra("res_h", 720);
        /**视频帧率*/
        mFrameRate = getIntent().getIntExtra("frame_rate", 15);
        /**视频码率*/
        mBitrate = getIntent().getIntExtra("bitrate", 1024) * 1000;
        /**推流地址*/
        mStreamingUrl = getIntent().getStringExtra("push_url");

        initUIElements();

        /**摄像头为前置摄像头*/
        mCurrentCamera = LiveConfig.CAMERA_FACING_FRONT;
        /**是否开始散光灯*/
        isFlashOn = false;
        initUIEventHandler();
        /**推流状态的监听*/
        initStateListener();
        /**初始化推流会话*/
        initRTMPSession(mCameraView.getHolder());

        mDetector = new GestureDetectorCompat(this, this);
        mDetector.setOnDoubleTapListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged orientation=" + newConfig.orientation);
        super.onConfigurationChanged(newConfig);
    }

    private void initUIElements() {
        mLoadingAnimation = (LinearLayout) findViewById(R.id.loading_anim);
        mRecorderButton = (Button) findViewById(R.id.btn_streaming_action);
        mRecorderButton.setEnabled(false);
        mCameraView = (SurfaceView) findViewById(R.id.sv_camera_preview);
        mFocusIcon = (ImageView) findViewById(R.id.iv_ico_focus);
        mFlashStateButton = (Button) findViewById(R.id.iv_flash_state);
        mBeautyEffectStateButton = (Button) findViewById(R.id.iv_effect_state);
        statusView = (TextView) findViewById(R.id.tv_streaming_action);
    }

    private void initUIEventHandler() {
        mUIEventHandler = new Handler() {

            public void handleMessage(Message msg) {
                switch (msg.what) {
                    /**连接中*/
                    case UI_EVENT_RECORDER_CONNECTING:
                        isConnecting = true;
                        mRecorderButton.setBackgroundResource(R.drawable.btn_block_streaming);
                        mRecorderButton.setPadding(0, 0, 0, 0);
                        statusView.setText("连接中");
                        mRecorderButton.setEnabled(false);
                        break;
                    /**直播中*/
                    case UI_EVENT_RECORDER_STARTED:
                        Log.i(TAG, "Starting Streaming succeeded!");
                        serverFailTryingCount = 0;
                        isSessionStarted = true;
                        needRestartAfterStopped = false;
                        isConnecting = false;
                        mRecorderButton.setBackgroundResource(R.drawable.btn_stop_streaming);
                        mRecorderButton.setPadding(0, 0, 0, 0);
                        statusView.setText("停止直播");
                        mRecorderButton.setEnabled(true);
                        break;
                    /**暂停直播*/
                    case UI_EVENT_RECORDER_STOPPED:
                        Log.i(TAG, "Stopping Streaming succeeded!");
                        serverFailTryingCount = 0;
                        isSessionStarted = false;
                        needRestartAfterStopped = false;
                        isConnecting = false;
                        mRecorderButton.setBackgroundResource(R.drawable.btn_start_streaming);
                        mRecorderButton.setPadding(0, 0, 0, 0);
                        statusView.setText("开始直播");
                        mRecorderButton.setEnabled(true);
                        break;
                    /**会话准备完毕*/
                    case UI_EVENT_SESSION_PREPARED:
                        isSessionReady = true;
                        mLoadingAnimation.setVisibility(View.GONE);
                        mRecorderButton.setEnabled(true);
                        break;
                    case UI_EVENT_HIDE_FOCUS_ICON:
                        mFocusIcon.setVisibility(View.GONE);
                        break;
                    case UI_EVENT_RECONNECT_SERVER:
                        Log.i(TAG, "Reconnecting to server...");
                        if (isSessionReady && mLiveSession != null) {
                            mLiveSession.startRtmpSession(mStreamingUrl);
                        }
                        if (mUIEventHandler != null) {
                            mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_CONNECTING);
                        }
    
                        break;
                    /**
                     * 停止直播（当前会话）
                     */
                    case UI_EVENT_STOP_STREAMING:
                        if (!isConnecting) {
                            Log.i(TAG, "Stopping current session...");
                            if (isSessionReady) {
                                mLiveSession.stopRtmpSession();
                            }
                            mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_STOPPED);
                        }
                        break;
                    case UI_EVENT_RESTART_STREAMING:
                        if (!isConnecting) {
                            Log.i(TAG, "Restarting session...");
                            isConnecting = true;
                            needRestartAfterStopped = true;
                            if (isSessionReady && mLiveSession != null) {
                                mLiveSession.stopRtmpSession();
                            }
                            if (mUIEventHandler != null) {
                                mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_CONNECTING);
                            }
    
                        }
                        break;
                    case UI_EVENT_SHOW_TOAST_MESSAGE:
                        String text = (String) msg.obj;
                        Toast.makeText(StreamingActivity.this, text, Toast.LENGTH_SHORT).show();
                        break;
                    case UI_EVENT_RESIZE_CAMERA_PREVIEW:
                        String hint = "注意：当前摄像头不支持您所选择的分辨率\n实际分辨率为" + mVideoWidth + "x" + mVideoHeight;
                        Toast.makeText(StreamingActivity.this, hint, Toast.LENGTH_LONG).show();
                        fitPreviewToParentByResolution(mCameraView.getHolder(), mVideoWidth, mVideoHeight);
                        break;
                    case TEST_EVENT_SHOW_UPLOAD_BANDWIDTH:
                        if (mLiveSession != null) {
                            Log.d(TAG, "Current upload bandwidth is " + mLiveSession.getCurrentUploadBandwidthKbps()
                                    + " KBps.");
                        }
                        if (mUIEventHandler != null) {
                            mUIEventHandler.sendEmptyMessageDelayed(TEST_EVENT_SHOW_UPLOAD_BANDWIDTH, 2000);
                        }
                        break;
                    default:
                        break;
                }
                super.handleMessage(msg);
            }
        };
    }

    private void initStateListener() {
        mStateListener = new SessionStateListener() {
            /**
             * 录制设备准备完毕
             * @param code 固定为RESULT_CODE_OF_OPERATION_SUCCEEDED
             */
            @Override
            public void onSessionPrepared(int code) {
                if (code == SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED) {
                    if (mUIEventHandler != null) {
                        mUIEventHandler.sendEmptyMessage(UI_EVENT_SESSION_PREPARED);
                    }
                    int realWidth = mLiveSession.getAdaptedVideoWidth();
                    int realHeight = mLiveSession.getAdaptedVideoHeight();
                    if (realHeight != mVideoHeight || realWidth != mVideoWidth) {
                        mVideoHeight = realHeight;
                        mVideoWidth = realWidth;
                        mUIEventHandler.sendEmptyMessage(UI_EVENT_RESIZE_CAMERA_PREVIEW);
                    }
                }
            }

            /**
             * 推流开始后的回调
             * @param code 固定为RESULT_CODE_OF_OPERATION_SUCCEEDED
             */
            @Override
            public void onSessionStarted(int code) {
                if (code == SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED) {
                    if (mUIEventHandler != null) {
                        mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_STARTED);
                    }
                } else {
                    Log.e(TAG, "Starting Streaming failed!");
                }
            }

            /**
             * 推流结束后的回调
             * @param code 固定为RESULT_CODE_OF_OPERATION_SUCCEEDED
             */
            @Override
            public void onSessionStopped(int code) {
                if (code == SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED) {
                    if (mUIEventHandler != null) {
                        if (needRestartAfterStopped && isSessionReady) {
                            mLiveSession.startRtmpSession(mStreamingUrl);
                        } else {
                            mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_STOPPED);
                        }
                    }
                } else {
                    Log.e(TAG, "Stopping Streaming failed!");
                }
            }

            /**
             * 推流 SDK 出错后的回调
             * @param code 错误类型如下：
             *                ERROR_CODE_OF_OPEN_MIC_FAILED
             *                ERROR_CODE_OF_OPEN_CAMERA_FAILED
             *                ERROR_CODE_OF_PREPARE_SESSION_FAILED
             *                ERROR_CODE_OF_CONNECT_TO_SERVER_FAILED
             *                ERROR_CODE_OF_DISCONNECT_FROM_SERVER_FAILED
             *                ERROR_CODE_OF_UNKNOWN_STREAMING_ERROR
             *                ERROR_CODE_OF_WEAK_CONNECTION_ERROR
             *                ERROR_CODE_OF_SERVER_INTERNAL_ERROR
             *                ERROR_CODE_OF_LOCAL_NETWORK_ERROR
             */
            @Override
            public void onSessionError(int code) {
                switch (code) {
                    case SessionStateListener.ERROR_CODE_OF_OPEN_MIC_FAILED:
                        Log.e(TAG, "Error occurred while opening MIC!");
                        onOpenDeviceFailed();
                        break;
                    case SessionStateListener.ERROR_CODE_OF_OPEN_CAMERA_FAILED:
                        Log.e(TAG, "Error occurred while opening Camera!");
                        onOpenDeviceFailed();
                        break;
                    case SessionStateListener.ERROR_CODE_OF_PREPARE_SESSION_FAILED:
                        Log.e(TAG, "Error occurred while preparing recorder!");
                        onPrepareFailed();
                        break;
                    case SessionStateListener.ERROR_CODE_OF_CONNECT_TO_SERVER_FAILED:
                        Log.e(TAG, "Error occurred while connecting to server!");
                        if (mUIEventHandler != null) {
                            serverFailTryingCount++;
                            if (serverFailTryingCount > 5) {
                                Message msg = mUIEventHandler.obtainMessage(UI_EVENT_SHOW_TOAST_MESSAGE);
                                msg.obj = "自动重连服务器失败，请检查网络设置";
                                mUIEventHandler.sendMessage(msg);
                                mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_STOPPED);
                            } else {
                                Message msg = mUIEventHandler.obtainMessage(UI_EVENT_SHOW_TOAST_MESSAGE);
                                msg.obj = "连接推流服务器失败，自动重试5次，当前为第" + serverFailTryingCount + "次";
                                mUIEventHandler.sendMessage(msg);
                                mUIEventHandler.sendEmptyMessageDelayed(UI_EVENT_RECONNECT_SERVER, 2000);
                            }
                            
                        }
                        break;
                    case SessionStateListener.ERROR_CODE_OF_DISCONNECT_FROM_SERVER_FAILED:
                        Log.e(TAG, "Error occurred while disconnecting from server!");
                        isConnecting = false;
                        // Although we can not stop session successfully, we still
                        // need to take it as stopped
                        if (mUIEventHandler != null) {
                            mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_STOPPED);
                        }
                        break;
                    default:
                        onStreamingError(code);
                        break;
                }
            }
        };
    }
    
    int serverFailTryingCount = 0;

    /**
     * 打开硬件设备失败
     */
    private void onOpenDeviceFailed() {
        if (mUIEventHandler != null) {
            Message msg = mUIEventHandler.obtainMessage(UI_EVENT_SHOW_TOAST_MESSAGE);
            msg.obj = "摄像头或MIC打开失败！请确认您已开启相关硬件使用权限！";
            mUIEventHandler.sendMessage(msg);
        }
        // StreamingActivity.this.finish();
    }

    /**
     * 推流会话准备失败
     */
    private void onPrepareFailed() {
        isSessionReady = false;
    }

    int mWeakConnectionHintCount = 0;


    private void onStreamingError(int errno) {
        Message msg = mUIEventHandler.obtainMessage(UI_EVENT_SHOW_TOAST_MESSAGE);
        switch (errno) {
            case SessionStateListener.ERROR_CODE_OF_SERVER_INTERNAL_ERROR:
                msg.obj = "因服务器异常，当前直播已经中断！正在尝试重新推流...";
                if (mUIEventHandler != null) {
                    mUIEventHandler.sendMessage(msg);
                    mUIEventHandler.sendEmptyMessage(UI_EVENT_RESTART_STREAMING);
                }
                break;
            case SessionStateListener.ERROR_CODE_OF_WEAK_CONNECTION_ERROR:
                Log.i(TAG, "Weak connection...");
                msg.obj = "当前网络不稳定，请检查网络信号！";
                mWeakConnectionHintCount++;
                if (mUIEventHandler != null) {
                    mUIEventHandler.sendMessage(msg);
                    if (mWeakConnectionHintCount >= 5) {
                        mWeakConnectionHintCount = 0;
                        mUIEventHandler.sendEmptyMessage(UI_EVENT_RESTART_STREAMING);
                    }
                }
                break;
            case SessionStateListener.ERROR_CODE_OF_LOCAL_NETWORK_ERROR:
                Log.i(TAG, "Timeout when streaming...");
                msg.obj = "本地网络错误，请检查当前网络是否畅通！我们正在努力重连...";
                if (mUIEventHandler != null) {
                    mUIEventHandler.sendMessage(msg);
                    mUIEventHandler.sendEmptyMessage(UI_EVENT_RESTART_STREAMING);
                }
                break;
            default:
                Log.i(TAG, "Unknown error when streaming...");
                msg.obj = "未知错误，当前直播已经中断！正在重试！";
                if (mUIEventHandler != null) {
                    mUIEventHandler.sendMessage(msg);
                    mUIEventHandler.sendEmptyMessageDelayed(UI_EVENT_RESTART_STREAMING, 1000);
                }
                break;
        }
    }

    /**
     * 初始化推流会话
     * @param sh
     */
    private void initRTMPSession(SurfaceHolder sh) {
        int orientation = isOritationLanscape ? LiveConfig.ORIENTATION_LANDSCAPE : LiveConfig.ORIENTATION_PORTRAIT;
        LiveConfig liveConfig = new LiveConfig.Builder().setCameraId(LiveConfig.CAMERA_FACING_FRONT) // 选择摄像头为前置摄像头
                .setCameraOrientation(orientation) // 设置摄像头为竖向
                .setVideoWidth(mVideoWidth) // 设置推流视频宽度, 需传入长的一边
                .setVideoHeight(mVideoHeight) // 设置推流视频高度，需传入短的一边
                .setVideoFPS(mFrameRate) // 设置视频帧率
                .setInitVideoBitrate(mBitrate) // 设置初始视频码率，单位为bit per seconds
                .setAudioBitrate(64 * 1000) // 设置音频码率，单位为bit per seconds
                .setAudioSampleRate(LiveConfig.AUDIO_SAMPLE_RATE_44100) // 设置音频采样率
                .setGopLengthInSeconds(2) // 设置I帧间隔，单位为秒
                .setQosEnabled(true) // 开启码率自适应，默认为true，即默认开启
                .setMinVideoBitrate(200 * 1000) // 码率自适应，最低码率
                .setMaxVideoBitrate(mBitrate) // 码率自适应，最高码率
                .setQosSensitivity(5) // 码率自适应，调整的灵敏度，单位为秒，可接受[5, 10]之间的值
//                .setVideoEnabled(false)
                .build();
        Log.d(TAG, "Calling initRTMPSession..." + liveConfig.toString());
        /**如果sdk版本大于18*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mLiveSession = new LiveSessionHW(this, liveConfig);
        } else {
            mLiveSession = new LiveSessionSW(this, liveConfig);
        }
        /**推流状态的监听*/
        mLiveSession.setStateListener(mStateListener);
        /**会话绑定预览控件*/
        mLiveSession.bindPreviewDisplay(sh);
        /**启动音视频采集设备（即相机和 MIC）*/
        mLiveSession.prepareSessionAsync();
    }

    public void onClickQuit(View v) {
        if (isSessionStarted || isConnecting) {
            Toast.makeText(this, "直播过程中不能返回，请先停止直播！", Toast.LENGTH_SHORT).show();
        } else {
            // 提前让surface自动销毁，防止后续的慢销毁可能带来的兼容问题
            if (mCameraView != null) {
                mCameraView.setVisibility(View.INVISIBLE);
            }
            this.finish();
        }
    }

    /**
     * 切换闪光灯:mLiveSession.toggleFlash(!isFlashOn);
     * @param v
     */
    public void onClickSwitchFlash(View v) {
        /**如果是后摄像头*/
        if (mCurrentCamera == LiveConfig.CAMERA_FACING_BACK) {
            mLiveSession.toggleFlash(!isFlashOn);
            isFlashOn = !isFlashOn;
            if (isFlashOn) {
                mFlashStateButton.setBackgroundResource(R.drawable.btn_flash_on);
            } else {
                mFlashStateButton.setBackgroundResource(R.drawable.btn_flash_off);
            }
        }
    }

    /**
     * 切换摄像头:mLiveSession.switchCamera(mCurrentCamera);
     * @param v
     */
    public void onClickSwitchCamera(View v) {
        if (mLiveSession.canSwitchCamera()) {
            if (mCurrentCamera == LiveConfig.CAMERA_FACING_BACK) {
                mCurrentCamera = LiveConfig.CAMERA_FACING_FRONT;
                mLiveSession.switchCamera(mCurrentCamera);
                if (isFlashOn) {
                    mFlashStateButton.setBackgroundResource(R.drawable.btn_flash_off);
                }
            } else {
                mCurrentCamera = LiveConfig.CAMERA_FACING_BACK;
                mLiveSession.switchCamera(mCurrentCamera);
                if (isFlashOn) {
                    mFlashStateButton.setBackgroundResource(R.drawable.btn_flash_on);
                }
            }
        } else {
            Toast.makeText(this, "抱歉！该分辨率下不支持切换摄像头！", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 切换美图效果：mLiveSession.enableDefaultBeautyEffect
     * @param v
     */
    public void onClickSwitchBeautyEffect(View v) {
        hasBueatyEffect = !hasBueatyEffect;
        mLiveSession.enableDefaultBeautyEffect(hasBueatyEffect);
        mBeautyEffectStateButton
                .setBackgroundResource(hasBueatyEffect ? R.drawable.btn_effect_on : R.drawable.btn_effect_off);
    }

    /**
     * 开始直播（会话）：mLiveSession.startRtmpSession(mStreamingUrl)
     * 停止直播（会话）：mLiveSession.stopRtmpSession()
     * @param v
     */
    public void onClickStreamingButton(View v) {
        /**在调用onSessionPrepared 的时候isSessionReady=true*/
        if (!isSessionReady) {
            return;
        }
        if (!isSessionStarted && !TextUtils.isEmpty(mStreamingUrl)) {
            if (mLiveSession.startRtmpSession(mStreamingUrl)) {
                Log.i(TAG, "Starting Streaming in right state!");
            } else {
                Log.e(TAG, "Starting Streaming in wrong state!");
            }
            mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_CONNECTING);
        } else {
            if (mLiveSession.stopRtmpSession()) {
                Log.i(TAG, "Stopping Streaming in right state!");
            } else {
                Log.e(TAG, "Stopping Streaming in wrong state!");
            }
            mUIEventHandler.sendEmptyMessage(UI_EVENT_RECORDER_CONNECTING);
        }
    }

    @Override
    public void onBackPressed() {
        if (isSessionStarted) {
            Toast.makeText(this, "直播过程中不能返回，请先停止直播！", Toast.LENGTH_SHORT).show();
        } else {
            // 提前让surface自动销毁，防止后续的慢销毁可能带来的兼容问题
            if (mCameraView != null) {
                mCameraView.setVisibility(View.INVISIBLE);
            }
            finish();
        }
    }

    @Override
    public void onStart() {
        Log.i(TAG, "===========> onStart()");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "===========> onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "===========> onDestroy()");
        mUIEventHandler.removeCallbacksAndMessages(null);
        if (isSessionStarted) {
            mLiveSession.stopRtmpSession();
            isSessionStarted = false;
        }
        if (isSessionReady) {
            mLiveSession.destroyRtmpSession();
            mLiveSession = null;
            mStateListener = null;
            mUIEventHandler = null;
            isSessionReady = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        // Be sure to call the superclass implementation
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent arg0) {
    }

    @Override
    public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent arg0) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent arg0) {
        // 双击，触发一次
        if (mLiveSession != null && !mLiveSession.zoomInCamera()) {
            Log.e(TAG, "Zooming camera failed!");
            mLiveSession.cancelZoomCamera();
        }
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent arg0) {
        // 双击，触发两次
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent arg0) {
        if (mLiveSession != null) {
            mLiveSession.focusToPosition((int) arg0.getX(), (int) arg0.getY());
            mFocusIcon.setX(arg0.getX() - mFocusIcon.getWidth() / 2);
            mFocusIcon.setY(arg0.getY() - mFocusIcon.getHeight() / 2);
            mFocusIcon.setVisibility(View.VISIBLE);
            mUIEventHandler.sendEmptyMessageDelayed(UI_EVENT_HIDE_FOCUS_ICON, 1000);
        }
        return true;
    }

    private void fitPreviewToParentByResolution(SurfaceHolder holder, int width, int height) {
        // Adjust the size of SurfaceView dynamically
        int screenHeight = getWindow().getDecorView().getRootView().getHeight();
        int screenWidth = getWindow().getDecorView().getRootView().getWidth();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) { // If
                                                                                                   // portrait,
                                                                                                   // we
                                                                                                   // should
                                                                                                   // swap
                                                                                                   // width
                                                                                                   // and
                                                                                                   // height
            width = width ^ height;
            height = width ^ height;
            width = width ^ height;
        }
        // Fit height
        int adjustedVideoHeight = screenHeight;
        int adjustedVideoWidth = screenWidth;
        if (width * screenHeight > height * screenWidth) { // means width/height
                                                           // >
                                                           // screenWidth/screenHeight
            // Fit width
            adjustedVideoHeight = height * screenWidth / width;
            adjustedVideoWidth = screenWidth;
        } else {
            // Fit height
            adjustedVideoHeight = screenHeight;
            adjustedVideoWidth = width * screenHeight / height;
        }
        holder.setFixedSize(adjustedVideoWidth, adjustedVideoHeight);
    }

}
