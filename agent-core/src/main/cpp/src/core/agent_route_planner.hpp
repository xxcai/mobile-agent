#pragma once

// Internal AgentLoop implementation chunk. Included only by agent_loop.cpp.

nlohmann::json build_step_json(const SkillStepHint& step) {
    nlohmann::json json = nlohmann::json::object();
    if (!step.page.empty()) {
        json["page"] = step.page;
    }
    if (!step.activity.empty()) {
        json["activity"] = step.activity;
    }
    if (!step.target.empty()) {
        json["target"] = step.target;
    }
    if (!step.aliases.empty()) {
        json["aliases"] = build_string_array_json(step.aliases);
    }
    if (!step.region.empty()) {
        json["region"] = step.region;
    }
    if (!step.anchor_type.empty()) {
        json["anchor_type"] = step.anchor_type;
    }
    if (!step.container_role.empty()) {
        json["container_role"] = step.container_role;
    }
    if (!step.action.empty()) {
        json["action"] = step.action;
    }
    if (step.max_attempts > 0) {
        json["maxAttempts"] = step.max_attempts;
    }
    if (step.readout) {
        json["readout"] = true;
    }
    return json;
}

std::string describe_step(const SkillStepHint& step) {
    std::ostringstream stream;
    if (!step.action.empty()) {
        stream << step.action;
    }
    if (!step.target.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "'" << step.target << "'";
    }
    if (!step.aliases.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "(aliases:";
        for (size_t i = 0; i < std::min<size_t>(step.aliases.size(), 3); ++i) {
            stream << (i == 0 ? " " : ", ") << step.aliases[i];
        }
        if (step.aliases.size() > 3) {
            stream << ", ...";
        }
        stream << ")";
    }
    if (!step.region.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "[region=" << step.region << "]";
    }
    if (!step.anchor_type.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "[anchor_type=" << step.anchor_type << "]";
    }
    if (!step.container_role.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "[container_role=" << step.container_role << "]";
    }
    if (step.max_attempts > 0) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "[maxAttempts=" << step.max_attempts << "]";
    }
    if (!step.page.empty() || !step.activity.empty()) {
        if (stream.tellp() > 0) {
            stream << " on ";
        }
        stream << (!step.page.empty() ? step.page : step.activity);
    }
    if (step.readout) {
        if (stream.tellp() > 0) {
            stream << " -> ";
        }
        stream << "readout";
    }
    return truncate_runtime_text(stream.str(), EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
}

std::optional<ParsedExecutionHints> parse_execution_hints(const std::vector<SkillMetadata>& selected_skills) {
    for (const auto& skill : selected_skills) {
        if (!skill.execution_hints.is_object()) {
            continue;
        }
        ParsedExecutionHints parsed;
        parsed.kind = first_string_value(skill.execution_hints, {"kind"});
        const auto& steps = skill.execution_hints["steps"];
        if (!steps.is_array()) {
            continue;
        }
        for (const auto& step_json : steps) {
            if (!step_json.is_object()) {
                continue;
            }
            SkillStepHint step;
            step.page = first_string_value(step_json,
                    {"page", "pageContains", "summaryContains", "pageSummaryContains"});
            step.activity = first_string_value(step_json,
                    {"activity", "activityContains", "activityClassNameContains"});
            step.target = first_string_value(step_json,
                    {"target", "targetHint", "targetContains", "label"});
            step.aliases = string_array_values(step_json,
                    {"aliases", "alias", "targets", "targetAliases"});
            step.region = first_string_value(step_json,
                    {"region", "preferredRegion", "anchorRegion"});
            step.anchor_type = first_string_value(step_json,
                    {"anchor_type", "anchorType", "entryType"});
            step.container_role = first_string_value(step_json,
                    {"container_role", "containerRole"});
            step.action = first_string_value(step_json,
                    {"action", "gesture", "type"});
            step.max_attempts = step_json.value("maxAttempts",
                    step_json.value("max_attempts", 0));
            const std::string phase = first_string_value(step_json, {"phase"});
            step.readout = step_json.value("goalReached", false)
                    || phase == "readout"
                    || step.action == "read"
                    || step.action == "readout";
            if (step.page.empty() && step.activity.empty() && step.target.empty()
                    && step.aliases.empty() && step.region.empty()
                    && step.anchor_type.empty() && step.container_role.empty()
                    && step.action.empty() && !step.readout) {
                continue;
            }
            parsed.steps.push_back(std::move(step));
        }
        if (!parsed.steps.empty()) {
            return parsed;
        }
    }
    return std::nullopt;
}

bool goal_requires_readout(const std::string& goal) {
    static const std::vector<std::string> readout_terms = {
        u8"\u67e5\u770b", u8"\u9605\u8bfb", u8"\u603b\u7ed3", u8"\u5185\u5bb9", u8"\u8be6\u60c5",
        "read", "summary", "summarize", "content", "details", "list"
    };
    for (const auto& term : readout_terms) {
        if (contains_runtime_match(goal, term)) {
            return true;
        }
    }
    return false;
}

NavigationPlan build_navigation_plan(const std::optional<ParsedExecutionHints>& hints) {
    NavigationPlan plan;
    if (!hints || hints->steps.empty()) {
        return plan;
    }
    for (const auto& hint_step : hints->steps) {
        NavigationStep step;
        step.page = hint_step.page;
        step.activity = hint_step.activity;
        step.target = hint_step.target;
        step.aliases = hint_step.aliases;
        step.action = hint_step.action;
        step.readout = hint_step.readout;
        plan.steps.push_back(std::move(step));
    }
    return plan;
}

const SkillMetadata* pick_route_skill(const std::vector<SkillMetadata>& selected_skills) {
    if (selected_skills.empty()) {
        return nullptr;
    }
    for (const auto& skill : selected_skills) {
        if (skill.execution_hints.is_object()) {
            return &skill;
        }
    }
    return &selected_skills.front();
}

std::string build_skill_readout_contract(const SkillMetadata* skill) {
    if (!skill || !skill->execution_hints.is_object()) {
        return "";
    }

    static const std::vector<std::string> contract_keys = {
        "readout_contract",
        "readoutContract",
        "readout_schema",
        "readoutSchema",
        "readout_rules",
        "readoutRules"
    };
    for (const auto& key : contract_keys) {
        if (!skill->execution_hints.contains(key)) {
            continue;
        }
        const auto& contract = skill->execution_hints[key];
        if (contract.is_string()) {
            return truncate_runtime_text(contract.get<std::string>(), 1200);
        }
        if (contract.is_object() || contract.is_array()) {
            return truncate_runtime_text(contract.dump(), 1200);
        }
    }
    return "";
}

StopConditionSpec build_stop_condition_spec(const std::string& objective,
                                            const SkillMetadata* route_skill,
                                            const std::optional<ParsedExecutionHints>& hints) {
    StopConditionSpec spec;
    spec.requires_readout = goal_requires_readout(objective);

    if (hints && !hints->steps.empty()) {
        const SkillStepHint* terminal_step = nullptr;
        for (const auto& step : hints->steps) {
            const bool is_terminal = step.readout
                    || (&step == &hints->steps.back());
            if (!is_terminal) {
                continue;
            }
            terminal_step = &step;
            break;
        }
        if (!terminal_step) {
            terminal_step = &hints->steps.back();
        }

        if (terminal_step) {
            if (!terminal_step->page.empty()) {
                spec.page_predicates.push_back(terminal_step->page);
            }
            if (!terminal_step->activity.empty()) {
                spec.page_predicates.push_back(terminal_step->activity);
            }
            if (!terminal_step->target.empty()) {
                spec.content_predicates.push_back(terminal_step->target);
            }
            for (const auto& alias : terminal_step->aliases) {
                if (!alias.empty()) {
                    spec.content_predicates.push_back(alias);
                }
            }
            spec.requires_readout = spec.requires_readout || terminal_step->readout;
        }
    }

    if (route_skill && route_skill->execution_hints.is_object()
            && route_skill->execution_hints.contains("stop_condition")
            && route_skill->execution_hints["stop_condition"].is_object()) {
        const auto& stop = route_skill->execution_hints["stop_condition"];
        auto merge_values = [&](std::vector<std::string>& target,
                                const std::vector<std::string>& values) {
            for (const auto& value : values) {
                if (value.empty()) {
                    continue;
                }
                if (std::find(target.begin(), target.end(), value) == target.end()) {
                    target.push_back(value);
                }
            }
        };
        merge_values(spec.page_predicates, string_array_values(stop,
                {"page_predicates", "pagePredicates", "pages"}));
        merge_values(spec.content_predicates, string_array_values(stop,
                {"content_predicates", "contentPredicates", "content"}));
        merge_values(spec.success_signals, string_array_values(stop,
                {"success_signals", "successSignals", "success"}));
        merge_values(spec.failure_signals, string_array_values(stop,
                {"failure_signals", "failureSignals", "failure"}));
        if (stop.contains("requires_readout") && stop["requires_readout"].is_boolean()) {
            spec.requires_readout = stop["requires_readout"].get<bool>();
        }
    }

    if (spec.page_predicates.empty()) {
        if (hints && !hints->steps.empty()) {
            const auto& last = hints->steps.back();
            if (!last.page.empty()) {
                spec.page_predicates.push_back(last.page);
            }
            if (!last.activity.empty()) {
                spec.page_predicates.push_back(last.activity);
            }
        }
    }
    if (spec.page_predicates.empty()) {
        spec.page_predicates.push_back(objective);
    }
    if (spec.failure_signals.empty()) {
        spec.failure_signals = {"failed", "error", "denied", "not found"};
    }
    return spec;
}

IntentRoute build_intent_route(const std::string& objective,
                               const std::vector<SkillMetadata>& selected_skills,
                               const std::optional<ParsedExecutionHints>& hints) {
    IntentRoute route;
    const SkillMetadata* route_skill = pick_route_skill(selected_skills);
    if (route_skill) {
        route.selected_skill = route_skill->name;
    }

    route.stop_condition = build_stop_condition_spec(objective, route_skill, hints);
    route.readout_goal = truncate_runtime_text(objective, 160);
    route.escalation_policy = "on_ambiguity_or_no_progress";
    route.task_type = route.stop_condition.requires_readout ? "navigate_and_read" : "navigate_and_trigger";

    if (hints && !hints->steps.empty()) {
        for (const auto& step : hints->steps) {
            if (!step.readout) {
                continue;
            }
            if (!step.page.empty()) {
                route.navigation_goal = step.page;
                break;
            }
            if (!step.activity.empty()) {
                route.navigation_goal = step.activity;
                break;
            }
            if (!step.target.empty()) {
                route.navigation_goal = step.target;
                break;
            }
        }
        if (route.navigation_goal.empty()) {
            const auto& first = hints->steps.front();
            route.navigation_goal = !first.page.empty()
                    ? first.page
                    : (!first.activity.empty() ? first.activity : first.target);
        }
    }

    if (route.navigation_goal.empty()) {
        route.navigation_goal = truncate_runtime_text(objective, 120);
    }

    return route;
}

ExecutionState initialize_execution_state(const std::string& objective,
                                          const std::vector<SkillMetadata>& selected_skills) {
    ExecutionState state;
    state.goal = truncate_runtime_text(objective, EXECUTION_STATE_MAX_OBJECTIVE_CHARS);
    state.selected_skills.reserve(selected_skills.size());
    for (const auto& skill : selected_skills) {
        state.selected_skills.push_back(skill.name);
    }
    state.active_hints = parse_execution_hints(selected_skills);
    state.navigation_plan = build_navigation_plan(state.active_hints);
    const SkillMetadata* route_skill = pick_route_skill(selected_skills);
    state.intent_route = build_intent_route(objective, selected_skills, state.active_hints);
    state.route_ready = true;
    state.readout_context.objective = truncate_runtime_text(objective, 220);
    state.readout_context.readout_goal = state.intent_route.readout_goal;
    state.readout_context.selected_skill = state.intent_route.selected_skill;
    state.readout_context.readout_contract = build_skill_readout_contract(route_skill);

    if (state.active_hints && !state.active_hints->empty()) {
        state.mode = "planned_fast_execute";
        state.pending_step_index = 0;
        state.pending_step = build_step_json(state.active_hints->steps.front());
    }
    return state;
}

}  // namespace

namespace {


