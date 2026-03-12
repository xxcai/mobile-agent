package com.hh.agent.floating;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.view.View;
import android.widget.ImageView;

/**
 * 悬浮球自定义View
 * 支持拖拽和边缘吸附
 */
public class FloatingBallView extends ImageView {

    private int mStartX;
    private int mStartY;
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

        // 设置默认图片（可选，使用圆形背景即可）
        setScaleType(ScaleType.CENTER);
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
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        mStartX = (int) event.getRawX();
                        mStartY = (int) event.getRawY();
                        isDragging = true;
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            int deltaX = (int) event.getRawX() - mStartX;
                            int deltaY = (int) event.getRawY() - mStartY;

                            int[] position = new int[2];
                            v.getLocationOnScreen(position);

                            int newX = position[0] + deltaX;
                            int newY = position[1] + deltaY;

                            // 更新位置
                            FloatingBallManager.getInstance(getContext()).updatePosition(newX, newY);

                            mStartX = (int) event.getRawX();
                            mStartY = (int) event.getRawY();
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        if (isDragging) {
                            isDragging = false;
                            // 执行边缘吸附
                            FloatingBallManager.getInstance(getContext()).snapToEdge();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        };
    }
}
