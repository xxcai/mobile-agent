package com.hh.agent.android.harness;

import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ResultStore {
    private static final int MAX_HISTORY = 50;

    private final LinkedList<ExecutionRecord> history;
    private final Map<String, ExecutionRecord> latestBySkill;
    private final Map<String, List<ExecutionRecord>> recordsBySkill;

    public ResultStore() {
        this.history = new LinkedList<>();
        this.latestBySkill = new HashMap<>();
        this.recordsBySkill = new HashMap<>();
    }

    public void record(String skillName, String shortcutName, JSONObject args, ToolResult result) {
        ExecutionRecord record = new ExecutionRecord();
        record.setTimestamp(System.currentTimeMillis());
        record.setSkillName(skillName);
        record.setShortcutName(shortcutName);
        record.setArgs(args);
        record.setResult(result);

        history.addFirst(record);
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }

        latestBySkill.put(skillName, record);

        List<ExecutionRecord> skillRecords = recordsBySkill.get(skillName);
        if (skillRecords == null) {
            skillRecords = new ArrayList<>();
            recordsBySkill.put(skillName, skillRecords);
        }
        skillRecords.add(record);
        if (skillRecords.size() > MAX_HISTORY) {
            skillRecords.remove(0);
        }
    }

    public void recordSkillValidation(String skillName, int stepCount, JSONObject args) {
        ExecutionRecord record = new ExecutionRecord();
        record.setTimestamp(System.currentTimeMillis());
        record.setSkillName(skillName);
        record.setShortcutName("skill_validation");
        record.setArgs(args);
        record.setResult(ToolResult.success()
            .with("validated", true)
            .with("step_count", stepCount)
            .with("executor", "native"));

        history.addFirst(record);
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }

        latestBySkill.put(skillName, record);

        List<ExecutionRecord> skillRecords = recordsBySkill.get(skillName);
        if (skillRecords == null) {
            skillRecords = new ArrayList<>();
            recordsBySkill.put(skillName, skillRecords);
        }
        skillRecords.add(record);
        if (skillRecords.size() > MAX_HISTORY) {
            skillRecords.remove(0);
        }
    }

    public void recordNativeStep(String skillName, int stepIndex, String toolName, JSONObject args, ToolResult result) {
        ExecutionRecord record = new ExecutionRecord();
        record.setTimestamp(System.currentTimeMillis());
        record.setSkillName(skillName);
        record.setShortcutName("native_step_" + stepIndex);
        record.setArgs(args);
        record.setResult(result);

        history.addFirst(record);
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }

        List<ExecutionRecord> skillRecords = recordsBySkill.get(skillName);
        if (skillRecords == null) {
            skillRecords = new ArrayList<>();
            recordsBySkill.put(skillName, skillRecords);
        }
        skillRecords.add(record);
        if (skillRecords.size() > MAX_HISTORY) {
            skillRecords.remove(0);
        }
    }

    public boolean hasSuccessfulExecution(String skillName) {
        ExecutionRecord record = latestBySkill.get(skillName);
        return record != null && record.isSuccess();
    }

    public ExecutionRecord getLatest(String skillName) {
        return latestBySkill.get(skillName);
    }

    public List<ExecutionRecord> getHistory(String skillName, int limit) {
        List<ExecutionRecord> skillRecords = recordsBySkill.get(skillName);
        if (skillRecords == null || skillRecords.isEmpty()) {
            return new ArrayList<>();
        }

        int count = Math.min(limit, skillRecords.size());
        return new ArrayList<>(skillRecords.subList(skillRecords.size() - count, skillRecords.size()));
    }

    public List<ExecutionRecord> getRecentHistory(int limit) {
        int count = Math.min(limit, history.size());
        return new ArrayList<>(history.subList(0, count));
    }

    public void clear() {
        history.clear();
        latestBySkill.clear();
        recordsBySkill.clear();
    }

    public static class ExecutionRecord {
        private long timestamp;
        private String skillName;
        private String shortcutName;
        private JSONObject args;
        private ToolResult result;
        private String executor;

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getSkillName() {
            return skillName;
        }

        public void setSkillName(String skillName) {
            this.skillName = skillName;
        }

        public String getShortcutName() {
            return shortcutName;
        }

        public void setShortcutName(String shortcutName) {
            this.shortcutName = shortcutName;
        }

        public JSONObject getArgs() {
            return args;
        }

        public void setArgs(JSONObject args) {
            this.args = args;
        }

        public ToolResult getResult() {
            return result;
        }

        public void setResult(ToolResult result) {
            this.result = result;
        }

        public String getExecutor() {
            return executor;
        }

        public void setExecutor(String executor) {
            this.executor = executor;
        }

        public boolean isSuccess() {
            if (result == null) return false;
            String json = result.toJsonString();
            return json.contains("\"success\":true");
        }

        public boolean isNativeExecution() {
            return shortcutName != null && shortcutName.startsWith("native_step_");
        }

        public boolean isSkillValidation() {
            return "skill_validation".equals(shortcutName);
        }
    }
}
