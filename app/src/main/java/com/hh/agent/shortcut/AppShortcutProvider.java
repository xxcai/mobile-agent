package com.hh.agent.shortcut;

import com.hh.agent.core.shortcut.ShortcutExecutor;

import java.util.ArrayList;
import java.util.List;

public final class AppShortcutProvider {

    private AppShortcutProvider() {
    }

    public static List<ShortcutExecutor> createShortcuts() {
        List<ShortcutExecutor> shortcuts = new ArrayList<>();
        shortcuts.add(new SearchContactsShortcut());
        shortcuts.add(new SendImMessageShortcut());
        return shortcuts;
    }
}
