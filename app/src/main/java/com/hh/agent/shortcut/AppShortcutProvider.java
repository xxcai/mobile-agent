package com.hh.agent.shortcut;

import android.content.Context;
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
        shortcuts.addAll(RouteShortcutProvider.createShortcuts(context));
        return shortcuts;
    }
}
