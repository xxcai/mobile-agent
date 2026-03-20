package com.hh.agent.core.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用实体类
 */
public class ToolCall {
    @SerializedName("id") private String id;
    @SerializedName("name") private String name;
    @SerializedName("display_name") private String displayName;
    @SerializedName("visible_in_tool_ui") private boolean visibleInToolUi;
    @SerializedName("status") private String status;  // "running" or "completed"
    @SerializedName("arguments") private String arguments;
    @SerializedName("result") private String result;

    public ToolCall() {}

    public ToolCall(String id, String name) {
        this.id = id;
        this.name = name;
        this.status = "running";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isVisibleInToolUi() {
        return visibleInToolUi;
    }

    public void setVisibleInToolUi(boolean visibleInToolUi) {
        this.visibleInToolUi = visibleInToolUi;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
