package com.hh.agent.android.harness;

import java.util.ArrayList;
import java.util.List;

/**
 * SkillContext - Skill 上下文数据结构
 * 
 * 用途：
 * 1. 存储 YAML frontmatter 解析结果
 * 2. 用于格式校验和结果记录
 * 3. 不独立执行，依赖 Native AgentLoop
 */
public class SkillContext {
    private String name;
    private String description;
    private boolean always;
    private String primaryShortcut;
    private List<String> dependencies;
    private List<String> requiredParams;
    
    private String executionMode;
    private List<ExecutionHint> executionHints;
    private List<String> phases;

    private boolean valid;
    private List<String> validationErrors;
    private String markdownContent;
    
    private List<String> references;
    private String templatePath;
    private String styleGuidePath;
    private List<String> variables;
    
    private List<String> discoveryQuestions;
    private List<String> constraintQuestions;

    public SkillContext() {
        this.dependencies = new ArrayList<>();
        this.requiredParams = new ArrayList<>();
        this.executionHints = new ArrayList<>();
        this.phases = new ArrayList<>();
        this.validationErrors = new ArrayList<>();
        this.references = new ArrayList<>();
        this.variables = new ArrayList<>();
        this.discoveryQuestions = new ArrayList<>();
        this.constraintQuestions = new ArrayList<>();
        this.valid = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAlways() {
        return always;
    }

    public void setAlways(boolean always) {
        this.always = always;
    }

    public String getPrimaryShortcut() {
        return primaryShortcut;
    }

    public void setPrimaryShortcut(String primaryShortcut) {
        this.primaryShortcut = primaryShortcut;
    }

    public String[] getDependencies() {
        return dependencies.toArray(new String[0]);
    }

    public void addDependency(String dependency) {
        if (dependency != null && !dependencies.contains(dependency)) {
            dependencies.add(dependency);
        }
    }

    public String[] getRequiredParams() {
        return requiredParams.toArray(new String[0]);
    }

    public void addRequiredParam(String param) {
        if (param != null && !requiredParams.contains(param)) {
            requiredParams.add(param);
        }
    }

    public boolean isDeterministic() {
        return executionHints != null && !executionHints.isEmpty();
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> errors) {
        this.validationErrors = errors != null ? errors : new ArrayList<>();
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public void setMarkdownContent(String markdownContent) {
        this.markdownContent = markdownContent;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public List<ExecutionHint> getExecutionHints() {
        return executionHints;
    }

    public void setExecutionHints(List<ExecutionHint> hints) {
        this.executionHints = hints;
    }

    public void addExecutionHint(ExecutionHint hint) {
        if (hint != null) {
            executionHints.add(hint);
        }
    }

    public List<String> getPhases() {
        return phases;
    }

    public void setPhases(List<String> phases) {
        this.phases = phases;
    }

    public void addPhase(String phase) {
        if (phase != null && !phases.contains(phase)) {
            phases.add(phase);
        }
    }

    public List<String> getReferences() {
        return references;
    }

    public void setReferences(List<String> references) {
        this.references = references != null ? references : new ArrayList<>();
    }

    public void addReference(String reference) {
        if (reference != null && !references.contains(reference)) {
            references.add(reference);
        }
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getStyleGuidePath() {
        return styleGuidePath;
    }

    public void setStyleGuidePath(String styleGuidePath) {
        this.styleGuidePath = styleGuidePath;
    }

    public List<String> getVariables() {
        return variables;
    }

    public void setVariables(List<String> variables) {
        this.variables = variables != null ? variables : new ArrayList<>();
    }

    public void addVariable(String variable) {
        if (variable != null && !variables.contains(variable)) {
            variables.add(variable);
        }
    }

    public List<String> getDiscoveryQuestions() {
        return discoveryQuestions;
    }

    public void setDiscoveryQuestions(List<String> questions) {
        this.discoveryQuestions = questions != null ? questions : new ArrayList<>();
    }

    public void addDiscoveryQuestion(String question) {
        if (question != null && !discoveryQuestions.contains(question)) {
            discoveryQuestions.add(question);
        }
    }

    public List<String> getConstraintQuestions() {
        return constraintQuestions;
    }

    public void setConstraintQuestions(List<String> questions) {
        this.constraintQuestions = questions != null ? questions : new ArrayList<>();
    }

    public void addConstraintQuestion(String question) {
        if (question != null && !constraintQuestions.contains(question)) {
            constraintQuestions.add(question);
        }
    }

    public String toSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(name);
        sb.append(", valid=").append(valid);
        sb.append(", steps=").append(executionHints.size());
        if (!validationErrors.isEmpty()) {
            sb.append(", errors=").append(validationErrors.size());
        }
        return sb.toString();
    }
}
