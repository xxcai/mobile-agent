package com.hh.agent.android.floating;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.hh.agent.android.log.AgentLogs;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 默认的悬浮球生命周期控制。
 * App 在前台且当前页面不在隐藏列表中时显示悬浮球，否则隐藏。
 */
public class FloatingBallLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "FloatingBallLifecycle";
    private static final long STABLE_ACTIVITY_DELAY_MS = 180L;

    private static final Set<String> DEFAULT_HIDDEN_ACTIVITY_CLASS_NAMES =
            Collections.singleton(ContainerActivity.class.getName());
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Application application;
    private final Set<String> hiddenActivityClassNames;
    private static WeakReference<Activity> currentForegroundActivityRef =
            new WeakReference<>(null);
    private static final Object OBSERVER_LOCK = new Object();
    private static final List<StableActivityObserver> STABLE_ACTIVITY_OBSERVERS = new ArrayList<>();
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
        currentForegroundActivityRef = new WeakReference<>(activity);
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
        Activity referencedActivity = currentForegroundActivityRef.get();
        if (referencedActivity == activity) {
            currentForegroundActivityRef = new WeakReference<>(null);
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
        currentForegroundActivityRef = new WeakReference<>(activity);
        notifyStableActivityLater(activity);
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

    public static Activity getCurrentForegroundActivity() {
        return currentForegroundActivityRef.get();
    }

    public static Activity awaitNextStableForegroundActivity(boolean excludeContainerActivity,
                                                             long timeoutMs) {
        Activity currentActivity = currentForegroundActivityRef.get();
        if (isEligibleStableActivity(currentActivity, excludeContainerActivity)) {
            return currentActivity;
        }

        CountDownLatch latch = new CountDownLatch(1);
        final Activity[] resultHolder = new Activity[1];
        StableActivityObserver observer = activity -> {
            if (!isEligibleStableActivity(activity, excludeContainerActivity)) {
                return false;
            }
            resultHolder[0] = activity;
            latch.countDown();
            return true;
        };

        synchronized (OBSERVER_LOCK) {
            STABLE_ACTIVITY_OBSERVERS.add(observer);
        }

        Activity maybeReady = currentForegroundActivityRef.get();
        if (isEligibleStableActivity(maybeReady, excludeContainerActivity)) {
            removeObserver(observer);
            return maybeReady;
        }

        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                removeObserver(observer);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            removeObserver(observer);
            return null;
        }
        return resultHolder[0];
    }

    private void notifyStableActivityLater(Activity activity) {
        WeakReference<Activity> activityRef = new WeakReference<>(activity);
        MAIN_HANDLER.postDelayed(() -> {
            Activity candidate = activityRef.get();
            Activity current = currentForegroundActivityRef.get();
            if (candidate == null || current != candidate) {
                return;
            }
            if (!isEligibleStableActivity(candidate, false)) {
                return;
            }
            notifyStableActivityObservers(candidate);
        }, STABLE_ACTIVITY_DELAY_MS);
    }

    private static void notifyStableActivityObservers(Activity activity) {
        List<StableActivityObserver> snapshot;
        synchronized (OBSERVER_LOCK) {
            snapshot = new ArrayList<>(STABLE_ACTIVITY_OBSERVERS);
        }
        for (StableActivityObserver observer : snapshot) {
            boolean consumed = observer.onStableActivity(activity);
            if (consumed) {
                removeObserver(observer);
            }
        }
    }

    private static void removeObserver(StableActivityObserver observer) {
        synchronized (OBSERVER_LOCK) {
            STABLE_ACTIVITY_OBSERVERS.remove(observer);
        }
    }

    private static boolean isEligibleStableActivity(Activity activity, boolean excludeContainerActivity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        if (excludeContainerActivity && activity instanceof ContainerActivity) {
            return false;
        }
        return activity.getWindow() != null
                && activity.getWindow().getDecorView() != null
                && activity.getWindow().getDecorView().isAttachedToWindow();
    }

    private boolean isActivityEligibleForForegroundTracking(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private interface StableActivityObserver {
        boolean onStableActivity(Activity activity);
    }
}
