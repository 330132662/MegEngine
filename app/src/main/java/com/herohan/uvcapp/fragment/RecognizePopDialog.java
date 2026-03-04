package com.herohan.uvcapp.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.herohan.uvcapp.R;
import java.io.File;
import java.util.Arrays;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by  Lei on 2021/1/29.
 * example :
 * single:
 * dialog.setMode(RecognizePopDialog.MODE_ONE);
 * dialog.setType(RecognizePopDialog.BG_YELLOW | RecognizePopDialog.BODY_SHOW_TEXT | RecognizePopDialog.TITLE_NO_LIVES )
 * .setMessage(new String[]{"检测目标非活体"})
 * .show(mContainer);
 * more:
 * dialog.setMode(RecognizePopDialog.MODE_MORE);
 * dialog.multiShow(mContainer,new RecognizePerson());
 */

public class RecognizePopDialog extends ConstraintLayout {

    private static final String TAG = "RECOGNIZE_DIALOG";

    public static final int MODE_ONE = 0;
    public static final int MODE_MORE = 1;
    public static final int MODE_MULTI = 2;

    public static final int BOTTOM_CLICK_TYPE_BELL = 0;
    public static final int BOTTOM_CLICK_TYPE_QR = 1;


    @IntDef({MODE_ONE, MODE_MORE, MODE_MULTI})
    public @interface Mode {
    }

    public int mode = MODE_ONE;

    /**
     * =======================================bg
     */
    public static final int BG_RED = 1;
    public static final int BG_BLUE = 1 << 1;
    public static final int BG_YELLOW = 1 << 2;
    /**
     * =======================================title
     */
    // 检测目标非活体
    public static final int TITLE_NO_LIVES = 1 << 3;
    // 无权限请联系管理员
    public static final int TITLE_NO_PERMISSION = 1 << 4;
    // 认证失败
    public static final int TITLE_NO_AUTH_FAIL = 1 << 5;
    // 通过
    public static final int TITLE_PASS = 1 << 6;
    //不在通行时间内
    public static final int TITLE_TIME_OUT = 1 << 20;

    /**
     * =======================================body内容区域
     */
    // 只显示文本
    public static final int BODY_SHOW_TEXT = 1 << 10;



    // private int mCurrentType = -1;
    protected int mType = -1;
    protected int needChange = 0;


    protected String[] messages;


    protected ImageView mPopBg;
    protected LinearLayout mllMessageContainer;
    protected ConstraintLayout mOneContainer;


    protected ImageView mTitleImg;

    protected ImageView mImgClose;


    protected Handler mHandler;

    private final Runnable autoCloseRunnable = this::hide;

    private Supplier<Boolean> onCloseClickCallBack;
    private Supplier<Boolean> onHideDialogCallBack;
    private long preClickTime;

    protected int currentHolidayIndex = -1;


    public RecognizePopDialog(@NonNull Context context) {
        super(context);
        init();
    }

    public RecognizePopDialog(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecognizePopDialog(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mHandler = new Handler(Looper.getMainLooper());
        LayoutInflater.from(getContext()).inflate(R.layout.lib_ui_recognize_pop_layout, this, true);
        mOneContainer = findViewById(R.id.constraint_single);

        mPopBg = findViewById(R.id.pop_bg);
        mTitleImg = findViewById(R.id.img_title);
        mllMessageContainer = findViewById(R.id.ll_message_container);
        mImgClose = findViewById(R.id.img_close);

        initListener();

    }

    @SuppressLint("ClickableViewAccessibility")
    private void initListener() {

        mImgClose.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - preClickTime < 2000) {
                return;
            }
            preClickTime = clickTime;
            if (onCloseClickCallBack != null) {
                final boolean consume = onCloseClickCallBack.get();
                if (consume) {
                    return;
                }
            }

        });
     
    }



    public RecognizePopDialog setOnCloseClickCallBack(Supplier<Boolean> onCloseClickCallBack) {
        this.onCloseClickCallBack = onCloseClickCallBack;
        return this;
    }

    /**
     * 设置弹窗消失回调
     *
     * @param onHideDialogCallBack
     * @return
     */
    public RecognizePopDialog setDialogHideCallBack(Supplier<Boolean> onHideDialogCallBack) {
        this.onHideDialogCallBack = onHideDialogCallBack;
        return this;
    }


    public RecognizePopDialog setType(int type) {
        if (mType != type) {
            needChange = 1;
        }
        this.mType = type;
        return this;
    }

    public RecognizePopDialog setMessage(String[] message) {
        if (message == null) {
            Log.d(TAG, "updateRecognizeMessage message == null");
            return this;
        }
        if (this.messages == null || !eq(this.messages, message)) {
            needChange = 2;
        }
        this.messages = message;
        Log.d(TAG, "updateRecognizeMessage message = " + Arrays.toString(this.messages));
        return this;
    }



    public void show(ViewGroup parent) {
        show(parent, 2000);
    }

    public void show(ViewGroup parent, long dismissDelay) {
        Log.d(TAG, "call [show] dismissDelay = " + dismissDelay + " , mode = " + mode);
        if (mType == -1) {
            throw new RuntimeException(" call setType first ");
        }

        if (this.getVisibility() != VISIBLE) {
            this.setVisibility(VISIBLE);
        }
        mHandler.removeCallbacksAndMessages(null);
        if (this.getParent() == null) {
            Log.d(TAG, "show [getParent] parent = " + parent + "childCount = " + parent.getChildCount());
            parent.addView(this, parent.getChildCount() - 1);
            matchWh(parent);
        }
        Log.d(TAG, "show [needChange] = " + needChange);
        if (needParse()) {
            hideBody();
            parseBgType();
            parseTitleType();
            parseBodyType();
        }
        mHandler.postDelayed(autoCloseRunnable, dismissDelay);

    }



    public boolean isShow() {
        return getParent() != null;
    }


    private void matchWh(ViewGroup parent) {
        ViewGroup.LayoutParams parentLayoutParams = parent.getLayoutParams();
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.width = parentLayoutParams.width;
        layoutParams.height = parentLayoutParams.height;
        Log.d(TAG, "w : " + layoutParams.width + " h : " + layoutParams.height);
    }

    private void changeMode() {
        switch (mode) {
            case MODE_ONE:
                mOneContainer.setVisibility(VISIBLE);
                break;
        }
    }




    protected void parseBodyType() {
        if ((mType & BODY_SHOW_TEXT) == BODY_SHOW_TEXT) {
            showTextBody();
        }else {
            Log.d("updateRecognizeMessage", "UNKNOW BODY");
        }
    }


    protected void showTextBody() {
        if (messages == null) {
            Log.d("updateRecognizeMessage", "messages == null");
            return;
        }
        for (int i = 0; i < messages.length; i++) {
            View child = mllMessageContainer.getChildAt(i);
            if (child instanceof TextView) {
                child.setVisibility(VISIBLE);
                ((TextView) child).setText(messages[i]);
            }
            Log.d("updateRecognizeMessage", "messages " + messages[i]);
        }
    }


    protected void parseTitleType() {
        if ((mType & TITLE_NO_AUTH_FAIL) == TITLE_NO_AUTH_FAIL) {
            mTitleImg.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.lib_ui_pop_title_no_auth));
        } else if ((mType & TITLE_NO_LIVES) == TITLE_NO_LIVES) {
            mTitleImg.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.lib_ui_pop_title_no_live));
        }else if ((mType & TITLE_NO_PERMISSION) == TITLE_NO_PERMISSION) {
            mTitleImg.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.lib_ui_pop_title_no_permission));
        } else if ((mType & TITLE_PASS) == TITLE_PASS) {
            mTitleImg.setImageResource(R.drawable.lib_ui_pop_title_pass);
        }  else {
            Log.i(TAG, " unknown title");
        }
    }


    protected void parseBgType() {
        if ((mType & BG_RED) == BG_RED) {
            mPopBg.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.lib_ui_pop_bg_red1));
        } else if ((mType & BG_YELLOW) == BG_YELLOW) {
            mPopBg.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.lib_ui_pop_bg_yellow1));
        } else if ((mType & BG_BLUE) == BG_BLUE) {
            mPopBg.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.lib_ui_pop_bg_blue1));
        }
    }



    public void hide() {
        Log.d(TAG, "call [hide]");
        mHandler.removeCallbacks(autoCloseRunnable);
        needChange = 0;
        ViewParent parent = getParent();
        hideBody();
        messages = null;
        mType = -1;
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(this);
        } else {
            this.setVisibility(GONE);
        }

        if (onHideDialogCallBack != null) {
            final boolean consume = onHideDialogCallBack.get();
            onHideDialogCallBack = null;
            if (consume) {
                return;
            }
        }

    }

    /**
     * 点击弹窗中的二维码、等按钮，不进行弹窗关闭回调
     */
    public void hide(boolean isCallBack) {
        Log.d(TAG, "call [hide]");
        mHandler.removeCallbacks(autoCloseRunnable);
        needChange = 0;
        ViewParent parent = getParent();
        hideBody();
        messages = null;

        mType = -1;

        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(this);
        } else {
            this.setVisibility(GONE);
        }

        if (onHideDialogCallBack != null && isCallBack) {
            final boolean consume = onHideDialogCallBack.get();
            onHideDialogCallBack = null;
            if (consume) {
                return;
            }
        }

    }



    private void hideBody() {
        if (mode == MODE_ONE) {
            for (int i = 0; i < mllMessageContainer.getChildCount(); i++) {
                mllMessageContainer.getChildAt(i).setVisibility(GONE);
            }
            mTitleImg.setImageBitmap(null);
            mImgClose.setVisibility(GONE);
        }
    }



    private boolean needParse() {
        return needChange != 0;
    }

    private boolean eq(String[] data1, String[] data2) {
        if (data1.length != data2.length) {
            return false;
        }
        for (int i = 0; i < data1.length; i++) {
            Log.d("updateRecognizeMessage", "i:" + i + ",data1[i]" + data1[i] + ",data2[i]:" + data2[i]);
            if (data1[i] == null || data2[i] == null) {
                continue;
            }
            if (!data1[i].equals(data2[i])) {
                return false;
            }
        }

        return true;
    }


}
