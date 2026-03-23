package com.hh.agent.android.floating;

import android.app.Activity;
import android.app.Application;
import com.hh.agent.android.log.AgentLogs;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认的悬浮球生命周期控制。
 * App 在前台且当前页面不在隐藏列表中时显示悬浮球，否则隐藏。
 */
public class FloatingBallLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "FloatingBallLifecycle";

    private static final Set<String> DEFAULT_HIDDEN_ACTIVITY_CLASS_NAMES =
            Collections.singleton(ContainerActivity.class.getName());

    private final Application application;
    private final Set<String> hiddenActivityClassNames;
    private int foregroundActivityCount = 0;
    private Activity currentForegroundActivity;

    public FloatingBallLifecycleCallbacks(Application application,
                                          List<String> hiddenActivityClassNames) {
        this.application = application;
        this.hiddenActivityClassNames = new HashSet<>(DEFAULT_HIDDEN_ACTIVITY_CLASS_NAMES);
        if (hiddenActivityClassNames != null) {
            this.hiddenActivityClassNames.addAll(hiddenActivityClassNames);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        foregroundActivityCount++;
        currentForegroundActivity = isActivityEligibleForForegroundTracking(activity) ? activity : null;
        AgentLogs.debug(TAG, "activity_started", "foreground_count=" + foregroundActivityCount);
        updateFloatingBallVisibility();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        foregroundActivityCount--;
        if (foregroundActivityCount < 0) {
            foregroundActivityCount = 0;
        }

        if (activity == currentForegroundActivity) {
            currentForegroundActivity = null;
        }

        AgentLogs.debug(TAG, "activity_stopped", "foreground_count=" + foregroundActivityCount);
        updateFloatingBallVisibility();
    }

    @Override
    public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {
        // not used
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (activity == currentForegroundActivity) {
            currentForegroundActivity = null;
            updateFloatingBallVisibility();
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        currentForegroundActivity = isActivityEligibleForForegroundTracking(activity) ? activity : null;
        updateFloatingBallVisibility();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        // ContainerActivity may finish before its enter transition fully settles.
        // Once it is closing, stop treating it as the foreground blocker immediately.
        if (activity == currentForegroundActivity && activity.isFinishing()) {
            currentForegroundActivity = null;
            updateFloatingBallVisibility();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {
        // not used
    }

    private void updateFloatingBallVisibility() {
        FloatingBallManager.getInstance(application).updateVisibility(
                foregroundActivityCount > 0,
                shouldHideForActivity(currentForegroundActivity)
        );
    }

    private boolean shouldHideForActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        if (!isActivityEligibleForForegroundTracking(activity)) {
            return false;
        }
        return hiddenActivityClassNames.contains(activity.getClass().getName());
    }

    private boolean isActivityEligibleForForegroundTracking(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
