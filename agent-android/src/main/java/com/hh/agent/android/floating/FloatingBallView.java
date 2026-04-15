package com.hh.agent.android.floating;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

/**
 * 悬浮球自定义View
 * 支持拖拽和边缘吸附
 */
public class FloatingBallView extends ImageView {

    private static final long WORKING_ANIMATION_DURATION_MS = 1100L;
    private static final float WORKING_SWEEP_ANGLE = 110f;

    private boolean isDragging = false;
    private boolean isWorking = false;
    private GestureDetector gestureDetector;
    private OnClickListener onSingleTapListener;
    private final int touchSlop;
    private final Paint workingTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint workingRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF workingRingBounds = new RectF();
    private ValueAnimator workingAnimator;
    private float workingStartAngle = -90f;

    // 记录按下时的初始位置
    private float mInitialTouchX;
    private float mInitialTouchY;
    private int mInitialBallX;
    private int mInitialBallY;

    public FloatingBallView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    private void init() {
        // 设置48dp尺寸
        int size48dp = dpToPx(48);
        setLayoutParams(new ViewGroup.LayoutParams(size48dp, size48dp));

        // 使用偏科技感的冷色底色，便于衬托工作态环形动画
        ShapeDrawable background = new ShapeDrawable(new OvalShape());
        Paint paint = background.getPaint();
        paint.setColor(0xD01A2A44);
        paint.setStyle(Paint.Style.FILL);
        setBackground(background);
        setWillNotDraw(false);

        setScaleType(ScaleType.CENTER);

        // 必须设置 clickable 和 focusable 才能响应触摸事件
        setClickable(true);
        setFocusable(true);

        // 初始化 GestureDetector
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            // 单击事件
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (onSingleTapListener != null) {
                    onSingleTapListener.onClick(FloatingBallView.this);
                }
                return true;
            }

            // 拖拽滚动
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // 计算相对于初始按下的偏移
                float offsetX = e2.getRawX() - mInitialTouchX;
                float offsetY = e2.getRawY() - mInitialTouchY;
                if (!isDragging && Math.hypot(offsetX, offsetY) < touchSlop) {
                    return false;
                }

                isDragging = true;
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());

                // 新位置 = 初始球位置 + 手指偏移
                int newX = mInitialBallX + (int) offsetX;
                int newY = mInitialBallY + (int) offsetY;

                manager.updatePosition(newX, newY);
                return true;
            }
        });

        workingTrackPaint.setStyle(Paint.Style.STROKE);
        workingTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        workingTrackPaint.setColor(Color.argb(90, 120, 210, 255));
        workingTrackPaint.setStrokeWidth(dpToPx(2.2f));

        workingRingPaint.setStyle(Paint.Style.STROKE);
        workingRingPaint.setStrokeCap(Paint.Cap.ROUND);
        workingRingPaint.setColor(Color.argb(245, 151, 238, 255));
        workingRingPaint.setStrokeWidth(dpToPx(2.4f));
    }

    private int dpToPx(float dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * 设置单击监听器
     */
    public void setOnSingleTapListener(View.OnClickListener listener) {
        this.onSingleTapListener = listener;
    }

    /**
     * 设置当前 Agent 是否正在工作
     */
    public void setWorking(boolean isWorking) {
        if (this.isWorking == isWorking) {
            return;
        }

        this.isWorking = isWorking;
        if (isWorking) {
            startWorkingAnimation();
        } else {
            stopWorkingAnimation();
        }
        invalidate();
    }

    public boolean isWorking() {
        return isWorking;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isWorking) {
            return;
        }

        float inset = dpToPx(6f);
        workingRingBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(workingRingBounds, 0f, 360f, false, workingTrackPaint);
        canvas.drawArc(workingRingBounds, workingStartAngle, WORKING_SWEEP_ANGLE, false, workingRingPaint);
    }

    private void startWorkingAnimation() {
        stopWorkingAnimation();

        workingAnimator = ValueAnimator.ofFloat(0f, 360f);
        workingAnimator.setDuration(WORKING_ANIMATION_DURATION_MS);
        workingAnimator.setInterpolator(new LinearInterpolator());
        workingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        workingAnimator.addUpdateListener(animation -> {
            workingStartAngle = -90f + (float) animation.getAnimatedValue();
            invalidate();
        });
        workingAnimator.start();
    }

    private void stopWorkingAnimation() {
        if (workingAnimator != null) {
            workingAnimator.cancel();
            workingAnimator = null;
        }
        workingStartAngle = -90f;
    }

    /**
     * 获取触摸监听器（用于FloatingBallManager设置）
     */
    public OnTouchListener getDragTouchListener() {
        return (v, event) -> {
            // 记录按下时的初始位置
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());
                manager.cancelSnapAnimation();
                isDragging = false;
                mInitialTouchX = event.getRawX();
                mInitialTouchY = event.getRawY();
                mInitialBallX = manager.getX();
                mInitialBallY = manager.getY();
            }

            // GestureDetector 处理所有触摸事件
            gestureDetector.onTouchEvent(event);

            // 拖拽结束时吸附到边缘
            if ((event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) && isDragging) {
                isDragging = false;
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());
                manager.snapToEdge();
            }

            return true;
        };
    }
}
