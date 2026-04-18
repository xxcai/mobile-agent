package com.hh.agent.shortcut;

import android.content.Context;
import com.hh.agent.H5BenchmarkActivity;
import com.hh.agent.android.floating.FloatingBallLifecycleCallbacks;
import com.hh.agent.app.RouteShortcutProvider;
import com.hh.agent.core.shortcut.ShortcutExecutor;

import java.util.ArrayList;
import java.util.List;

public final class AppShortcutProvider {

    private AppShortcutProvider() {
    }

    public static List<ShortcutExecutor> createShortcuts(Context context) {
        List<ShortcutExecutor> shortcuts = new ArrayList<>();
        shortcuts.add(new SearchContactsShortcut());
        shortcuts.add(new SendImMessageShortcut());
        shortcuts.add(new StartH5BenchmarkShortcut(
                FloatingBallLifecycleCallbacks::getCurrentForegroundActivity,
                () -> {
                    android.app.Activity activity = FloatingBallLifecycleCallbacks.getCurrentForegroundActivity();
                    if (activity instanceof H5BenchmarkActivity) {
                        return ((H5BenchmarkActivity) activity).getBenchmarkHost();
                    }
                    return null;
                }));
        shortcuts.addAll(RouteShortcutProvider.createShortcuts(context));
        return shortcuts;
    }
}
