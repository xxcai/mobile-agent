package com.hh.agent.android.floating;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

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
 * Application-wide Activity lifecycle observer for controlling floating-ball visibility.
 */
public class FloatingBallLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "FloatingBallLifecycle";
    private static final long STABLE_ACTIVITY_DELAY_MS = 180L;
    private static final long VISIBILITY_SETTLE_DELAY_MS = 180L;
    private static final long FOREGROUND_TRANSITION_GRACE_MS = 1500L;

    private static final Set<String> DEFAULT_HIDDEN_ACTIVITY_CLASS_NAMES =
            Collections.singleton(ContainerActivity.class.getName());
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Application application;
    private final Set<String> hiddenActivityClassNames;
    private final Runnable visibilityUpdateRunnable = this::applyFloatingBallVisibility;
    private static WeakReference<Activity> currentForegroundActivityRef =
            new WeakReference<>(null);
    private static final Object OBSERVER_LOCK = new Object();
    private static final List<StableActivityObserver> STABLE_ACTIVITY_OBSERVERS = new ArrayList<>();
    private Activity currentForegroundActivity;
    private boolean appInForeground;
    private long lastEligibleForegroundAtMs = 0L;

    public FloatingBallLifecycleCallbacks(Application application,
                                          List<String> hiddenActivityClassNames) {
        this.application = application;
        this.hiddenActivityClassNames = new HashSet<>(DEFAULT_HIDDEN_ACTIVITY_CLASS_NAMES);
        if (hiddenActivityClassNames != null) {
            this.hiddenActivityClassNames.addAll(hiddenActivityClassNames);
        }
        this.appInForeground = ProcessLifecycleOwner.get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(LifecycleOwner owner) {
                appInForeground = true;
                lastEligibleForegroundAtMs = SystemClock.elapsedRealtime();
                AgentLogs.debug(TAG, "process_on_start", null);
                scheduleFloatingBallVisibilityUpdate("process_on_start", 0L);
            }

            @Override
            public void onStop(LifecycleOwner owner) {
                appInForeground = false;
                AgentLogs.debug(TAG, "process_on_stop", null);
                scheduleFloatingBallVisibilityUpdate("process_on_stop", VISIBILITY_SETTLE_DELAY_MS);
            }
        });
    }

    @Override
    public void onActivityStarted(Activity activity) {
        currentForegroundActivity = isActivityEligibleForForegroundTracking(activity) ? activity : null;
        if (currentForegroundActivity != null) {
            lastEligibleForegroundAtMs = SystemClock.elapsedRealtime();
        }
        currentForegroundActivityRef = new WeakReference<>(activity);
        AgentLogs.debug(TAG, "activity_started", "activity=" + activity.getClass().getName());
        scheduleFloatingBallVisibilityUpdate("activity_started");
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (activity == currentForegroundActivity) {
            currentForegroundActivity = null;
        }
        Activity referencedActivity = currentForegroundActivityRef.get();
        if (referencedActivity == activity) {
            currentForegroundActivityRef = new WeakReference<>(null);
        }

        AgentLogs.debug(TAG, "activity_stopped", "activity=" + activity.getClass().getName());
        scheduleFloatingBallVisibilityUpdate("activity_stopped");
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        // not used
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (activity == currentForegroundActivity) {
            currentForegroundActivity = null;
            scheduleFloatingBallVisibilityUpdate("activity_destroyed");
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        currentForegroundActivity = isActivityEligibleForForegroundTracking(activity) ? activity : null;
        if (currentForegroundActivity != null) {
            lastEligibleForegroundAtMs = SystemClock.elapsedRealtime();
        }
        currentForegroundActivityRef = new WeakReference<>(activity);
        notifyStableActivityLater(activity);
        scheduleFloatingBallVisibilityUpdate("activity_resumed");
    }

    @Override
    public void onActivityPaused(Activity activity) {
        // ContainerActivity may finish before its transition fully settles. Delay visibility updates
        // so the host page can become stable before deciding whether to hide the floating ball.
        if (activity == currentForegroundActivity && activity.isFinishing()) {
            AgentLogs.debug(TAG, "activity_paused_finishing",
                    "activity=" + activity.getClass().getName() + " action=wait_for_settle");
            currentForegroundActivity = null;
            scheduleFloatingBallVisibilityUpdate("activity_paused_finishing");
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        // not used
    }

    private void scheduleFloatingBallVisibilityUpdate(String reason) {
        scheduleFloatingBallVisibilityUpdate(reason, VISIBILITY_SETTLE_DELAY_MS);
    }

    private void scheduleFloatingBallVisibilityUpdate(String reason, long delayMs) {
        MAIN_HANDLER.removeCallbacks(visibilityUpdateRunnable);
        AgentLogs.debug(TAG, "visibility_update_scheduled",
                "reason=" + reason
                        + " app_in_foreground=" + appInForeground
                        + " activity=" + describeActivity(currentForegroundActivity)
                        + " delay_ms=" + delayMs);
        MAIN_HANDLER.postDelayed(visibilityUpdateRunnable, delayMs);
    }

    private void applyFloatingBallVisibility() {
        long now = SystemClock.elapsedRealtime();
        long sinceLastEligibleForegroundMs = now - lastEligibleForegroundAtMs;
        boolean withinForegroundGrace = lastEligibleForegroundAtMs > 0
                && sinceLastEligibleForegroundMs < FOREGROUND_TRANSITION_GRACE_MS;
        boolean foreground = appInForeground || currentForegroundActivity != null || withinForegroundGrace;
        boolean blockedByCurrentActivity = shouldHideForActivity(currentForegroundActivity);
        AgentLogs.debug(TAG, "visibility_update_applied",
                "foreground=" + foreground
                        + " app_in_foreground=" + appInForeground
                        + " within_grace=" + withinForegroundGrace
                        + " blocked=" + blockedByCurrentActivity
                        + " activity=" + describeActivity(currentForegroundActivity));
        FloatingBallManager.getInstance(application).updateVisibility(
                foreground,
                blockedByCurrentActivity
        );

        EdgeGlowManager.getInstance(application).updateVisibility(foreground);

        if (withinForegroundGrace
                && !appInForeground
                && currentForegroundActivity == null
                && !blockedByCurrentActivity) {
            long remainingGraceMs = FOREGROUND_TRANSITION_GRACE_MS - sinceLastEligibleForegroundMs;
            if (remainingGraceMs > 0) {
                scheduleFloatingBallVisibilityUpdate("foreground_grace_recheck", remainingGraceMs);
            }
        }
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

    private static String describeActivity(Activity activity) {
        return activity == null ? "null" : activity.getClass().getName();
    }

    private interface StableActivityObserver {
        boolean onStableActivity(Activity activity);
    }
}