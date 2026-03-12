package com.hh.agent.floating;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * 悬浮球自定义View
 * 支持拖拽和边缘吸附
 */
public class FloatingBallView extends ImageView {

    private boolean isDragging = false;

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
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * 获取拖拽监听器
     */
    public OnTouchListener getDragTouchListener() {
        return new OnTouchListener() {
            private int touchOffsetX, touchOffsetY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                FloatingBallManager manager = FloatingBallManager.getInstance(getContext());
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 记录触摸点相对于悬浮球位置的偏移
                        touchOffsetX = (int) event.getRawX() - manager.getX();
                        touchOffsetY = (int) event.getRawY() - manager.getY();
                        isDragging = true;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            // 新位置 = 当前触摸点 - 偏移
                            int newX = (int) event.getRawX() - touchOffsetX;
                            int newY = (int) event.getRawY() - touchOffsetY;
                            manager.updatePosition(newX, newY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            isDragging = false;
                            manager.snapToEdge();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        };
    }
}
