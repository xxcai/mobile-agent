package com.hh.agent;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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

public class TouchDispatchProbeActivity extends AppCompatActivity {

    private static final long CLICK_SETTLE_DELAY_MS = 64L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProbeButton targetButton;
    private TextView reportView;
    private String activeProbeLabel;
    private boolean recordingProbe;
    private final List<String> eventLog = new ArrayList<>();
    private boolean targetReceivedDown;
    private boolean targetReceivedUp;
    private boolean targetClicked;
    private long lastProbeDownHandledAtMs;
    private long lastProbeUpHandledAtMs;
    private long lastProbeClickAtMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Touch Dispatch Probe");
        setContentView(createContentView());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (recordingProbe) {
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

        container.addView(createIntroView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        targetButton = new ProbeButton(this);
        targetButton.setText("Target Button");
        targetButton.setAllCaps(false);
        targetButton.setPadding(dp(20), dp(24), dp(20), dp(24));
        targetButton.setOnClickListener(v -> {
            if (recordingProbe) {
                targetClicked = true;
                lastProbeClickAtMs = SystemClock.uptimeMillis();
                appendEvent("target.onClick");
                updateTargetLabel();
            } else {
                reportView.setText("manual_click=true\n请点击注入按钮开始验证。");
            }
        });
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        targetParams.topMargin = dp(16);
        container.addView(targetButton, targetParams);

        addActionButton(container, "Inject Via Activity.dispatchTouchEvent",
                v -> targetButton.post(() -> runProbe(DispatchPath.ACTIVITY)));
        addActionButton(container, "Inject Via DecorView.dispatchTouchEvent",
                v -> targetButton.post(() -> runProbe(DispatchPath.DECOR_VIEW)));
        addActionButton(container, "Clear Report", v -> {
            mainHandler.removeCallbacksAndMessages(null);
            recordingProbe = false;
            activeProbeLabel = null;
            targetButton.setText("Target Button");
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

    private TextView createIntroView() {
        TextView intro = new TextView(this);
        intro.setText("验证范围：当前 App 当前窗口内，构造 MotionEvent 后经 Activity / DecorView 分发。\n"
                + "预期：目标按钮收到 DOWN/UP 并触发 click，则说明该路径可在本 App 内注入触摸事件。");
        intro.setLineSpacing(0f, 1.2f);
        return intro;
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

    private void runProbe(DispatchPath path) {
        if (!targetButton.isLaidOut()) {
            reportView.setText("target_not_ready=true");
            return;
        }

        resetProbeState(path.label);
        Rect rect = new Rect();
        boolean visible = targetButton.getGlobalVisibleRect(rect);
        int[] locationInWindow = new int[2];
        targetButton.getLocationInWindow(locationInWindow);
        float centerX = locationInWindow[0] + (targetButton.getWidth() / 2f);
        float centerY = locationInWindow[1] + (targetButton.getHeight() / 2f);

        appendEvent("target.center_in_window x=" + formatFloat(centerX)
                + " y=" + formatFloat(centerY));
        appendEvent("target.global_visible=" + visible
                + " rect=" + rect.toShortString());

        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                centerX,
                centerY,
                0);
        MotionEvent upEvent = MotionEvent.obtain(
                downTime,
                downTime + 16L,
                MotionEvent.ACTION_UP,
                centerX,
                centerY,
                0);
        downEvent.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        upEvent.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);

        boolean downHandled;
        boolean upHandled;
        activeProbeLabel = path.label;
        try {
            downHandled = path.dispatch(this, downEvent);
            lastProbeDownHandledAtMs = SystemClock.uptimeMillis();
            appendEvent("dispatch_result action=DOWN handled=" + downHandled);
            upHandled = path.dispatch(this, upEvent);
            lastProbeUpHandledAtMs = SystemClock.uptimeMillis();
            appendEvent("dispatch_result action=UP handled=" + upHandled);
        } finally {
            downEvent.recycle();
            upEvent.recycle();
        }

        final boolean finalDownHandled = downHandled;
        final boolean finalUpHandled = upHandled;
        mainHandler.postDelayed(
                () -> finishProbe(path, centerX, centerY, visible, rect, finalDownHandled, finalUpHandled),
                CLICK_SETTLE_DELAY_MS);
    }

    private void resetProbeState(String probeLabel) {
        mainHandler.removeCallbacksAndMessages(null);
        eventLog.clear();
        targetReceivedDown = false;
        targetReceivedUp = false;
        targetClicked = false;
        lastProbeDownHandledAtMs = 0L;
        lastProbeUpHandledAtMs = 0L;
        lastProbeClickAtMs = 0L;
        activeProbeLabel = probeLabel;
        recordingProbe = true;
        updateTargetLabel();
    }

    private void finishProbe(DispatchPath path,
                             float centerX,
                             float centerY,
                             boolean visible,
                             Rect rect,
                             boolean downHandled,
                             boolean upHandled) {
        appendEvent("probe.settle_delay_ms=" + CLICK_SETTLE_DELAY_MS);
        boolean pass = downHandled && upHandled && targetReceivedDown && targetReceivedUp && targetClicked;
        renderReport(path, centerX, centerY, visible, rect, downHandled, upHandled, pass);
        recordingProbe = false;
        activeProbeLabel = null;
    }

    private void renderReport(DispatchPath path,
                              float centerX,
                              float centerY,
                              boolean visible,
                              Rect rect,
                              boolean downHandled,
                              boolean upHandled,
                              boolean pass) {
        StringBuilder report = new StringBuilder();
        report.append("# Touch Dispatch Probe\n");
        report.append("test_item=").append(path.label).append('\n');
        report.append("input.target=Target Button\n");
        report.append("input.center_in_window=(")
                .append(formatFloat(centerX))
                .append(',')
                .append(formatFloat(centerY))
                .append(")\n");
        report.append("input.global_visible=").append(visible).append('\n');
        report.append("input.global_rect=").append(rect.toShortString()).append('\n');
        report.append('\n');
        report.append("expected=target receives DOWN/UP and click=true\n");
        report.append("actual.down_handled=").append(downHandled).append('\n');
        report.append("actual.up_handled=").append(upHandled).append('\n');
        report.append("actual.target_received_down=").append(targetReceivedDown).append('\n');
        report.append("actual.target_received_up=").append(targetReceivedUp).append('\n');
        report.append("actual.target_clicked=").append(targetClicked).append('\n');
        report.append("actual.down_handled_at_ms=").append(lastProbeDownHandledAtMs).append('\n');
        report.append("actual.up_handled_at_ms=").append(lastProbeUpHandledAtMs).append('\n');
        report.append("actual.click_observed_at_ms=").append(lastProbeClickAtMs).append('\n');
        report.append("result=").append(pass ? "PASS" : "FAIL").append('\n');
        report.append('\n');
        report.append("event_log:\n");
        for (String line : eventLog) {
            report.append("- ").append(line).append('\n');
        }
        reportView.setText(report.toString());
        updateTargetLabel();
    }

    private void appendEvent(String message) {
        String prefix = activeProbeLabel == null ? "manual" : activeProbeLabel;
        eventLog.add(prefix + " | " + message);
    }

    private void updateTargetLabel() {
        String text = String.format(
                Locale.US,
                "Target Button\nDOWN=%s UP=%s CLICK=%s",
                targetReceivedDown,
                targetReceivedUp,
                targetClicked);
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
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            default:
                return String.valueOf(action);
        }
    }

    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private enum DispatchPath {
        ACTIVITY("Activity.dispatchTouchEvent") {
            @Override
            boolean dispatch(TouchDispatchProbeActivity activity, MotionEvent event) {
                return activity.dispatchTouchEvent(event);
            }
        },
        DECOR_VIEW("DecorView.dispatchTouchEvent") {
            @Override
            boolean dispatch(TouchDispatchProbeActivity activity, MotionEvent event) {
                return activity.getWindow().getDecorView().dispatchTouchEvent(event);
            }
        };

        private final String label;

        DispatchPath(String label) {
            this.label = label;
        }

        abstract boolean dispatch(TouchDispatchProbeActivity activity, MotionEvent event);
    }

    private final class ProbeButton extends AppCompatButton {

        ProbeButton(@NonNull TouchDispatchProbeActivity context) {
            super(context);
            setGravity(Gravity.CENTER);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (recordingProbe) {
                appendEvent("target.dispatchTouchEvent action=" + actionToString(event.getActionMasked())
                        + " x=" + formatFloat(event.getX())
                        + " y=" + formatFloat(event.getY()));
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (recordingProbe) {
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
            return super.onTouchEvent(event);
        }
    }
}
