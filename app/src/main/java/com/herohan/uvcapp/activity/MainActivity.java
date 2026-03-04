package com.herohan.uvcapp.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.facepass.NativeLib;
import com.google.gson.Gson;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.ImageCapture;
import com.herohan.uvcapp.R;
import com.herohan.uvcapp.VideoCapture;
import com.herohan.uvcapp.fragment.CameraControlsDialogFragment;
import com.herohan.uvcapp.fragment.DeviceListDialogFragment;
import com.herohan.uvcapp.fragment.RecognizePopDialog;
import com.herohan.uvcapp.fragment.VideoFormatDialogFragment;
import com.herohan.uvcapp.utils.CustomFPS;
import com.herohan.uvcapp.utils.SaveHelper;
import com.herohan.uvcapp.utils.ShellUtils;
import com.herohan.uvcapp.utils.UserManager;
import com.hjq.permissions.XXPermissions;
import com.serenegiant.opengl.renderer.MirrorMode;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.utils.UriHelper;
import com.serenegiant.widget.AspectRatioTextureView;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements NativeLib.facePassCallBack {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String TAG_FACE_PASS = "face_pass";
    private static final boolean DEBUG = true;

    private static final int QUARTER_SECOND = 250;
    private static final int HALF_SECOND = 500;
    private static final int ONE_SECOND = 1000;

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    /**
     * Camera preview width
     */
    private int mPreviewWidth = DEFAULT_WIDTH;
    /**
     * Camera preview height
     */
    private int mPreviewHeight = DEFAULT_HEIGHT;

    private int mPreviewRotation = 0;

    private ICameraHelper mCameraHelper;

    private NativeLib mFacePass = new NativeLib();
    private UserManager mUserManager = new UserManager();
    //private NativeLib.facePassCallBack facePassCB;

    private UsbDevice mUsbDevice;
    private final ICameraHelper.StateCallback mStateCallback = new MyCameraHelperCallback();

    private long mRecordStartTime = 0;
    private Timer mRecordTimer = null;
    private DecimalFormat mDecimalFormat;

    private boolean mIsRecording = false;
    private boolean mIsCameraConnected = false;

    private CameraControlsDialogFragment mControlsDialog;
    private DeviceListDialogFragment mDeviceListDialog;
    private VideoFormatDialogFragment mFormatDialog;

    /*face pass*/
    private boolean mIsSeriaOpened = false;
    private boolean mIsFacePassInit = false;
    private int face_cnts = 0;
    private int palm_cnts = 0;
    private int local_face_cnts = 0;
    private int local_palm_cnts = 0;
    private boolean mIsRegister = false;
    private boolean mIsRecog = false;
    private boolean mIsIdle = false;
    private boolean mIsBookLoad = false;
    private boolean mIsModeling = false;
    UserManager.UserData user_data = new UserManager.UserData();
    private byte[] ota_data;

    //识别后弹窗
    RecognizePopDialog mRecognizeDialog;
    // fps
    CustomFPS mFps = new CustomFPS();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));

        mFps.setInterval(1000);
        mFps.addListener(fps -> runOnUiThread(() -> {
            ((TextView) findViewById(R.id.tvFps)).setText("FPS:" + fps);
        }));
        checkCameraHelper();

        setListeners();

        int ret = mUserManager.loadBook("/storage/emulated/0/Documents/Users.dat");
        if (ret < 0) {
            Log.v(TAG_FACE_PASS, "load book failed\n");
            mIsBookLoad = false;
        } else {
            int usercnts = mUserManager.getUserCnts();
            int face_cnts = mUserManager.getUserCnts(0);
            int palm_cnts = mUserManager.getUserCnts(1);

            Log.v(TAG_FACE_PASS, String.format("load book ok face cnts:%d plam cnts:%d total user cnts:%d\n", face_cnts, palm_cnts, usercnts));
            mIsBookLoad = true;
        }
        mRecognizeDialog = new RecognizePopDialog(this);


    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            if (!mIsCameraConnected) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                selectDevice(device);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        initPreviewView();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mIsRecording) {
            toggleVideoRecord(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearCameraHelper();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_control) {
            showCameraControlsDialog();
        } else if (id == R.id.action_device) {
            showDeviceListDialog();
        } else if (id == R.id.action_safely_eject) {
            safelyEject();
        } else if (id == R.id.action_settings) {
        } else if (id == R.id.action_video_format) {
            showVideoFormatDialog();
        } else if (id == R.id.action_rotate_90_CW) {
            rotateBy(90);
        } else if (id == R.id.action_rotate_90_CCW) {
            rotateBy(-90);
        } else if (id == R.id.action_flip_horizontally) {
            flipHorizontally();
        } else if (id == R.id.action_flip_vertically) {
            flipVertically();
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mIsCameraConnected) {
            menu.findItem(R.id.action_control).setVisible(true);
            menu.findItem(R.id.action_safely_eject).setVisible(true);
            menu.findItem(R.id.action_video_format).setVisible(true);
            menu.findItem(R.id.action_rotate_90_CW).setVisible(true);
            menu.findItem(R.id.action_rotate_90_CCW).setVisible(true);
            menu.findItem(R.id.action_flip_horizontally).setVisible(true);
            menu.findItem(R.id.action_flip_vertically).setVisible(true);
        } else {
            menu.findItem(R.id.action_control).setVisible(false);
            menu.findItem(R.id.action_safely_eject).setVisible(false);
            menu.findItem(R.id.action_video_format).setVisible(false);
            menu.findItem(R.id.action_rotate_90_CW).setVisible(false);
            menu.findItem(R.id.action_rotate_90_CCW).setVisible(false);
            menu.findItem(R.id.action_flip_horizontally).setVisible(false);
            menu.findItem(R.id.action_flip_vertically).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    AtomicBoolean isInit = new AtomicBoolean(false);

    /*face pass interface*/
    private void initFacePass() {
        int ret = 0;
        String port_name;
        port_name = mFacePass.getACMPortName();
        Log.d("facepass", "begin init" + " port_name:" + port_name);
        if (port_name.isEmpty()) {
            port_name = "/dev/ttyACM0";
        }

        String shell_cmd = "chmod 777 " + port_name;
        ShellUtils.exeCmd(shell_cmd);
        Log.v(TAG_FACE_PASS, "open port:" + port_name);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mFacePass.setFacePassCallBack(this);

        {
            ret = mFacePass.serialIint(port_name);
            if (ret < 0) {
                Log.v(TAG_FACE_PASS, "serialIint " + port_name + " failed");
                mIsSeriaOpened = false;
            } else {
                Log.v(TAG_FACE_PASS, "serialIint " + port_name + " ok");
                ret = mFacePass.facePassInit();
                if (ret < 0) {
                    Log.v(TAG_FACE_PASS, "facePassInit init failed ret:" + ret);
                } else {
                    mFacePass.CreateGroup(0);
                    mFacePass.CreateGroup(1);
                    mFacePass.SetLivessConfig(1, 90, 4);
                    mFacePass.SetRecogConfig(90, 56, 7, 3);

                    face_cnts = mFacePass.facepassGetFaceUserCnts();
                    palm_cnts = mFacePass.facepassGetPlamUserCnts();
                    local_face_cnts = mUserManager.getUserCnts(0);
                    local_palm_cnts = mUserManager.getUserCnts(1);
                    Log.v(TAG_FACE_PASS, String.format("face cnt:%d-%d  palm cnt:%d-%d\n", face_cnts, local_face_cnts, palm_cnts, local_palm_cnts));
                    if (face_cnts != local_face_cnts || palm_cnts != local_palm_cnts) {
                        Log.v(TAG_FACE_PASS, "soft_ver " + mFacePass.getVersion() + " feature id:" + mFacePass.getFeatureVersion());

                        /*modeling*/
                        local_face_cnts = mUserManager.getUserCnts(0);
                        local_palm_cnts = mUserManager.getUserCnts(1);

                        if (mUserManager.getUserCnts() > 0) {
                            Log.v(TAG_FACE_PASS, "start modeling user cnts:" + mUserManager.getUserCnts());
                            for (int i = 1; i <= mUserManager.getUserCnts(); i++) {
                                user_data = mUserManager.getUserInfo(i);
                                Log.v(TAG_FACE_PASS, String.format("face_id:%d type:%d ft:%d [%d-%d-%d]", i, user_data.type, user_data.ft.length, user_data.ft[0], user_data.ft[1], user_data.ft[2]));
                                if (user_data.type == 0 || user_data.type == 1) {
                                    mFacePass.InsertFeature(user_data.type, i, user_data.ft);
                                }
                            }
                        }
                        face_cnts = mFacePass.facepassGetFaceUserCnts();
                        palm_cnts = mFacePass.facepassGetPlamUserCnts();
                        Log.v(TAG_FACE_PASS, "modeling fin remote face_cnts:" + face_cnts + " palm_cnts:" + palm_cnts);
                        mIsModeling = true;
                    } else {
                        mIsModeling = true;
                    }
                }
                mIsSeriaOpened = true;
                mIsIdle = true;
            }
        }
    }


    private void deInitFacepass() {
        Log.v(TAG_FACE_PASS, "deInitFacepass................");
        if (mFacePass == null) {
            return;
        }
        mFacePass.facepassDeInit();
        mIsSeriaOpened = false;
    }

    private void setListeners() {
        findViewById(R.id.button_recog).setOnClickListener(v -> {
            if (mIsModeling) {
                mFacePass.StartRecog();
                mIsRecog = true;
                mIsIdle = false;
                mIsRegister = false;
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        Toast.makeText(getApplicationContext(), "ACM通信异常", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        findViewById(R.id.button_pause).setOnClickListener(v -> {
            mFacePass.PauseRecog();
            mIsRecog = false;
            mIsIdle = true;
            mIsRegister = false;
        });

        findViewById(R.id.ota).setOnClickListener(v -> {
            mFacePass.PauseRecog();
            mIsRecog = false;
            mIsIdle = true;
            mIsRegister = false;
            XXPermissions.with(this).permission(Manifest.permission.MANAGE_EXTERNAL_STORAGE).request((permissions, all) -> {
                ota_data = mUserManager.loadUpgradeFile("/storage/emulated/0/Documents/Upgrade.bin");

                if (ota_data.length > 0) {
                    int ret = 0;
                    ret = mFacePass.initOta();
                    if (ret < 0) {
                        Log.v(TAG, "ota init failed\n");
                    } else {
                        ret = mFacePass.sendOtaData(ota_data);
                        if (ret == ota_data.length) {
                            Log.v(TAG, "start ota\n");
                            mFacePass.startOta();
                        }
                    }
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 在主线程执行
                            Toast.makeText(getApplicationContext(), "加载升级文件异常", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });

        findViewById(R.id.button_face).setOnClickListener(v -> {
//            开始录入
            mFacePass.PauseRecog();
            int faceid = mUserManager.getLastId();
            if (faceid == 0) {
                Log.v(TAG, "invalid face_id current user cnts:" + mUserManager.getUserCnts());
            } else {
                int ret = mFacePass.StartRegister(faceid, 0, 15000);
                Log.v(TAG, "start register face:" + faceid + " ret:" + ret);
                mIsRecog = false;
                mIsIdle = false;
                mIsRegister = true;
            }
        });

        findViewById(R.id.button_palm).setOnClickListener(v -> {
            mFacePass.PauseRecog();
            int faceid = mUserManager.getLastId();
            if (faceid == 0) {
                Log.v(TAG, "invalid face_id current user cnts:" + mUserManager.getUserCnts());
            } else {
                int ret = mFacePass.StartRegister(faceid, 1, 15000);
                Log.v(TAG, "start register palm:" + faceid + " ret:" + ret);
                mIsRecog = false;
                mIsIdle = false;
                mIsRegister = true;
            }
        });

        findViewById(R.id.button_del).setOnClickListener(v -> {
            int ret = mFacePass.RemoveAllUsers();
            if (ret < 0) {
                Log.v(TAG_FACE_PASS, "delet remote user failed");
            } else {
                mUserManager.removeAllUser();
            }
        });

    }

    private void showCameraControlsDialog() {
        if (mControlsDialog == null) {
            mControlsDialog = new CameraControlsDialogFragment(mCameraHelper);
        }
        // When DialogFragment is not showing
        if (!mControlsDialog.isAdded()) {
            mControlsDialog.show(getSupportFragmentManager(), "camera_controls");
        }
    }

    private void showDeviceListDialog() {
        if (mDeviceListDialog != null && mDeviceListDialog.isAdded()) {
            return;
        }

        mDeviceListDialog = new DeviceListDialogFragment(mCameraHelper, mIsCameraConnected ? mUsbDevice : null);
        mDeviceListDialog.setOnDeviceItemSelectListener(usbDevice -> {
            if (mIsCameraConnected) {
                mCameraHelper.closeCamera();
            }
            selectDevice(mUsbDevice);
        });

        mDeviceListDialog.show(getSupportFragmentManager(), "device_list");
    }

    private void showVideoFormatDialog() {
        if (mFormatDialog != null && mFormatDialog.isAdded()) {
            return;
        }

        mFormatDialog = new VideoFormatDialogFragment(mCameraHelper.getSupportedFormatList(), mCameraHelper.getPreviewSize());
        mFormatDialog.setOnVideoFormatSelectListener(size -> {
            if (mIsCameraConnected && !mCameraHelper.isRecording()) {
                mCameraHelper.stopPreview();
                mCameraHelper.setPreviewSize(size);
                mCameraHelper.startPreview();
                resizePreviewView(size);
                // save selected preview size
                setSavedPreviewSize(size);
            }
        });

        mFormatDialog.show(getSupportFragmentManager(), "video_format");
    }

    private void closeAllDialogFragment() {
        if (mControlsDialog != null && mControlsDialog.isAdded()) {
            mControlsDialog.dismiss();
        }
        if (mDeviceListDialog != null && mDeviceListDialog.isAdded()) {
            mDeviceListDialog.dismiss();
        }
        if (mFormatDialog != null && mFormatDialog.isAdded()) {
            mFormatDialog.dismiss();
        }
    }

    private void safelyEject() {
        if (mCameraHelper != null) {
            mCameraHelper.closeCamera();
        }
    }

    private void rotateBy(int angle) {
        mPreviewRotation += angle;
        mPreviewRotation %= 360;
        if (mPreviewRotation < 0) {
            mPreviewRotation += 360;
        }

        if (mCameraHelper != null) {
            mCameraHelper.setPreviewConfig(mCameraHelper.getPreviewConfig().setRotation(mPreviewRotation));
        }
    }

    private void flipHorizontally() {
        if (mCameraHelper != null) {
            mCameraHelper.setPreviewConfig(mCameraHelper.getPreviewConfig().setMirror(MirrorMode.MIRROR_HORIZONTAL));
        }
    }

    private void flipVertically() {
        if (mCameraHelper != null) {
            mCameraHelper.setPreviewConfig(mCameraHelper.getPreviewConfig().setMirror(MirrorMode.MIRROR_VERTICAL));
        }
    }

    private void checkCameraHelper() {
        if (!mIsCameraConnected) {
            clearCameraHelper();
        }
        initCameraHelper();
    }

    private void initCameraHelper() {
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateCallback);
            mCameraHelper.setFPSMonitor(mFps);
        }
    }

    private void clearCameraHelper() {
        if (DEBUG) Log.v(TAG, "clearCameraHelper:");
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void initPreviewView() {
        AspectRatioTextureView viewMainPreview = findViewById(R.id.viewMainPreview);
        viewMainPreview.setAspectRatio(mPreviewWidth, mPreviewHeight);
        viewMainPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(surface, false);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(surface);
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });

        findViewById(R.id.btn_sync).setOnClickListener(v -> doSync());
    }

    /**
     * 同步用户特征数据到当前设备
     * 从 tvFt 输入框读取 Base64 编码的特征值，解码后插入到模组
     * 用于跨设备同步：A 设备录入 → 复制 Base64 特征值 → B 设备粘贴 → 同步
     */
    private void doSync() {
        // 获取 face_id
        String faceIdStr = ((androidx.appcompat.widget.AppCompatEditText) findViewById(R.id.tv_faceid)).getText().toString();
        if (TextUtils.isEmpty(faceIdStr)) {
            Toast.makeText(this, "请输入 face_id", Toast.LENGTH_SHORT).show();
            return;
        }
        int faceId = Integer.parseInt(faceIdStr);

        // 获取 Base64 编码的特征值
        String base64Ft = ((androidx.appcompat.widget.AppCompatEditText) findViewById(R.id.tv_ft)).getText().toString().trim();
        if (TextUtils.isEmpty(base64Ft)) {
            Toast.makeText(this, "请输入特征值（Base64 格式）", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "doSync: empty feature string");
            return;
        }

        // 解码 Base64 获取原始字节数组
        byte[] featureBytes;
        try {
            featureBytes = Base64.decode(base64Ft, Base64.DEFAULT);
            Log.i(TAG, "doSync: Base64 decoded, ft length=" + featureBytes.length);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "特征值格式错误（必须是 Base64）", Toast.LENGTH_LONG).show();
            Log.e(TAG, "doSync: Base64 decode failed", e);
            return;
        }

        // 检查解码后的数据是否有效
        if (featureBytes == null || featureBytes.length == 0) {
            Toast.makeText(this, "特征数据无效", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "doSync: invalid decoded data, faceId=" + faceId);
            return;
        }

        int dataType = 0; // 0=人脸组, 1=掌纹组
        int insertRes = mFacePass.InsertFeature(dataType, faceId, featureBytes);

        if (insertRes >= 0) {
            Toast.makeText(this, "同步成功 faceId=" + faceId, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "doSync success: faceId=" + faceId + ", result=" + insertRes);
        } else {
            Toast.makeText(this, "同步失败=" + insertRes, Toast.LENGTH_LONG).show();
            Log.e(TAG, "doSync failed: faceId=" + faceId + ", result=" + insertRes);
        }
    }

    public void attachNewDevice(UsbDevice device) {
        if (mUsbDevice == null) {
            mUsbDevice = device;

            selectDevice(device);
        }
    }

    /**
     * In Android9+, connected to the UVC CAMERA, CAMERA permission is required
     *
     * @param device
     */
    protected void selectDevice(UsbDevice device) {

//        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
//        for (StackTraceElement stackTraceElement : stackTrace) {
//            Log.i(TAG,"method:" +stackTraceElement.getMethodName() + ",vId:" + device.getVendorId() + ",pId:" + device.getProductId());
//        }

        if (!((CameraHelper) mCameraHelper).isUVCCamera(device)) {
            if (device == null) {
                Log.e(TAG, "selectDevice fail device is null");
                return;
            }
            Log.e(TAG, "selectDevice fail device=" + device.getDeviceName() + ",vId:" + device.getVendorId() + ",pId:" + device.getProductId() + ",ProductName:" + device.getProductName());
            return;
        }
        if (DEBUG)
            Log.v(TAG, "selectDevice:device=" + device.getDeviceName() + ",vId:" + device.getVendorId() + ",pId:" + device.getProductId() + ",ProductName:" + device.getProductName());
        mUsbDevice = device;
        XXPermissions.with(this).permission(Manifest.permission.CAMERA).request((permissions, all) -> {
            mIsCameraConnected = false;
            updateUIControls();

            if (mCameraHelper != null) {
                // 通过UsbDevice对象，尝试获取设备权限
                mCameraHelper.selectDevice(device);
            }
        });
    }

    /*face pass*/
    @Override
    public void OnRecogMsg(int ppl_code, int obj_type, int stranger, int top1_id, int iden_score, byte[] ft, byte[] img) {
        if (mIsRecog == false) {
            return;
        }
        Log.v(TAG, "ppl_code:=" + ppl_code + " face_id:" + top1_id);

        user_data = mUserManager.getUserInfo(top1_id);
        Log.v(TAG, "ppl_code:=" + ppl_code + " face_id:" + top1_id + ",name:" + user_data.name);
        if (ppl_code == 96) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 在主线程执行
                    // Toast.makeText(getApplicationContext(), String.format("%s 比对成功",user_data.name), Toast.LENGTH_SHORT).show();
                    mRecognizeDialog.setType(RecognizePopDialog.BG_BLUE | RecognizePopDialog.TITLE_PASS | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{String.format("%s 比对成功", user_data.name)}).show(findViewById(R.id.rl_container));
                }
            });
        } else {
            if (ppl_code == 92) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_RED | RecognizePopDialog.TITLE_NO_PERMISSION | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{"算法未授权"}).show(findViewById(R.id.rl_container));
                    }
                });
            }
            if (ppl_code == 93 || ppl_code == 94) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_YELLOW | RecognizePopDialog.TITLE_NO_LIVES | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{"图像异常"}).show(findViewById(R.id.rl_container));
                    }
                });
            }

            if (ppl_code == 33) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_YELLOW | RecognizePopDialog.TITLE_NO_LIVES | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{"图像过曝"}).show(findViewById(R.id.rl_container));
                    }
                });
            }
            if (ppl_code == 51) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_YELLOW | RecognizePopDialog.TITLE_NO_LIVES | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{"活体攻击"}).show(findViewById(R.id.rl_container));
                    }
                });
            }
            if (ppl_code == 71) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRecognizeDialog.setType(RecognizePopDialog.BG_YELLOW | RecognizePopDialog.TITLE_NO_AUTH_FAIL | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{"比对失败! score:" + iden_score}).show(findViewById(R.id.rl_container));
                    }
                });
            }
            if (ppl_code >= 1 && ppl_code <= 6) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_YELLOW | RecognizePopDialog.TITLE_NO_AUTH_FAIL | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{"请调整位置"}).show(findViewById(R.id.rl_container));
                    }
                });
            }

            if (ppl_code >= 7 && ppl_code <= 8) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_YELLOW | RecognizePopDialog.TITLE_NO_AUTH_FAIL | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{"请调整姿势"}).show(findViewById(R.id.rl_container));
                    }
                });
            }
        }
    }


    @Override
    public void OnRegisterMsg(int ppl_code, int obj_type, int face_id, int time_out, byte[] ft, byte[] rgb_img, byte[] ir_img) {
        if (mIsRegister == false) {
            return;
        }
        Log.v(TAG, "ppl_code:" + ppl_code);
        if (ppl_code == 96) {
            int usercnts = mFacePass.facepassGetFaceUserCnts();
            Log.v(TAG, "current user_cnts:" + usercnts);
            int ret = mUserManager.addUser(face_id, obj_type, String.format("%d_%s", face_id, obj_type == 0 ? "face" : "palm"), ft);
            if (ret < 0) {
                Log.i(TAG, "NOT saveBook: face_id:" + face_id + " obj_type:" + obj_type + " ret:" + ret);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_BLUE | RecognizePopDialog.TITLE_PASS | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{String.format("底库保存异常")}).show(findViewById(R.id.rl_container));
                    }
                });
            } else {
                Log.i(TAG, "saveBook: face_id:" + face_id + " obj_type:" + obj_type + " ret:" + ret);
                mUserManager.saveBook("/storage/emulated/0/Documents/Users.dat");

                // 将特征值转换为 Base64 字符串，方便跨设备复制
                String base64Ft = Base64.encodeToString(ft, Base64.NO_WRAP);
                Log.i(TAG, "特征值 Base64: " + base64Ft);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_BLUE | RecognizePopDialog.TITLE_PASS | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{String.format("录入成功\n特征值已复制到剪贴板")}).show(findViewById(R.id.rl_container));

                        // 将 Base64 特征值显示到输入框，方便复制
                        ((androidx.appcompat.widget.AppCompatEditText) findViewById(R.id.tv_ft)).setText(base64Ft);

                        // 同时复制到剪贴板
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("face_feature", base64Ft);
                        clipboard.setPrimaryClip(clip);
                    }
                });
            }
        } else {
            if (ppl_code >= 1 && ppl_code <= 6) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_BLUE | RecognizePopDialog.TITLE_PASS | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{String.format("请调整姿势")}).show(findViewById(R.id.rl_container));

                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        mRecognizeDialog.setType(RecognizePopDialog.BG_BLUE | RecognizePopDialog.TITLE_PASS | RecognizePopDialog.BODY_SHOW_TEXT).setMessage(new String[]{String.format("录入失败")}).show(findViewById(R.id.rl_container));

                    }
                });
            }
        }
    }

    public void OnOtaMsg(int ppl_code, int process, int err_info) {
        if (ppl_code == 120) {
            if (process == 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        Toast a = Toast.makeText(getApplicationContext(), "开始升级", Toast.LENGTH_SHORT);
                        a.show();

                    }
                });
            } else if (process == 100) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行
                        Toast a = Toast.makeText(getApplicationContext(), "升级完成", Toast.LENGTH_SHORT);
                        a.show();

                    }
                });
            }
        }
    }

    private class MyCameraHelperCallback implements ICameraHelper.StateCallback {
        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach:device=" + device.getDeviceName());

            attachNewDevice(device);
        }

        /**
         * After obtaining USB device permissions, connect the USB camera
         */
        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:device=" + device.getDeviceName());

            mCameraHelper.openCamera(getSavedPreviewSize());

            mCameraHelper.setButtonCallback(new IButtonCallback() {
                @Override
                public void onButton(int button, int state) {
                    Toast.makeText(MainActivity.this, "onButton(button=" + button + "; " + "state=" + state + ")", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:device=" + device.getDeviceName());
            mCameraHelper.startPreview();

            // After connecting to the camera, you can get preview size of the camera
            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                resizePreviewView(size);
            }

            AspectRatioTextureView viewMainPreview = findViewById(R.id.viewMainPreview);
            if (viewMainPreview.getSurfaceTexture() != null) {
                mCameraHelper.addSurface(viewMainPreview.getSurfaceTexture(), false);
            }

            mIsCameraConnected = true;
            updateUIControls();

            Log.v(TAG_FACE_PASS, "initFacePass mIsSeriaOpened:" + mIsSeriaOpened);
            if (mIsSeriaOpened == false) {
                initFacePass();

                if (mIsRecog) {
                    mFacePass.StartRecog();
                }
            }
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:device=" + device.getDeviceName());

            if (mIsRecording) {
                toggleVideoRecord(false);
            }

            AspectRatioTextureView viewMainPreview = findViewById(R.id.viewMainPreview);
            if (mCameraHelper != null && viewMainPreview.getSurfaceTexture() != null) {
                mCameraHelper.removeSurface(viewMainPreview.getSurfaceTexture());
            }

            mIsCameraConnected = false;
            mIsSeriaOpened = false;
            updateUIControls();

            closeAllDialogFragment();
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:device=" + device.getDeviceName());
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:device=" + device.getDeviceName());

            if (device.equals(mUsbDevice)) {
                mUsbDevice = null;
            }

            deInitFacepass();
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel:device=" + device.getDeviceName());

            if (device.equals(mUsbDevice)) {
                mUsbDevice = null;
            }
        }
    }

    private void resizePreviewView(Size size) {
        // Update the preview size
        mPreviewWidth = size.width;
        mPreviewHeight = size.height;
        // Set the aspect ratio of TextureView to match the aspect ratio of the camera
        ((AspectRatioTextureView) findViewById(R.id.viewMainPreview)).setAspectRatio(mPreviewWidth, mPreviewHeight);
    }

    private void updateUIControls() {
        runOnUiThread(() -> {
            if (mIsCameraConnected) {
                findViewById(R.id.viewMainPreview).setVisibility(View.VISIBLE);
                findViewById(R.id.tvConnectUSBCameraTip).setVisibility(View.GONE);

                findViewById(R.id.fabPicture).setVisibility(View.GONE);
                findViewById(R.id.fabVideo).setVisibility(View.GONE);

                // Update record button
                int colorId = R.color.WHITE;
                if (mIsRecording) {
                    colorId = R.color.RED;
                }
                ColorStateList colorStateList = ColorStateList.valueOf(getResources().getColor(colorId));
                ((com.google.android.material.floatingactionbutton.FloatingActionButton) findViewById(R.id.fabVideo)).setSupportImageTintList(colorStateList);

            } else {
                findViewById(R.id.viewMainPreview).setVisibility(View.GONE);
                findViewById(R.id.tvConnectUSBCameraTip).setVisibility(View.VISIBLE);

                findViewById(R.id.fabPicture).setVisibility(View.GONE);
                findViewById(R.id.fabVideo).setVisibility(View.GONE);

                findViewById(R.id.tvVideoRecordTime).setVisibility(View.GONE);
            }
            invalidateOptionsMenu();
        });
    }

    private Size getSavedPreviewSize() {
        String key = getString(R.string.saved_preview_size) + USBMonitor.getProductKey(mUsbDevice);
        String sizeStr = getPreferences(MODE_PRIVATE).getString(key, null);
        if (TextUtils.isEmpty(sizeStr)) {
            return null;
        }
        Gson gson = new Gson();
        return gson.fromJson(sizeStr, Size.class);
    }

    private void setSavedPreviewSize(Size size) {
        String key = getString(R.string.saved_preview_size) + USBMonitor.getProductKey(mUsbDevice);
        Gson gson = new Gson();
        String json = gson.toJson(size);
        getPreferences(MODE_PRIVATE).edit().putString(key, json).apply();
    }

    private void setCustomImageCaptureConfig() {
//        mCameraHelper.setImageCaptureConfig(
//                mCameraHelper.getImageCaptureConfig().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY));
        mCameraHelper.setImageCaptureConfig(mCameraHelper.getImageCaptureConfig().setJpegCompressionQuality(90));
    }

    public void takePicture() {
        if (mIsRecording) {
            return;
        }

        try {
            File file = new File(SaveHelper.getSavePhotoPath());
            ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(file).build();
            mCameraHelper.takePicture(options, new ImageCapture.OnImageCaptureCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    Toast.makeText(MainActivity.this, "save \"" + UriHelper.getPath(MainActivity.this, outputFileResults.getSavedUri()) + "\"", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    public void toggleVideoRecord(boolean isRecording) {
        try {
            if (isRecording) {
                if (mIsCameraConnected && mCameraHelper != null && !mCameraHelper.isRecording()) {
                    startRecord();
                }
            } else {
                if (mIsCameraConnected && mCameraHelper != null && mCameraHelper.isRecording()) {
                    stopRecord();
                }

                stopRecordTimer();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            stopRecordTimer();
        }

        mIsRecording = isRecording;

        updateUIControls();
    }

    private void setCustomVideoCaptureConfig() {
        mCameraHelper.setVideoCaptureConfig(mCameraHelper.getVideoCaptureConfig()
//                        .setAudioCaptureEnable(false)
                .setBitRate((int) (1024 * 1024 * 25 * 0.25)).setVideoFrameRate(25).setIFrameInterval(1));
    }

    private void startRecord() {
        File file = new File(SaveHelper.getSaveVideoPath());
        VideoCapture.OutputFileOptions options = new VideoCapture.OutputFileOptions.Builder(file).build();
        mCameraHelper.startRecording(options, new VideoCapture.OnVideoCaptureCallback() {
            @Override
            public void onStart() {
                startRecordTimer();
            }

            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                toggleVideoRecord(false);

                Toast.makeText(MainActivity.this, "save \"" + UriHelper.getPath(MainActivity.this, outputFileResults.getSavedUri()) + "\"", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                toggleVideoRecord(false);

                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void stopRecord() {
        mCameraHelper.stopRecording();
    }

    private void startRecordTimer() {
        runOnUiThread(() -> findViewById(R.id.tvVideoRecordTime).setVisibility(View.VISIBLE));

        // Set “00:00:00” to record time TextView
        setVideoRecordTimeText(formatTime(0));

        // Start Record Timer
        mRecordStartTime = SystemClock.elapsedRealtime();
        mRecordTimer = new Timer();
        //The timer is refreshed every quarter second
        mRecordTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long recordTime = (SystemClock.elapsedRealtime() - mRecordStartTime) / 1000;
                if (recordTime > 0) {
                    setVideoRecordTimeText(formatTime(recordTime));
                }
            }
        }, QUARTER_SECOND, QUARTER_SECOND);
    }

    private void stopRecordTimer() {
        runOnUiThread(() -> findViewById(R.id.tvVideoRecordTime).setVisibility(View.GONE));

        // Stop Record Timer
        mRecordStartTime = 0;
        if (mRecordTimer != null) {
            mRecordTimer.cancel();
            mRecordTimer = null;
        }
        // Set “00:00:00” to record time TextView
        setVideoRecordTimeText(formatTime(0));
    }

    private void setVideoRecordTimeText(String timeText) {
        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.tvVideoRecordTime)).setText(timeText);
        });
    }

    /**
     * 将秒转化为 HH:mm:ss 的格式
     *
     * @param time 秒
     * @return
     */
    private String formatTime(long time) {
        if (mDecimalFormat == null) {
            mDecimalFormat = new DecimalFormat("00");
        }
        String hh = mDecimalFormat.format(time / 3600);
        String mm = mDecimalFormat.format(time % 3600 / 60);
        String ss = mDecimalFormat.format(time % 60);
        return hh + ":" + mm + ":" + ss;
    }
}