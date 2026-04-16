package com.hh.agent.android.harness;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hh.agent.android.log.AgentLogs;

/**
 * Resolver - Skill 预加载与 YAML frontmatter 格式校验
 * 
 * 职责：
 * 1. 预加载 Skill，校验 YAML frontmatter 格式（与 Native SkillLoader 一致）
 * 2. 不独立执行，依赖 Native AgentLoop 的 planned_fast_execute
 * 3. 记录校验结果，供 ResultStore 使用
 */
public class Resolver {
    private static final String TAG = "Resolver";
    private static final Pattern YAML_FRONTMATTER_PATTERN = 
        Pattern.compile("^---\\s*\n([\\s\\S]*?)\n---\\s*\n([\\s\\S]*)$");

    private final Map<String, SkillContext> skillCache;
    private final List<String> validationErrors;
    private Context context;

    public Resolver() {
        this.skillCache = new HashMap<>();
        this.validationErrors = new ArrayList<>();
    }

    public Resolver setContext(Context context) {
        this.context = context;
        return this;
    }

    public SkillContext resolve(String skillName) {
        if (TextUtils.isEmpty(skillName)) {
            return null;
        }

        if (skillCache.containsKey(skillName)) {
            return skillCache.get(skillName);
        }

        SkillContext skillContext = loadFromAssets(skillName);
        if (skillContext != null) {
            skillCache.put(skillName, skillContext);
        }

        return skillContext;
    }

    private static final String[] SKILL_PATHS = {
        "builtin_workspace/skills/",
        "workspace/skills/"
    };

    private SkillContext loadFromAssets(String skillName) {
        if (context == null) {
            return null;
        }

        for (String basePath : SKILL_PATHS) {
            try {
                String skillPath = basePath + skillName + "/SKILL.md";
                InputStream is = context.getAssets().open(skillPath);
                String content = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
                is.close();

                AgentLogs.info(TAG, "skill_loaded_from_path", "skill=" + skillName + " path=" + skillPath);
                SkillContext skillContext = parseSkillYamlFrontmatter(skillName, content);
                
                if (skillContext != null && skillContext.isValid()) {
                    return skillContext;
                }
            } catch (Exception e) {
                AgentLogs.debug(TAG, "skill_path_not_found", 
                    "skill=" + skillName + " path=" + basePath + " message=" + e.getMessage());
            }
        }

        AgentLogs.warn(TAG, "skill_load_failed", "skill=" + skillName + " tried all paths");
        return null;
    }

    /**
     * 解析 YAML frontmatter（与 Native skill_loader.cpp 格式一致）
     * 
     * Native skill_loader.cpp 正则：
     * regex: "^---\\s*\n([\\s\\S]*?)\n---\\s*\n([\\s\\S]*)$"
     */
    private SkillContext parseSkillYamlFrontmatter(String skillName, String content) {
        Matcher matcher = YAML_FRONTMATTER_PATTERN.matcher(content);
        
        if (!matcher.find()) {
            AgentLogs.warn(TAG, "yaml_frontmatter_not_found", "skill=" + skillName);
            return createInvalidContext(skillName, "YAML frontmatter not found");
        }

        String yamlStr = matcher.group(1);
        String markdownContent = matcher.group(2);

        SkillContext skillContext = new SkillContext();
        skillContext.setName(skillName);
        skillContext.setMarkdownContent(markdownContent);

        try {
            JSONObject yamlJson = parseYamlToJson(yamlStr);
            
            if (yamlJson == null) {
                return createInvalidContext(skillName, "YAML parse failed");
            }

            parseYamlFields(skillContext, yamlJson);
            
            if (yamlJson.has("execution_hints")) {
                JSONObject executionHints = yamlJson.getJSONObject("execution_hints");
                parseExecutionHints(skillContext, executionHints);
            }

            validateSkillContext(skillContext);
            
            if (skillContext.isValid()) {
                AgentLogs.info(TAG, "yaml_frontmatter_parsed", 
                    "skill=" + skillName + " steps=" + skillContext.getExecutionHints().size());
            }
            
        } catch (Exception e) {
            AgentLogs.error(TAG, "yaml_parse_exception", 
                "skill=" + skillName + " message=" + e.getMessage(), e);
            return createInvalidContext(skillName, "YAML parse exception: " + e.getMessage());
        }

        return skillContext;
    }

    /**
     * 简化 YAML 解析为 JSON（不依赖 SnakeYAML，使用手动解析）
     */
    private JSONObject parseYamlToJson(String yamlStr) {
        try {
            JSONObject result = new JSONObject();
            String[] lines = yamlStr.split("\n");
            
            String currentKey = null;
            JSONObject currentObject = null;
            JSONArray currentArray = null;
            int currentIndent = 0;
            
            for (String line : lines) {
                if (TextUtils.isEmpty(line.trim())) {
                    continue;
                }

                int indent = getIndentLevel(line);
                String trimmed = line.trim();
                
                if (trimmed.startsWith("- ")) {
                    if (currentArray != null) {
                        String itemStr = trimmed.substring(2);
                        JSONObject item = parseYamlItem(itemStr);
                        currentArray.put(item);
                    }
                } else if (trimmed.contains(":")) {
                    int colonIndex = trimmed.indexOf(":");
                    String key = trimmed.substring(0, colonIndex).trim();
                    String value = trimmed.substring(colonIndex + 1).trim();
                    
                    if (TextUtils.isEmpty(value)) {
                        if (indent > currentIndent) {
                            if (currentKey != null && currentObject != null) {
                                if (currentObject.has(currentKey) && currentObject.get(currentKey) instanceof JSONArray) {
                                    currentArray = currentObject.getJSONArray(currentKey);
                                } else {
                                    currentArray = new JSONArray();
                                    currentObject.put(currentKey, currentArray);
                                }
                            }
                        } else {
                            currentKey = key;
                            currentObject = new JSONObject();
                            result.put(key, currentObject);
                            currentArray = null;
                        }
                        currentIndent = indent;
                    } else {
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (currentObject != null) {
                            currentObject.put(key, value);
                        } else {
                            result.put(key, value);
                        }
                    }
                }
            }
            
            return result;
        } catch (Exception e) {
            AgentLogs.warn(TAG, "yaml_to_json_failed", "message=" + e.getMessage());
            return null;
        }
    }

    private JSONObject parseYamlItem(String itemStr) {
        JSONObject item = new JSONObject();
        String[] fields = itemStr.split(",");
        
        for (String field : fields) {
            field = field.trim();
            if (field.contains(":")) {
                int colonIndex = field.indexOf(":");
                String key = field.substring(0, colonIndex).trim();
                String value = field.substring(colonIndex + 1).trim();
                
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                try {
                    item.put(key, value);
                } catch (org.json.JSONException ignored) {
                }
            }
        }
        
        return item;
    }

    private int getIndentLevel(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                indent++;
            } else if (line.charAt(i) == '\t') {
                indent += 2;
            } else {
                break;
            }
        }
        return indent;
    }

    private void parseYamlFields(SkillContext skillContext, JSONObject yamlJson) throws Exception {
        if (yamlJson.has("name")) {
            skillContext.setName(yamlJson.getString("name"));
        }
        if (yamlJson.has("description")) {
            skillContext.setDescription(yamlJson.getString("description"));
        }
        if (yamlJson.has("references")) {
            JSONArray refs = yamlJson.getJSONArray("references");
            List<String> references = new ArrayList<>();
            for (int i = 0; i < refs.length(); i++) {
                references.add(refs.getString(i));
            }
            skillContext.setReferences(references);
        }
    }

    /**
     * 解析 execution_hints（与 Native parse_execution_hints 格式一致）
     * 
     * Native agent_loop.cpp:750-802
     */
    private void parseExecutionHints(SkillContext skillContext, JSONObject executionHints) throws Exception {
        if (!executionHints.has("steps")) {
            return;
        }

        JSONArray steps = executionHints.getJSONArray("steps");
        
        for (int i = 0; i < steps.length(); i++) {
            JSONObject stepJson = steps.getJSONObject(i);
            ExecutionHint hint = parseStepJson(i, stepJson);
            if (hint != null) {
                skillContext.addExecutionHint(hint);
            }
        }
    }

    /**
     * 解析单个 step（与 Native SkillStepHint 字段一致）
     */
    private ExecutionHint parseStepJson(int index, JSONObject stepJson) throws Exception {
        ExecutionHint hint = new ExecutionHint();
        hint.setIndex(index);

        if (stepJson.has("page")) {
            hint.setPage(stepJson.getString("page"));
        }
        if (stepJson.has("activity")) {
            hint.setActivity(stepJson.getString("activity"));
        }
        if (stepJson.has("target")) {
            hint.setTargetHint(stepJson.getString("target"));
        }
        if (stepJson.has("aliases")) {
            JSONArray aliases = stepJson.getJSONArray("aliases");
            List<String> aliasList = new ArrayList<>();
            for (int i = 0; i < aliases.length(); i++) {
                aliasList.add(aliases.getString(i));
            }
            hint.setAliases(aliasList);
        }
        if (stepJson.has("region")) {
            hint.setRegion(stepJson.getString("region"));
        }
        if (stepJson.has("action")) {
            hint.setAction(stepJson.getString("action"));
        }
        if (stepJson.has("maxAttempts")) {
            hint.setMaxAttempts(stepJson.getInt("maxAttempts"));
        }
        if (stepJson.has("max_attempts")) {
            hint.setMaxAttempts(stepJson.getInt("max_attempts"));
        }
        if (stepJson.has("phase")) {
            String phase = stepJson.getString("phase");
            hint.setReadout(phase.equals("readout"));
        }
        if (stepJson.has("goalReached")) {
            hint.setReadout(stepJson.getBoolean("goalReached"));
        }
        if (stepJson.has("action")) {
            String action = stepJson.getString("action");
            if (action.equals("read") || action.equals("readout")) {
                hint.setReadout(true);
            }
        }

        return hint;
    }

    /**
     * 校验 SkillContext（确保 Native 能正确解析）
     */
    private void validateSkillContext(SkillContext skillContext) {
        List<String> errors = new ArrayList<>();

        if (TextUtils.isEmpty(skillContext.getName())) {
            errors.add("name is required");
        }
        if (TextUtils.isEmpty(skillContext.getDescription())) {
            errors.add("description is recommended");
        }

        List<ExecutionHint> hints = skillContext.getExecutionHints();
        for (int i = 0; i < hints.size(); i++) {
            ExecutionHint hint = hints.get(i);
            if (TextUtils.isEmpty(hint.getAction())) {
                errors.add("step[" + i + "].action is required");
            }
            if (TextUtils.isEmpty(hint.getTargetHint()) && 
                (hint.getAliases() == null || hint.getAliases().isEmpty())) {
                errors.add("step[" + i + "].target or aliases is required");
            }
        }

        if (!errors.isEmpty()) {
            skillContext.setValidationErrors(errors);
            AgentLogs.warn(TAG, "skill_validation_failed", 
                "skill=" + skillContext.getName() + " errors=" + errors.toString());
        } else {
            skillContext.setValid(true);
        }
    }

    private SkillContext createInvalidContext(String skillName, String error) {
        SkillContext context = new SkillContext();
        context.setName(skillName);
        context.setValid(false);
        List<String> errors = new ArrayList<>();
        errors.add(error);
        context.setValidationErrors(errors);
        return context;
    }

    public void preloadSkills(String... skillNames) {
        if (skillNames == null) return;
        
        for (String name : skillNames) {
            SkillContext context = resolve(name);
            if (context != null && context.isValid()) {
                AgentLogs.info(TAG, "skill_preloaded", "skill=" + name);
            } else {
                AgentLogs.warn(TAG, "skill_preload_failed", "skill=" + name);
            }
        }
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public Map<String, SkillContext> getSkillCache() {
        return skillCache;
    }
}
