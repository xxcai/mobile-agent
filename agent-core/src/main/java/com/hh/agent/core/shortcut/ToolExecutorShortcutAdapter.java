package com.hh.agent.core.shortcut;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

/**
 * Adapts an existing ToolExecutor into the shortcut runtime model.
 */
public final class ToolExecutorShortcutAdapter implements ShortcutExecutor {

    private final ToolExecutor toolExecutor;
    private final ShortcutDefinition definition;

    public ToolExecutorShortcutAdapter(ToolExecutor toolExecutor) {
        if (toolExecutor == null) {
            throw new IllegalArgumentException("ToolExecutor cannot be null");
        }
        this.toolExecutor = toolExecutor;
        this.definition = buildDefinition(toolExecutor);
    }

    @Override
    public ShortcutDefinition getDefinition() {
        return definition;
    }

    @Override
    public ToolResult execute(JSONObject args) {
        return toolExecutor.execute(args);
    }

    private static ShortcutDefinition buildDefinition(ToolExecutor toolExecutor) {
        ToolDefinition toolDefinition = toolExecutor.getDefinition();
        if (toolDefinition == null) {
            throw new IllegalArgumentException("ToolDefinition cannot be null");
        }

        return ShortcutDefinition.builder(
                        toolExecutor.getName(),
                        toolDefinition.getTitle(),
                        toolDefinition.getDescription()
                )
                .argsSchema(toolDefinition.getArgsSchemaJsonString())
                .argsExample(toolDefinition.getArgsExampleJsonString())
                .build();
    }
}
