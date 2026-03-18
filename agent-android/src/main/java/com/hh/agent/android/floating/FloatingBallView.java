package com.hh.agent.android.floating;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * 悬浮球自定义View
 * 支持拖拽和边缘吸附
 */
public class FloatingBallView extends ImageView {

    private boolean isDragging = false;
    private GestureDetector gestureDetector;
    private OnClickListener onSingleTapListener;

    // 记录按下时的初始位置
    private float mInitialTouchX;
    private float mInitialTouchY;
    private int mInitialBallX;
    private int mInitialBallY;

    public FloatingBallView(Context context) {
        super(context);
        init();
    }

    private void init() {
        // 设置48dp尺寸
        int size48dp = dpToPx(48);
        setLayoutParams(new android.view.ViewGroup.LayoutParams(size48dp, size48dp));

        // 创建圆形背景：50%透明度的浅灰色 #80CCCCCC
        ShapeDrawable background = new ShapeDrawable(new OvalShape());
        Paint paint = background.getPaint();
        paint.setColor(0x80CCCCCC);
        paint.setStyle(Paint.Style.FILL);
        setBackground(background);

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
                isDragging = true;
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());

                // 计算相对于初始按下的偏移
                float offsetX = e2.getRawX() - mInitialTouchX;
                float offsetY = e2.getRawY() - mInitialTouchY;

                // 新位置 = 初始球位置 + 手指偏移
                int newX = mInitialBallX + (int) offsetX;
                int newY = mInitialBallY + (int) offsetY;

                manager.updatePosition(newX, newY);
                return true;
            }

            // 拖拽结束
            @Override
            public void onLongPress(MotionEvent e) {
                isDragging = false;
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());
                manager.snapToEdge();
            }
        });
    }

    private int dpToPx(int dp) {
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
     * 获取触摸监听器（用于FloatingBallManager设置）
     */
    public OnTouchListener getDragTouchListener() {
        return (v, event) -> {
            // 记录按下时的初始位置
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());
                mInitialTouchX = event.getRawX();
                mInitialTouchY = event.getRawY();
                mInitialBallX = manager.getX();
                mInitialBallY = manager.getY();
            }

            // GestureDetector 处理所有触摸事件
            gestureDetector.onTouchEvent(event);

            // 拖拽结束时吸附到边缘
            if (event.getAction() == MotionEvent.ACTION_UP && isDragging) {
                isDragging = false;
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());
                manager.snapToEdge();
            }

            return true;
        };
    }
}
