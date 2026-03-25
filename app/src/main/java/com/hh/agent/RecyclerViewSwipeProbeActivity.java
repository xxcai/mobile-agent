package com.hh.agent;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecyclerViewSwipeProbeActivity extends AppCompatActivity {

    private static final long MOVE_STEP_DELAY_MS = 16L;
    private static final long SWIPE_SETTLE_DELAY_MS = 180L;
    private static final int MOVE_STEP_COUNT = 6;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<String> eventLog = new ArrayList<>();
    private RecyclerView recyclerView;
    private ProbeAdapter adapter;
    private TextView reportView;
    private boolean probeRunning;
    private int scrollOffsetBefore;
    private int scrollOffsetAfter;
    private int accumulatedScrollDy;
    private int scrollEventCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Recycler Swipe Probe");
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
                    + " rawX=" + formatFloat(ev.getRawX())
                    + " rawY=" + formatFloat(ev.getRawY()));
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
        intro.setText("验证范围：当前 Activity 内部，通过 Activity.dispatchTouchEvent() 注入一组 swipe 事件。\n"
                + "预期：RecyclerView 收到滚动回调，verticalScrollOffset 明显增加。");
        intro.setLineSpacing(0f, 1.2f);
        container.addView(intro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProbeAdapter(buildItems());
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (probeRunning) {
                    accumulatedScrollDy += dy;
                    scrollEventCount++;
                    appendEvent("recycler.onScrolled dx=" + dx + " dy=" + dy
                            + " offset=" + recyclerView.computeVerticalScrollOffset());
                }
            }
        });
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(360));
        recyclerParams.topMargin = dp(16);
        container.addView(recyclerView, recyclerParams);

        addActionButton(container, "Inject Swipe Via Activity.dispatchTouchEvent",
                v -> recyclerView.post(this::runSwipeProbe));
        addActionButton(container, "Reset List To Top", v -> {
            mainHandler.removeCallbacksAndMessages(null);
            probeRunning = false;
            recyclerView.scrollToPosition(0);
            reportView.setText("list_reset_to_top=true");
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

    private void runSwipeProbe() {
        if (!recyclerView.isLaidOut() || probeRunning) {
            return;
        }
        resetProbeState();

        int[] location = new int[2];
        recyclerView.getLocationInWindow(location);
        float centerX = location[0] + (recyclerView.getWidth() / 2f);
        float startY = location[1] + (recyclerView.getHeight() * 0.78f);
        float endY = location[1] + (recyclerView.getHeight() * 0.22f);
        scrollOffsetBefore = recyclerView.computeVerticalScrollOffset();

        appendEvent("recycler.center_x=" + formatFloat(centerX));
        appendEvent("swipe.start_y=" + formatFloat(startY));
        appendEvent("swipe.end_y=" + formatFloat(endY));
        appendEvent("scroll_offset_before=" + scrollOffsetBefore);

        long downTime = SystemClock.uptimeMillis();
        dispatchSwipeEventSequence(downTime, centerX, startY, endY, 0);
    }

    private void dispatchSwipeEventSequence(long downTime,
                                            float x,
                                            float startY,
                                            float endY,
                                            int stepIndex) {
        int totalSteps = MOVE_STEP_COUNT + 1;
        int action;
        float y;
        long eventTime = downTime + (stepIndex * MOVE_STEP_DELAY_MS);
        if (stepIndex == 0) {
            action = MotionEvent.ACTION_DOWN;
            y = startY;
        } else if (stepIndex < totalSteps) {
            action = MotionEvent.ACTION_MOVE;
            float fraction = stepIndex / (float) totalSteps;
            y = startY + ((endY - startY) * fraction);
        } else {
            action = MotionEvent.ACTION_UP;
            y = endY;
        }

        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        boolean handled;
        try {
            handled = dispatchTouchEvent(event);
            appendEvent("dispatch_result action=" + actionToString(action) + " handled=" + handled);
        } finally {
            event.recycle();
        }

        if (stepIndex < totalSteps) {
            mainHandler.postDelayed(
                    () -> dispatchSwipeEventSequence(downTime, x, startY, endY, stepIndex + 1),
                    MOVE_STEP_DELAY_MS);
            return;
        }

        mainHandler.postDelayed(this::finishProbe, SWIPE_SETTLE_DELAY_MS);
    }

    private void finishProbe() {
        scrollOffsetAfter = recyclerView.computeVerticalScrollOffset();
        appendEvent("scroll_offset_after=" + scrollOffsetAfter);
        appendEvent("scroll_offset_delta=" + (scrollOffsetAfter - scrollOffsetBefore));

        boolean pass = scrollOffsetAfter > scrollOffsetBefore
                && accumulatedScrollDy > 0
                && scrollEventCount > 0;

        StringBuilder report = new StringBuilder();
        report.append("# Recycler Swipe Probe\n");
        report.append("test_item=Activity.dispatchTouchEvent swipe\n");
        report.append("input.recycler_item_count=").append(adapter.getItemCount()).append('\n');
        report.append("expected=RecyclerView scroll offset increases after injected swipe\n");
        report.append("actual.scroll_offset_before=").append(scrollOffsetBefore).append('\n');
        report.append("actual.scroll_offset_after=").append(scrollOffsetAfter).append('\n');
        report.append("actual.scroll_offset_delta=").append(scrollOffsetAfter - scrollOffsetBefore).append('\n');
        report.append("actual.accumulated_scroll_dy=").append(accumulatedScrollDy).append('\n');
        report.append("actual.scroll_event_count=").append(scrollEventCount).append('\n');
        report.append("result=").append(pass ? "PASS" : "FAIL").append('\n');
        report.append('\n');
        report.append("event_log:\n");
        for (String line : eventLog) {
            report.append("- ").append(line).append('\n');
        }
        reportView.setText(report.toString());
        probeRunning = false;
    }

    private void resetProbeState() {
        mainHandler.removeCallbacksAndMessages(null);
        eventLog.clear();
        probeRunning = true;
        scrollOffsetBefore = 0;
        scrollOffsetAfter = 0;
        accumulatedScrollDy = 0;
        scrollEventCount = 0;
    }

    private void appendEvent(String message) {
        eventLog.add(message);
    }

    private List<String> buildItems() {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            items.add(String.format(Locale.US,
                    "Mock Row %03d  This is a swipe probe item used to verify RecyclerView scrolling.",
                    i));
        }
        return items;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static String actionToString(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.valueOf(action);
        }
    }

    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static final class ProbeAdapter extends RecyclerView.Adapter<ProbeViewHolder> {

        private final List<String> items;

        ProbeAdapter(List<String> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ProbeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            int paddingHorizontal = Math.round(parent.getResources().getDisplayMetrics().density * 16);
            int paddingVertical = Math.round(parent.getResources().getDisplayMetrics().density * 14);
            textView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            textView.setGravity(Gravity.CENTER_VERTICAL);
            textView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ProbeViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ProbeViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class ProbeViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        ProbeViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }

        void bind(String text) {
            textView.setText(text);
        }
    }
}
