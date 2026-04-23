#pragma once

#include "icraw/types.hpp"

#include <nlohmann/json.hpp>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace icraw {

// Internal Route / Navigation / Readout runtime types.
// These are implementation details of AgentLoop and are intentionally kept out
// of the public SDK type surface.

struct StopConditionSpec {
    std::vector<std::string> page_predicates;
    std::vector<std::string> content_predicates;
    std::vector<std::string> success_signals;
    std::vector<std::string> failure_signals;
    bool requires_readout = true;
};

struct IntentRoute {
    std::string task_type;         // navigate_and_read | navigate_and_trigger | in_page_read ...
    std::string selected_skill;
    std::string navigation_goal;
    std::string readout_goal;
    std::string escalation_policy;
    StopConditionSpec stop_condition;
};

struct NavigationStep {
    std::string page;
    std::string activity;
    std::string target;
    std::vector<std::string> aliases;
    std::string action;
    bool readout = false;
};

struct NavigationPlan {
    std::vector<NavigationStep> steps;
};

struct NavigationCheckpoint {
    int current_step_index = -1;
    int stagnant_rounds = 0;
    std::string last_activity;
    std::string last_summary;
    std::string last_fingerprint;
};

struct NavigationEscalation {
    std::string reason;
    std::string detail;
};

struct RouteRequestContext {
    std::string objective;
    std::string skill_summary;
};

struct ObservationFingerprint {
    std::string value;
    std::string activity;
    std::string summary;
    std::vector<std::string> actionable_labels;
    std::vector<std::string> conflict_codes;
};

struct NavigationTraceSummary {
    std::string current_page;
    int current_step_index = -1;
    std::string pending_target;
    std::string latest_action_result;
    std::string latest_observation_summary;
    std::string summary;
};

struct NavigationEscalationRequest {
    std::string objective;
    IntentRoute intent_route;
    NavigationCheckpoint checkpoint;
    NavigationEscalation escalation;
    std::string pending_step_summary;
    std::string observation_summary;
    std::string latest_action_result;
    std::string trace_summary;
};

struct ReadoutRequestContext {
    std::string objective;
    std::string readout_goal;
    std::string current_page;
    std::string selected_skill;
    std::string readout_contract;
};

struct SkillStepHint {
    std::string page;
    std::string activity;
    std::string target;
    std::vector<std::string> aliases;
    std::string region;
    std::string anchor_type;
    std::string container_role;
    std::string fallback_strategy;
    std::string action;
    int max_attempts = 0;
    bool readout = false;
};

struct ParsedExecutionHints {
    std::string kind;
    std::vector<SkillStepHint> steps;

    bool empty() const {
        return steps.empty();
    }
};

struct ObservationCandidate {
    std::string label;
    std::string match_text;
    std::string source;
    std::string resource_id;
    std::string bounds;
    std::string region;
    std::string anchor_type;
    std::string container_role;
    bool clickable = false;
    bool container_clickable = false;
    bool badge_like = false;
    bool numeric_like = false;
    bool decorative_like = false;
    bool repeat_group = false;
    double score = 0.0;
};

struct ObservationConflict {
    std::string code;
    std::string severity;
    std::string message;
    std::string bounds;
};

struct ObservationSnapshot {
    std::string activity;
    std::string source;
    std::string visual_mode;
    std::string summary;
    std::string snapshot_id;
    int screen_width = 0;
    int screen_height = 0;
    bool has_warning_conflict = false;
    size_t warning_conflict_count = 0;
    std::vector<ObservationConflict> warning_conflicts;
    std::vector<ObservationCandidate> actionable_candidates;
    std::string visible_text;
};

struct CanonicalCandidate {
    ObservationCandidate aggregate;
    std::vector<ObservationCandidate> members;
    std::string display_label;
    std::string tap_bounds;
    std::string source_set;
    std::string risk_flags;
    int actionability_rank = 0;
    size_t member_count = 0;
};

struct NavigationAttemptCacheEntry {
    std::string step_target;
    std::string selected_candidate;
    std::string tap_bounds;
    int attempt_count = 0;
};

struct ExecutionState {
    std::string goal;
    std::string mode = "free_llm";
    std::string phase = "discovery";
    std::string current_page;
    std::vector<std::string> completed_steps;
    nlohmann::json pending_step;
    std::string latest_observation_summary;
    std::string latest_navigation_observation_summary;
    std::string latest_canonical_candidate_summary;
    std::string latest_readout_observation_summary;
    std::string latest_action_result;
    std::string last_observation_target_hint;
    std::string last_gesture_target;
    std::vector<std::string> selected_skills;
    std::optional<ParsedExecutionHints> active_hints;
    int pending_step_index = -1;
    int awaiting_step_confirmation_index = -1;
    std::string awaiting_confirmation_target;
    std::string awaiting_confirmation_previous_page;
    int awaiting_confirmation_retry_count = 0;
    bool goal_reached = false;
    bool context_reset = false;
    bool route_ready = false;
    bool route_resolved_by_llm = false;
    IntentRoute intent_route;
    NavigationPlan navigation_plan;
    NavigationCheckpoint navigation_checkpoint;
    NavigationEscalation latest_escalation;
    RouteRequestContext route_request_context;
    ObservationFingerprint latest_observation_fingerprint;
    NavigationTraceSummary latest_navigation_trace;
    ReadoutRequestContext readout_context;
    std::unordered_map<std::string, NavigationAttemptCacheEntry> navigation_attempt_cache;
    std::unordered_map<int, int> step_action_attempt_counts;
    int readout_retry_count = 0;
};

} // namespace icraw
