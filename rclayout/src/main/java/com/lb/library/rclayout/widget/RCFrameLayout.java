package com.lb.library.rclayout.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Checkable;
import android.widget.FrameLayout;


import com.lb.library.rclayout.R;

import java.util.ArrayList;

/**
 * Created by lb on 2019-08-30.
 */

public class RCFrameLayout extends FrameLayout implements Checkable {

    private boolean mRoundAsCircle;//是否是一个圆
    private float[] mRadii = new float[8];//四个圆角的x、y半径
    private float mStrokeWidth;//描边的宽度
    private int mStrokeColor;//描边的颜色
    private boolean mClipBackground;//是否裁剪背景色

    private ColorStateList mStrokeColorStateList;//描边颜色状态

    private Path mClipPath;//裁剪区域
    private Paint mPaint;//画笔
    private Region mAreaRegion;//内容区域
    private RectF mLayer;//画布图层大小

    private OnCheckChangedListener mOnCheckChangedListener;

    public RCFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public RCFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RCFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attributeSet) {
        TypedArray ta = context.obtainStyledAttributes(attributeSet, R.styleable.RCFrameLayout);
        mRoundAsCircle = ta.getBoolean(R.styleable.RCFrameLayout_round_as_circle, false);
        mStrokeColorStateList = ta.getColorStateList(R.styleable.RCFrameLayout_stroke_color);
        if (null != mStrokeColorStateList) {
            mStrokeColor = mStrokeColorStateList.getDefaultColor();
        } else {
            mStrokeColor = Color.WHITE;
        }
        mStrokeWidth = ta.getDimensionPixelSize(R.styleable.RCFrameLayout_stroke_width, 0);
        mClipBackground = ta.getBoolean(R.styleable.RCFrameLayout_clip_background, false);
        int roundCorner = ta.getDimensionPixelSize(R.styleable.RCFrameLayout_round_corner, 0);
        int roundCornerTopLeft = ta.getDimensionPixelSize(R.styleable.RCFrameLayout_round_corner_top_left, roundCorner);
        int roundCornerTopRight = ta.getDimensionPixelSize(R.styleable.RCFrameLayout_round_corner_top_right, roundCorner);
        int roundCornerBottomLeft = ta.getDimensionPixelSize(R.styleable.RCFrameLayout_round_corner_bottom_left, roundCorner);
        int roundCornerBottomRight = ta.getDimensionPixelSize(R.styleable.RCFrameLayout_round_corner_bottom_right, roundCorner);
        ta.recycle();

        mRadii[0] = roundCornerTopLeft;
        mRadii[1] = roundCornerTopLeft;
        mRadii[2] = roundCornerTopRight;
        mRadii[3] = roundCornerTopRight;
        mRadii[4] = roundCornerBottomLeft;
        mRadii[5] = roundCornerBottomLeft;
        mRadii[6] = roundCornerBottomRight;
        mRadii[7] = roundCornerBottomRight;

        mLayer = new RectF();
        mClipPath = new Path();
        mAreaRegion = new Region();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.WHITE);
        mPaint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mLayer.set(0, 0, w, h);
        refreshRegion(w, h);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.saveLayer(mLayer, null, Canvas.ALL_SAVE_FLAG);
        super.dispatchDraw(canvas);
        if (mStrokeWidth > 0) {
            //支持半透明描边，将与描边区域重叠的内容裁剪掉
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            mPaint.setColor(Color.WHITE);
            mPaint.setStrokeWidth(mStrokeWidth * 2);
            mPaint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(mClipPath, mPaint);

            //绘制描边
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
            mPaint.setColor(mStrokeColor);
            mPaint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(mClipPath, mPaint);
        }
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            canvas.drawPath(mClipPath, mPaint);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                Path path = new Path();
                path.addRect(0, 0, (int) mLayer.width(), (int) mLayer.height(), Path.Direction.CW);
                path.op(mClipPath, Path.Op.DIFFERENCE);
                canvas.drawPath(path, mPaint);
            }
        }
        canvas.restore();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mClipBackground) {
            canvas.save();
            canvas.clipPath(mClipPath);
            super.draw(canvas);
            canvas.restore();
        } else {
            super.draw(canvas);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getAction();//跳过非绘制区域
        if (action == MotionEvent.ACTION_DOWN && !mAreaRegion.contains((int) ev.getX(), (int) ev.getY())) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
            refreshDrawableState();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            setPressed(false);
            refreshDrawableState();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void invalidate() {
        refreshRegion(getWidth(), getHeight());
        super.invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        drawableStateChanged(this);
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked != mChecked) {
            mChecked = checked;
            refreshDrawableState();
            if (mOnCheckChangedListener != null) {
                mOnCheckChangedListener.onCheckedChanged(this, mChecked);
            }
        }
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }

    private void refreshRegion(int w, int h) {
        RectF area = new RectF();
        area.left = getPaddingLeft();
        area.top = getPaddingTop();
        area.right = w - getPaddingRight();
        area.bottom = h - getPaddingBottom();

        mClipPath.reset();
        if (mRoundAsCircle) {
            float a = area.width() >= area.height() ? area.height() : area.width();
            float r = a / 2;
            PointF center = new PointF(w / 2, h / 2);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                mClipPath.addCircle(center.x, center.y, r, Path.Direction.CW);
                mClipPath.moveTo(0, 0);//通过空操作让path占满画布
                mClipPath.moveTo(w, h);
            } else {
                float y = h / 2 - r;
                mClipPath.moveTo(area.left, y);
                mClipPath.addCircle(center.x, y + r, r, Path.Direction.CW);
            }
        } else {
            mClipPath.addRoundRect(area, mRadii, Path.Direction.CW);
        }
        Region clip = new Region((int) area.left, (int) area.top, (int) area.right, (int) area.bottom);
        mAreaRegion.setPath(mClipPath, clip);
    }

    public boolean isRoundAsCircle() {
        return mRoundAsCircle;
    }

    public void setRoundAsCircle(boolean roundAsCircle) {
        this.mRoundAsCircle = roundAsCircle;
        invalidate();
    }

    public boolean isClipBackground() {
        return mClipBackground;
    }

    public void setClipBackground(boolean clipBackground) {
        this.mClipBackground = clipBackground;
        invalidate();
    }

    public void setLeftTopRadius(int roundCorner) {
        this.mRadii[0] = roundCorner;
        this.mRadii[1] = roundCorner;
        invalidate();
    }

    public void setLeftBottomRadius(int roundCorner) {
        this.mRadii[4] = roundCorner;
        this.mRadii[5] = roundCorner;
        invalidate();
    }

    public void setRightTopRadius(int roundCorner) {
        this.mRadii[2] = roundCorner;
        this.mRadii[3] = roundCorner;
        invalidate();
    }

    public void setRightBottomRadius(int roundCorner) {
        this.mRadii[6] = roundCorner;
        this.mRadii[7] = roundCorner;
        invalidate();
    }

    public void setCornerRadius(int radius) {
        for (int i = 0; i < mRadii.length; i++) {
            mRadii[i] = radius;
        }
        invalidate();
    }

    public void setStrokeWidth(int strokeWidth) {
        this.mStrokeWidth = strokeWidth;
        invalidate();
    }

    public void setStrokeColor(int color) {
        this.mStrokeColor = color;
        invalidate();
    }

    public OnCheckChangedListener getOnCheckChangedListener() {
        return mOnCheckChangedListener;
    }

    public void setOnCheckChangedListener(OnCheckChangedListener onCheckChangedListener) {
        this.mOnCheckChangedListener = onCheckChangedListener;
    }

    ////支持selector
    private boolean mChecked;

    public void drawableStateChanged(View view) {
        ArrayList<Integer> stateListArray = new ArrayList<>();
        if (view instanceof Checkable) {
            stateListArray.add(android.R.attr.state_checkable);
            if (((Checkable) view).isChecked()) {
                stateListArray.add(android.R.attr.state_checked);
            }
            if (view.isEnabled()) {
                stateListArray.add(android.R.attr.state_enabled);
            }
            if (view.isFocused()) {
                stateListArray.add(android.R.attr.state_focused);
            }
            if (view.isPressed()) {
                stateListArray.add(android.R.attr.state_pressed);
            }
            if (view.isHovered()) {
                stateListArray.add(android.R.attr.state_hovered);
            }
            if (view.isSelected()) {
                stateListArray.add(android.R.attr.state_selected);
            }
            if (view.isActivated()) {
                stateListArray.add(android.R.attr.state_activated);
            }
            if (view.hasWindowFocus()) {
                stateListArray.add(android.R.attr.state_window_focused);
            }

            if (null != mStrokeColorStateList && mStrokeColorStateList.isStateful()) {
                int[] stateList = new int[stateListArray.size()];
                for (int i = 0; i < stateListArray.size(); i++) {
                    stateList[i] = stateListArray.get(i);
                }
                int stateColor = mStrokeColorStateList.getColorForState(stateList, mStrokeColor);
                setStrokeColor(stateColor);
            }
        }
    }

    public interface OnCheckChangedListener {
        void onCheckedChanged(View view, boolean isChecked);
    }


}
