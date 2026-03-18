package com.hh.agent.library.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用实体类
 */
public class ToolCall {
    @SerializedName("id") private String id;
    @SerializedName("name") private String name;
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
