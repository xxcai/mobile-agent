package com.hh.agent.core.shortcut;

import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicitly registered shortcut runtime for routing shortcut execution by name.
 */
public final class ShortcutRuntime {

    private final LinkedHashMap<String, ShortcutExecutor> shortcuts = new LinkedHashMap<>();

    public void register(ShortcutExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ShortcutExecutor cannot be null");
        }
        ShortcutDefinition definition = executor.getDefinition();
        if (definition == null) {
            throw new IllegalArgumentException("ShortcutDefinition cannot be null");
        }
        String shortcutName = definition.getName();
        if (shortcuts.containsKey(shortcutName)) {
            throw new IllegalArgumentException("Shortcut with name '" + shortcutName + "' is already registered");
        }
        shortcuts.put(shortcutName, executor);
    }

    public void registerAll(Collection<? extends ShortcutExecutor> executors) {
        if (executors == null) {
            throw new IllegalArgumentException("Shortcut executors cannot be null");
        }
        for (ShortcutExecutor executor : executors) {
            register(executor);
        }
    }

    public ShortcutExecutor find(String shortcutName) {
        if (shortcutName == null || shortcutName.trim().isEmpty()) {
            return null;
        }
        return shortcuts.get(shortcutName.trim());
    }

    public List<ShortcutDefinition> listDefinitions() {
        List<ShortcutDefinition> definitions = new ArrayList<>();
        for (ShortcutExecutor executor : shortcuts.values()) {
            definitions.add(executor.getDefinition());
        }
        return definitions;
    }

    public Map<String, ShortcutExecutor> getRegisteredShortcuts() {
        return new LinkedHashMap<>(shortcuts);
    }

    public ToolResult execute(String shortcutName, JSONObject args) {
        ShortcutExecutor executor = find(shortcutName);
        if (executor == null) {
            return ToolResult.error("shortcut_not_supported",
                            "Shortcut '" + shortcutName + "' is not supported")
                    .with("requestedShortcut", shortcutName)
                    .with("fallbackSuggested", false);
        }

        JSONObject safeArgs = args != null ? args : new JSONObject();
        ToolResult validationResult = executor.validate(safeArgs);
        if (!isSuccess(validationResult)) {
            return validationResult;
        }
        return executor.execute(safeArgs);
    }

    private boolean isSuccess(ToolResult result) {
        return result != null && result.toJsonString().contains("\"success\":true");
    }
}
