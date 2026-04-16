package com.hh.agent.android.harness;

import java.util.List;
import java.util.ArrayList;

/**
 * ExecutionHint - 确定性步骤数据结构
 * 
 * 用途：
 * 1. 用于结果记录（ResultStore），不独立执行
 * 2. 字段与 Native SkillStepHint 一致
 * 
 * Native SkillStepHint 字段（agent_loop.cpp:313-333）：
 * - page, activity, target, aliases, region, action, max_attempts, readout
 */
public class ExecutionHint {
    private int index;
    private String name;
    private String phase;
    private String tool;
    private String targetHint;
    private String action;
    private String bounds;
    private String selector;
    private String text;
    private String gate;
    private boolean readout;
    private int dependsOn = -1;

    private String page;
    private String activity;
    private List<String> aliases;
    private String region;
    private int maxAttempts;
    private boolean diamondGate;
    private String gatePrompt;

    public ExecutionHint() {
        this.aliases = new ArrayList<>();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getTargetHint() {
        return targetHint;
    }

    public void setTargetHint(String targetHint) {
        this.targetHint = targetHint;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getBounds() {
        return bounds;
    }

    public void setBounds(String bounds) {
        this.bounds = bounds;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getGate() {
        return gate;
    }

    public void setGate(String gate) {
        this.gate = gate;
    }

    public boolean isReadout() {
        return readout;
    }

    public void setReadout(boolean readout) {
        this.readout = readout;
    }

    public int getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(int dependsOn) {
        this.dependsOn = dependsOn;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases != null ? aliases : new ArrayList<>();
    }

    public void addAlias(String alias) {
        if (alias != null && !aliases.contains(alias)) {
            aliases.add(alias);
        }
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isDiamondGate() {
        return diamondGate;
    }

    public void setDiamondGate(boolean diamondGate) {
        this.diamondGate = diamondGate;
    }

    public String getGatePrompt() {
        return gatePrompt;
    }

    public void setGatePrompt(String gatePrompt) {
        this.gatePrompt = gatePrompt;
    }

    public String toSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("step[").append(index).append("]: ");
        sb.append("action=").append(action);
        if (targetHint != null) {
            sb.append(", target=").append(targetHint);
        }
        if (page != null) {
            sb.append(", page=").append(page);
        }
        if (readout) {
            sb.append(", readout=true");
        }
        return sb.toString();
    }
}
