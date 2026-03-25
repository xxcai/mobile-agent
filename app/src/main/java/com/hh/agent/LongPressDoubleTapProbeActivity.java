package com.hh.agent;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LongPressDoubleTapProbeActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<String> eventLog = new ArrayList<>();
    private ProbeButton targetButton;
    private TextView reportView;
    private boolean probeRunning;
    private String activeProbeName;
    private boolean targetReceivedDown;
    private boolean targetReceivedUp;
    private boolean longClickTriggered;
    private boolean doubleTapTriggered;
    private long longClickAtMs;
    private long doubleTapAtMs;
    private long probeStartAtMs;
    private long probeEndAtMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Long Press And Double Tap Probe");
        setContentView(createContentView());
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (probeRunning) {
            appendEvent("activity.dispatchTouchEvent action="
                    + actionToString(ev.getActionMasked())
                    + " x=" + formatFloat(ev.getX())
                    + " y=" + formatFloat(ev.getY()));
        }
        return super.dispatchTouchEvent(ev);
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);

        TextView intro = new TextView(this);
        intro.setText("验证范围：当前 Activity 内，通过 Activity.dispatchTouchEvent() 注入事件序列。\n"
                + "长按观察 View.OnLongClickListener，双击观察 GestureDetector.onDoubleTap。");
        intro.setLineSpacing(0f, 1.2f);
        container.addView(intro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        targetButton = new ProbeButton(this);
        targetButton.setText("Target Surface");
        targetButton.setAllCaps(false);
        targetButton.setLongClickable(true);
        targetButton.setPadding(dp(20), dp(28), dp(20), dp(28));
        targetButton.setOnLongClickListener(v -> {
            if (probeRunning) {
                longClickTriggered = true;
                longClickAtMs = SystemClock.uptimeMillis();
                appendEvent("target.onLongClick");
                updateTargetLabel();
            }
            return true;
        });
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        targetParams.topMargin = dp(16);
        container.addView(targetButton, targetParams);

        addActionButton(container, "Inject Long Press",
                v -> targetButton.post(this::runLongPressProbe));
        addActionButton(container, "Inject Double Tap",
                v -> targetButton.post(this::runDoubleTapProbe));
        addActionButton(container, "Clear Report", v -> {
            mainHandler.removeCallbacksAndMessages(null);
            probeRunning = false;
            activeProbeName = null;
            targetButton.setText("Target Surface");
            reportView.setText("");
        });

        reportView = new TextView(this);
        reportView.setTextIsSelectable(true);
        reportView.setMovementMethod(new ScrollingMovementMethod());
        reportView.setPadding(0, dp(16), 0, 0);
        container.addView(reportView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return scrollView;
    }

    private void addActionButton(LinearLayout container,
                                 String text,
                                 View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        container.addView(button, params);
    }

    private void runLongPressProbe() {
        if (!targetButton.isLaidOut() || probeRunning) {
            return;
        }
        Rect rect = beginProbe("Activity.dispatchTouchEvent long press");
        float centerX = rect.centerX();
        float centerY = rect.centerY();
        int longPressTimeout = ViewConfiguration.getLongPressTimeout();
        long downTime = SystemClock.uptimeMillis();

        appendEvent("long_press_timeout_ms=" + longPressTimeout);
        appendEvent("target.center=(" + formatFloat(centerX) + "," + formatFloat(centerY) + ")");

        dispatchProbeEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, centerX, centerY, 0));
        mainHandler.postDelayed(
                () -> dispatchProbeEvent(MotionEvent.obtain(
                        downTime,
                        downTime + longPressTimeout + 40L,
                        MotionEvent.ACTION_UP,
                        centerX,
                        centerY,
                        0)),
                longPressTimeout + 40L);
        mainHandler.postDelayed(
                () -> finishProbe(
                        "long_press",
                        rect,
                        longClickTriggered && !doubleTapTriggered,
                        "longClick=true and doubleTap=false"),
                longPressTimeout + 140L);
    }

    private void runDoubleTapProbe() {
        if (!targetButton.isLaidOut() || probeRunning) {
            return;
        }
        Rect rect = beginProbe("Activity.dispatchTouchEvent double tap");
        float centerX = rect.centerX();
        float centerY = rect.centerY();
        int doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
        long downTime = SystemClock.uptimeMillis();
        long secondTapGapMs = Math.min(120L, Math.max(80L, doubleTapTimeout / 2L));

        appendEvent("double_tap_timeout_ms=" + doubleTapTimeout);
        appendEvent("target.center=(" + formatFloat(centerX) + "," + formatFloat(centerY) + ")");

        dispatchProbeEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, centerX, centerY, 0));
        mainHandler.postDelayed(
                () -> dispatchProbeEvent(MotionEvent.obtain(
                        downTime,
                        downTime + 24L,
                        MotionEvent.ACTION_UP,
                        centerX,
                        centerY,
                        0)),
                24L);
        mainHandler.postDelayed(
                () -> dispatchProbeEvent(MotionEvent.obtain(
                        downTime + secondTapGapMs,
                        downTime + secondTapGapMs,
                        MotionEvent.ACTION_DOWN,
                        centerX,
                        centerY,
                        0)),
                secondTapGapMs);
        mainHandler.postDelayed(
                () -> dispatchProbeEvent(MotionEvent.obtain(
                        downTime + secondTapGapMs,
                        downTime + secondTapGapMs + 24L,
                        MotionEvent.ACTION_UP,
                        centerX,
                        centerY,
                        0)),
                secondTapGapMs + 24L);
        mainHandler.postDelayed(
                () -> finishProbe(
                        "double_tap",
                        rect,
                        doubleTapTriggered,
                        "doubleTap=true"),
                secondTapGapMs + 140L);
    }

    private Rect beginProbe(String probeName) {
        mainHandler.removeCallbacksAndMessages(null);
        eventLog.clear();
        targetReceivedDown = false;
        targetReceivedUp = false;
        longClickTriggered = false;
        doubleTapTriggered = false;
        longClickAtMs = 0L;
        doubleTapAtMs = 0L;
        probeStartAtMs = SystemClock.uptimeMillis();
        probeEndAtMs = 0L;
        activeProbeName = probeName;
        probeRunning = true;
        updateTargetLabel();

        Rect rect = new Rect();
        targetButton.getGlobalVisibleRect(rect);
        appendEvent("target.global_rect=" + rect.toShortString());
        return rect;
    }

    private void dispatchProbeEvent(MotionEvent event) {
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {
            boolean handled = dispatchTouchEvent(event);
            appendEvent("dispatch_result action=" + actionToString(event.getActionMasked())
                    + " handled=" + handled
                    + " eventTime=" + event.getEventTime());
        } finally {
            event.recycle();
        }
    }

    private void finishProbe(String testItem, Rect rect, boolean pass, String expected) {
        probeEndAtMs = SystemClock.uptimeMillis();
        appendEvent("probe_duration_ms=" + (probeEndAtMs - probeStartAtMs));

        StringBuilder report = new StringBuilder();
        report.append("# Long Press / Double Tap Probe\n");
        report.append("test_item=").append(testItem).append('\n');
        report.append("input.dispatch_path=Activity.dispatchTouchEvent\n");
        report.append("input.target_rect=").append(rect.toShortString()).append('\n');
        report.append("expected=").append(expected).append('\n');
        report.append("actual.target_received_down=").append(targetReceivedDown).append('\n');
        report.append("actual.target_received_up=").append(targetReceivedUp).append('\n');
        report.append("actual.long_click_triggered=").append(longClickTriggered).append('\n');
        report.append("actual.double_tap_triggered=").append(doubleTapTriggered).append('\n');
        report.append("actual.long_click_at_ms=").append(longClickAtMs).append('\n');
        report.append("actual.double_tap_at_ms=").append(doubleTapAtMs).append('\n');
        report.append("result=").append(pass ? "PASS" : "FAIL").append('\n');
        report.append('\n');
        report.append("event_log:\n");
        for (String line : eventLog) {
            report.append("- ").append(line).append('\n');
        }
        reportView.setText(report.toString());
        updateTargetLabel();
        probeRunning = false;
        activeProbeName = null;
    }

    private void appendEvent(String message) {
        String prefix = activeProbeName == null ? "manual" : activeProbeName;
        eventLog.add(prefix + " | " + message);
    }

    private void updateTargetLabel() {
        String text = String.format(
                Locale.US,
                "Target Surface\nDOWN=%s UP=%s LONG=%s DOUBLE=%s",
                targetReceivedDown,
                targetReceivedUp,
                longClickTriggered,
                doubleTapTriggered);
        targetButton.setText(text);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static String actionToString(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.valueOf(action);
        }
    }

    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private final class ProbeButton extends AppCompatButton {

        private final GestureDetector gestureDetector;

        ProbeButton(@NonNull LongPressDoubleTapProbeActivity context) {
            super(context);
            setGravity(Gravity.CENTER);
            gestureDetector = new GestureDetector(
                    context,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(@NonNull MotionEvent e) {
                            if (probeRunning) {
                                appendEvent("gesture.onDown");
                            }
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(@NonNull MotionEvent e) {
                            if (probeRunning) {
                                doubleTapTriggered = true;
                                doubleTapAtMs = SystemClock.uptimeMillis();
                                appendEvent("gesture.onDoubleTap");
                                updateTargetLabel();
                            }
                            return true;
                        }
                    });
            gestureDetector.setIsLongpressEnabled(true);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (probeRunning) {
                appendEvent("target.dispatchTouchEvent action=" + actionToString(event.getActionMasked())
                        + " x=" + formatFloat(event.getX())
                        + " y=" + formatFloat(event.getY()));
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (probeRunning) {
                appendEvent("target.onTouchEvent action=" + actionToString(event.getActionMasked())
                        + " x=" + formatFloat(event.getX())
                        + " y=" + formatFloat(event.getY()));
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    targetReceivedDown = true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    targetReceivedUp = true;
                }
                updateTargetLabel();
            }
            boolean gestureHandled = gestureDetector.onTouchEvent(event);
            boolean handled = super.onTouchEvent(event);
            return gestureHandled || handled;
        }
    }
}
