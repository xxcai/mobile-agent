#pragma once

// Internal AgentLoop implementation chunk. Included only by agent_loop.cpp.

void normalize_tool_call_arguments(ToolCall& tool_call);

std::vector<ToolCall> enrich_tool_calls_for_execution(const std::vector<ToolCall>& tool_calls,
                                                      const ExecutionState& state,
                                                      const std::vector<Message>& messages) {
    std::vector<ToolCall> enriched = tool_calls;
    const std::string detail_mode = determine_observation_detail_mode(state, messages);
    for (auto& tool_call : enriched) {
        normalize_tool_call_arguments(tool_call);
        if (tool_call.name != "android_view_context_tool" || !tool_call.arguments.is_object()) {
            continue;
        }
        tool_call.arguments["__detailMode"] = detail_mode;
        if (state.pending_step.is_object()
                && state.pending_step.contains("target")
                && state.pending_step["target"].is_string()) {
            const std::string pending_target = trim_whitespace(
                    state.pending_step["target"].get<std::string>());
            const std::string existing_target = trim_whitespace(
                    first_string_value(tool_call.arguments, {"targetHint", "target", "pageHint"}));
            if (!pending_target.empty() && existing_target.empty()) {
                tool_call.arguments["targetHint"] = pending_target;
            }
        }
        ICRAW_LOG_INFO("[AgentLoop][observation_detail_mode] mode={} phase={}", detail_mode, state.phase);
    }
    return enriched;
}

bool parse_bounds_center(const std::string& bounds, int& center_x, int& center_y) {
    int left = 0;
    int top = 0;
    int right = 0;
    int bottom = 0;
    if (std::sscanf(bounds.c_str(), "[%d,%d][%d,%d]", &left, &top, &right, &bottom) != 4) {
        return false;
    }
    center_x = (left + right) / 2;
    center_y = (top + bottom) / 2;
    return true;
}

struct BoundsRect {
    int left = 0;
    int top = 0;
    int right = 0;
    int bottom = 0;
};

bool parse_bounds_rect(const std::string& bounds, BoundsRect& rect) {
    return std::sscanf(bounds.c_str(),
            "[%d,%d][%d,%d]",
            &rect.left,
            &rect.top,
            &rect.right,
            &rect.bottom) == 4;
}

bool bounds_overlap(const std::string& left_bounds, const std::string& right_bounds) {
    BoundsRect left;
    BoundsRect right;
    if (!parse_bounds_rect(left_bounds, left) || !parse_bounds_rect(right_bounds, right)) {
        return false;
    }
    return std::max(left.left, right.left) < std::min(left.right, right.right)
            && std::max(left.top, right.top) < std::min(left.bottom, right.bottom);
}

bool is_low_risk_warning_conflict(const ObservationConflict& conflict) {
    return conflict.code == "vision_compaction_drop_summary";
}

bool is_stable_non_vision_candidate(const ObservationCandidate& candidate) {
    return candidate.source == "native" || candidate.source == "fused";
}

bool is_relaxable_vision_only_conflict(const ObservationConflict& conflict,
                                       const ObservationCandidate& candidate) {
    if (conflict.code != "vision_only_candidate") {
        return false;
    }
    if (!is_stable_non_vision_candidate(candidate)) {
        return false;
    }
    if (!conflict.bounds.empty() && !candidate.bounds.empty()
            && bounds_overlap(conflict.bounds, candidate.bounds)) {
        return false;
    }
    return true;
}

bool should_ignore_exact_target_vision_only_conflict(const ObservationConflict& conflict,
                                                     const SkillStepHint& step,
                                                     const ObservationCandidate& candidate) {
    if (conflict.code != "vision_only_candidate") {
        return false;
    }
    if (!is_stable_non_vision_candidate(candidate)) {
        return false;
    }
    if (!candidate_label_matches_step_target_exactly(candidate, step)) {
        return false;
    }
    // Canonical candidates may inherit decorative/repeat hints from sibling icon/text members in the same
    // clickable container. Keep blocking truly risky badge/numeric targets, but allow exact stable entries
    // to ignore unrelated vision-only warnings.
    if (candidate.badge_like || candidate.numeric_like) {
        return false;
    }
    return candidate.clickable || candidate.container_clickable;
}

bool is_target_related_warning_conflict(const ObservationConflict& conflict,
                                        const SkillStepHint& step,
                                        const ObservationCandidate& candidate) {
    if (!conflict.bounds.empty() && !candidate.bounds.empty()
            && bounds_overlap(conflict.bounds, candidate.bounds)) {
        return true;
    }
    const std::string conflict_text =
            trim_whitespace(conflict.code + " " + conflict.message);
    if (!step.target.empty() && contains_runtime_match(conflict_text, step.target)) {
        return true;
    }
    for (const auto& alias : step.aliases) {
        if (contains_runtime_match(conflict_text, alias)) {
            return true;
        }
    }
    if (!candidate.label.empty() && contains_runtime_match(conflict_text, candidate.label)) {
        return true;
    }
    if (!candidate.match_text.empty() && contains_runtime_match(conflict_text, candidate.match_text)) {
        return true;
    }
    return false;
}

bool should_block_fast_execute_on_conflicts(const ObservationSnapshot& snapshot,
                                            const SkillStepHint& step,
                                            const ObservationCandidate& candidate,
                                            std::string& reason) {
    if (!snapshot.has_warning_conflict || snapshot.warning_conflicts.empty()) {
        return false;
    }
    size_t non_low_risk_warning_count = 0;
    for (const auto& conflict : snapshot.warning_conflicts) {
        if (is_low_risk_warning_conflict(conflict)) {
            continue;
        }
        if (is_relaxable_vision_only_conflict(conflict, candidate)) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_conflict_ignored] code={} candidate={} candidate_source={} reason=stable_non_vision_candidate_non_overlapping",
                    conflict.code,
                    candidate.label,
                    candidate.source);
            continue;
        }
        if (should_ignore_exact_target_vision_only_conflict(conflict, step, candidate)) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_conflict_ignored] code={} candidate={} candidate_source={} reason=stable_exact_target_candidate",
                    conflict.code,
                    candidate.label,
                    candidate.source);
            continue;
        }
        non_low_risk_warning_count++;
        if (is_target_related_warning_conflict(conflict, step, candidate)) {
            reason = conflict.code.empty()
                    ? "warning_conflict_target_related"
                    : ("warning_conflict_" + conflict.code);
            return true;
        }
    }
    if (non_low_risk_warning_count >= 2) {
        reason = "warning_conflict_multiple";
        return true;
    }
    return false;
}

std::string normalize_runtime_key(const std::string& text) {
    return normalize_for_runtime_match(text);
}

std::string activity_simple_name(const std::string& activity) {
    const std::string trimmed = trim_whitespace(activity);
    const size_t last_dot = trimmed.find_last_of('.');
    if (last_dot == std::string::npos) {
        return trimmed;
    }
    return trimmed.substr(last_dot + 1);
}

bool matches_activity_name(const std::string& actual_activity,
                           const std::string& expected_activity) {
    const std::string actual_trimmed = trim_whitespace(actual_activity);
    const std::string expected_trimmed = trim_whitespace(expected_activity);
    if (actual_trimmed.empty() || expected_trimmed.empty()) {
        return false;
    }

    const std::string normalized_actual = normalize_runtime_key(actual_trimmed);
    const std::string normalized_expected = normalize_runtime_key(expected_trimmed);
    if (normalized_actual == normalized_expected) {
        return true;
    }

    const std::string normalized_actual_simple = normalize_runtime_key(activity_simple_name(actual_trimmed));
    const std::string normalized_expected_simple = normalize_runtime_key(activity_simple_name(expected_trimmed));
    return !normalized_actual_simple.empty()
            && normalized_actual_simple == normalized_expected_simple;
}

int candidate_term_match_strength(const ObservationCandidate& candidate,
                                  const std::string& term,
                                  bool is_primary) {
    const std::string label = normalize_runtime_key(candidate.label);
    const std::string match_text = normalize_runtime_key(candidate.match_text);
    const std::string normalized = normalize_runtime_key(term);
    if (normalized.empty()) {
        return 0;
    }

    const int exact_label = is_primary ? 120 : 90;
    const int contains_label = is_primary ? 90 : 55;
    const int contains_text = is_primary ? 55 : 0;
    if (!label.empty() && label == normalized) {
        return exact_label;
    }
    if (!label.empty() && label.find(normalized) != std::string::npos) {
        return contains_label;
    }
    if (contains_text > 0 && !match_text.empty() && match_text.find(normalized) != std::string::npos) {
        return contains_text;
    }
    return 0;
}

int candidate_match_strength(const ObservationCandidate& candidate,
                             const SkillStepHint& step) {
    int best = candidate_term_match_strength(candidate, step.target, true);
    for (const auto& alias : step.aliases) {
        best = std::max(best, candidate_term_match_strength(candidate, alias, false));
    }
    return best;
}

bool step_prefers_corner_entry(const SkillStepHint& step) {
    if (contains_runtime_match(step.region, "top_left")
            || contains_runtime_match(step.region, "header_left")
            || contains_runtime_match(step.anchor_type, "profile_entry")) {
        return true;
    }

    static const std::vector<std::string> keywords = {
        u8"\u5de6\u4e0a\u89d2", u8"\u5934\u50cf", u8"\u4e2a\u4eba\u5934\u50cf", u8"\u6211\u7684\u5934\u50cf",
        u8"\u4e2a\u4eba\u4e2d\u5fc3", u8"\u6211\u7684", u8"\u6211", "avatar", "profile", "me", "mine"
    };
    for (const auto& keyword : keywords) {
        if ((!step.target.empty() && contains_runtime_match(step.target, keyword))) {
            return true;
        }
        for (const auto& alias : step.aliases) {
            if (contains_runtime_match(alias, keyword)) {
                return true;
            }
        }
    }
    return false;
}

std::string normalize_region_label(const std::string& region) {
    std::string normalized;
    normalized.reserve(region.size());
    for (unsigned char ch : region) {
        if (std::isalnum(ch)) {
            normalized.push_back(static_cast<char>(std::tolower(ch)));
        } else if (ch == '-' || ch == ' ' || ch == '.') {
            normalized.push_back('_');
        } else if (ch >= 0x80) {
            normalized.push_back(static_cast<char>(ch));
        }
    }

    while (!normalized.empty() && normalized.front() == '_') {
        normalized.erase(normalized.begin());
    }
    while (!normalized.empty() && normalized.back() == '_') {
        normalized.pop_back();
    }
    while (normalized.find("__") != std::string::npos) {
        normalized.replace(normalized.find("__"), 2, "_");
    }
    return normalized;
}

bool step_prefers_card_title_anchor(const SkillStepHint& step) {
    const std::string normalized_anchor = normalize_region_label(step.anchor_type);
    return normalized_anchor == "card_title"
            || normalized_anchor == "card_header"
            || normalized_anchor == "tile_title";
}

std::string preferred_region_for_step(const SkillStepHint& step) {
    const std::string explicit_region = normalize_region_label(step.region);
    if (!explicit_region.empty()) {
        return explicit_region;
    }
    if (step_prefers_corner_entry(step)) {
        return "top_left";
    }
    return "";
}

bool candidate_in_region(const ObservationCandidate& candidate,
                         const std::string& region,
                         const ObservationSnapshot& snapshot) {
    const std::string normalized_region = normalize_region_label(region);
    if (normalized_region.empty()) {
        return false;
    }

    if (!candidate.region.empty()
            && normalize_region_label(candidate.region) == normalized_region) {
        return true;
    }

    if (snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
        return false;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(candidate.bounds, center_x, center_y)) {
        return false;
    }

    const double width = static_cast<double>(snapshot.screen_width);
    const double height = static_cast<double>(snapshot.screen_height);
    const double x = static_cast<double>(center_x);
    const double y = static_cast<double>(center_y);

    const bool top = y <= height * 0.30;
    const bool bottom = y >= height * 0.70;
    const bool left = x <= width * 0.42;
    const bool right = x >= width * 0.58;
    const bool middle_x = x > width * 0.32 && x < width * 0.68;
    const bool middle_y = y > height * 0.28 && y < height * 0.72;

    if (normalized_region == "top_left") {
        return top && left;
    }
    if (normalized_region == "top_right") {
        return top && right;
    }
    if (normalized_region == "top_center") {
        return top && middle_x;
    }
    if (normalized_region == "top") {
        return top;
    }
    if (normalized_region == "bottom_left") {
        return bottom && left;
    }
    if (normalized_region == "bottom_right") {
        return bottom && right;
    }
    if (normalized_region == "bottom_center") {
        return bottom && middle_x;
    }
    if (normalized_region == "bottom") {
        return bottom;
    }
    if (normalized_region == "left") {
        return left && middle_y;
    }
    if (normalized_region == "right") {
        return right && middle_y;
    }
    if (normalized_region == "center") {
        return middle_x && middle_y;
    }
    if (normalized_region == "header_left") {
        return top && left;
    }
    if (normalized_region == "header_right") {
        return top && right;
    }
    if (normalized_region == "bottom_tab") {
        return bottom;
    }
    return false;
}

double region_bonus_for_label(const std::string& region) {
    if (region == "top_left") {
        return 36.0;
    }
    if (region == "top_right") {
        return 28.0;
    }
    if (region == "top_center" || region == "bottom_center") {
        return 20.0;
    }
    if (region == "top" || region == "bottom" || region == "left" || region == "right") {
        return 14.0;
    }
    if (region == "center") {
        return 12.0;
    }
    return 18.0;
}

int candidate_source_bonus(const ObservationCandidate& candidate) {
    if (candidate.source == "fused") {
        return 30;
    }
    if (candidate.source == "native") {
        return 24;
    }
    if (candidate.source == "vision_only") {
        return 8;
    }
    return 12;
}

double candidate_anchor_type_bonus(const ObservationCandidate& candidate,
                                   const SkillStepHint& step) {
    if (step.anchor_type.empty() || candidate.anchor_type.empty()) {
        return 0.0;
    }
    if (normalize_region_label(step.anchor_type) == normalize_region_label(candidate.anchor_type)) {
        return 24.0;
    }
    return 0.0;
}

double candidate_container_role_bonus(const ObservationCandidate& candidate,
                                      const SkillStepHint& step) {
    if (step.container_role.empty() || candidate.container_role.empty()) {
        return 0.0;
    }
    if (normalize_region_label(step.container_role) == normalize_region_label(candidate.container_role)) {
        return 18.0;
    }
    return 0.0;
}

bool candidate_anchor_type_is(const ObservationCandidate& candidate, const std::string& expected) {
    return !candidate.anchor_type.empty()
            && normalize_region_label(candidate.anchor_type) == normalize_region_label(expected);
}

bool candidate_container_role_is(const ObservationCandidate& candidate, const std::string& expected) {
    return !candidate.container_role.empty()
            && normalize_region_label(candidate.container_role) == normalize_region_label(expected);
}

bool candidate_label_matches_step_primary_target_exactly(const ObservationCandidate& candidate,
                                                         const SkillStepHint& step) {
    const std::string candidate_label = normalize_runtime_key(candidate.label);
    const std::string primary_target = normalize_runtime_key(step.target);
    return !candidate_label.empty() && !primary_target.empty() && candidate_label == primary_target;
}

bool candidate_has_card_title_semantics(const ObservationCandidate& candidate,
                                        const SkillStepHint& step) {
    if (!step_prefers_card_title_anchor(step)) {
        return false;
    }
    if (candidate_label_matches_step_primary_target_exactly(candidate, step)) {
        return true;
    }
    return candidate_anchor_type_is(candidate, "card_title")
            || candidate_anchor_type_is(candidate, "card_header")
            || candidate_anchor_type_is(candidate, "tile_title");
}

bool candidate_is_card_title_noise(const ObservationCandidate& candidate) {
    return candidate.numeric_like
            || candidate.repeat_group
            || candidate_anchor_type_is(candidate, "primary_action")
            || candidate_anchor_type_is(candidate, "list_item")
            || candidate_anchor_type_is(candidate, "schedule_item")
            || candidate_container_role_is(candidate, "list")
            || candidate_container_role_is(candidate, "schedule_list");
}

bool candidate_has_corner_entry_semantics(const ObservationCandidate& candidate) {
    if (candidate_anchor_type_is(candidate, "profile_entry")) {
        return true;
    }
    static const std::vector<std::string> keywords = {
        u8"\u5de6\u4e0a\u89d2", u8"\u5934\u50cf", u8"\u4e2a\u4eba\u5934\u50cf", u8"\u6211\u7684\u5934\u50cf",
        u8"\u4e2a\u4eba\u4e2d\u5fc3", u8"\u6211\u7684", u8"\u6211", "avatar", "profile", "me", "mine"
    };
    for (const auto& keyword : keywords) {
        if (contains_runtime_match(candidate.label, keyword)
                || contains_runtime_match(candidate.match_text, keyword)) {
            return true;
        }
    }
    return false;
}

bool candidate_is_corner_entry_noise(const ObservationCandidate& candidate) {
    return candidate_anchor_type_is(candidate, "back_entry")
            || candidate_anchor_type_is(candidate, "tab_entry")
            || candidate_anchor_type_is(candidate, "search_entry")
            || candidate_anchor_type_is(candidate, "overflow_entry")
            || candidate_anchor_type_is(candidate, "primary_action")
            || candidate_container_role_is(candidate, "tab_bar")
            || candidate_container_role_is(candidate, "badge")
            || candidate.badge_like;
}

int candidate_actionability_rank(const ObservationCandidate& candidate) {
    int rank = 0;
    if (candidate.clickable) {
        rank += 3;
    }
    if (candidate.container_clickable) {
        rank += 2;
    }
    if (candidate.source == "fused") {
        rank += 2;
    } else if (candidate.source == "native") {
        rank += 1;
    }
    if (candidate_anchor_type_is(candidate, "profile_entry")) {
        rank += 2;
    }
    return rank;
}

bool bounds_are_nearby(const std::string& left_bounds,
                       const std::string& right_bounds,
                       int max_gap_px = 40) {
    BoundsRect left;
    BoundsRect right;
    if (!parse_bounds_rect(left_bounds, left) || !parse_bounds_rect(right_bounds, right)) {
        return false;
    }

    const int horizontal_gap = std::max(0, std::max(right.left - left.right, left.left - right.right));
    const int vertical_gap = std::max(0, std::max(right.top - left.bottom, left.top - right.bottom));
    return horizontal_gap <= max_gap_px && vertical_gap <= max_gap_px;
}

bool candidates_reference_similar_semantics(const ObservationCandidate& left,
                                            const ObservationCandidate& right) {
    const std::string left_label = normalize_runtime_key(left.label);
    const std::string right_label = normalize_runtime_key(right.label);
    const std::string left_resource = normalize_runtime_key(left.resource_id);
    const std::string right_resource = normalize_runtime_key(right.resource_id);
    if (!left_resource.empty() && left_resource == right_resource) {
        return true;
    }
    if (!left_label.empty() && left_label == right_label) {
        return true;
    }
    if (!left.label.empty() && contains_runtime_match(right.match_text, left.label)) {
        return true;
    }
    if (!right.label.empty() && contains_runtime_match(left.match_text, right.label)) {
        return true;
    }
    return false;
}

bool candidates_should_merge_canonically(const ObservationCandidate& left,
                                         const ObservationCandidate& right) {
    const bool spatially_related =
            bounds_overlap(left.bounds, right.bounds)
            || bounds_are_nearby(left.bounds, right.bounds);
    if (!spatially_related) {
        return false;
    }
    if (candidates_reference_similar_semantics(left, right)) {
        return true;
    }

    const bool same_anchor = !left.anchor_type.empty()
            && normalize_region_label(left.anchor_type) == normalize_region_label(right.anchor_type);
    const bool same_container = !left.container_role.empty()
            && normalize_region_label(left.container_role) == normalize_region_label(right.container_role);
    const bool complementary_actionability =
            (left.clickable != right.clickable)
            || (left.container_clickable != right.container_clickable);
    return complementary_actionability && (same_anchor || same_container);
}

int candidate_display_label_score(const ObservationCandidate& candidate) {
    int score = 0;
    const std::string label = trim_whitespace(candidate.label);
    if (!label.empty()) {
        score += static_cast<int>(std::min<size_t>(label.size(), 40));
    }
    if (!candidate.resource_id.empty() && contains_runtime_match(label, candidate.resource_id)) {
        score -= 4;
    }
    if (contains_runtime_match(label, "imageview")
            || contains_runtime_match(label, "textview")
            || contains_runtime_match(label, "view")) {
        score -= 8;
    }
    if (candidate.clickable || candidate.container_clickable) {
        score += 6;
    }
    if (candidate.source == "fused") {
        score += 4;
    } else if (candidate.source == "native") {
        score += 3;
    }
    return score;
}

int candidate_tap_priority_score(const ObservationCandidate& candidate) {
    int score = candidate_actionability_rank(candidate) * 100;
    score += candidate_source_bonus(candidate) * 4;
    score += static_cast<int>(candidate.score * 100.0);
    if (candidate.clickable) {
        score += 18;
    }
    if (candidate.container_clickable) {
        score += 12;
    }
    return score;
}

CanonicalCandidate build_canonical_candidate(const std::vector<ObservationCandidate>& members) {
    CanonicalCandidate canonical;
    canonical.members = members;
    canonical.member_count = members.size();

    if (members.empty()) {
        return canonical;
    }

    size_t label_index = 0;
    size_t tap_index = 0;
    size_t stable_tap_index = 0;
    int best_label_score = std::numeric_limits<int>::min();
    int best_tap_score = std::numeric_limits<int>::min();
    int best_stable_tap_score = std::numeric_limits<int>::min();
    bool has_stable_tap_candidate = false;
    bool has_stable_member = false;
    bool has_vision_only_member = false;
    std::set<std::string> source_set;
    std::vector<std::string> risk_flags;
    std::set<std::string> risk_seen;
    std::vector<std::string> merged_terms;
    std::set<std::string> seen_terms;
    int max_actionability_rank = 0;

    auto append_term = [&](const std::string& term) {
        const std::string trimmed = trim_whitespace(term);
        if (trimmed.empty()) {
            return;
        }
        const std::string normalized = normalize_runtime_key(trimmed);
        if (normalized.empty() || seen_terms.count(normalized) > 0) {
            return;
        }
        seen_terms.insert(normalized);
        merged_terms.push_back(truncate_runtime_text(trimmed, 64));
    };

    auto append_risk_flag = [&](const std::string& flag) {
        if (flag.empty() || risk_seen.count(flag) > 0) {
            return;
        }
        risk_seen.insert(flag);
        risk_flags.push_back(flag);
    };

    for (size_t i = 0; i < members.size(); ++i) {
        const auto& member = members[i];
        const int label_score = candidate_display_label_score(member);
        const int tap_score = candidate_tap_priority_score(member);
        if (label_score > best_label_score) {
            best_label_score = label_score;
            label_index = i;
        }
        if (tap_score > best_tap_score) {
            best_tap_score = tap_score;
            tap_index = i;
        }
        if (is_stable_non_vision_candidate(member) && (member.clickable || member.container_clickable)) {
            has_stable_tap_candidate = true;
            if (tap_score > best_stable_tap_score) {
                best_stable_tap_score = tap_score;
                stable_tap_index = i;
            }
        }
        if (is_stable_non_vision_candidate(member)) {
            has_stable_member = true;
        }
        if (member.source == "vision_only") {
            has_vision_only_member = true;
        }
        if (!member.source.empty()) {
            source_set.insert(member.source);
        }
        append_term(member.label);
        append_term(member.match_text);
        append_term(member.resource_id);
        if (member.badge_like) {
            append_risk_flag("badge_like");
        }
        if (member.numeric_like) {
            append_risk_flag("numeric_like");
        }
        if (member.decorative_like) {
            append_risk_flag("decorative_like");
        }
        if (member.repeat_group) {
            append_risk_flag("repeat_group");
        }
        max_actionability_rank = std::max(max_actionability_rank, candidate_actionability_rank(member));
    }

    if (has_stable_tap_candidate) {
        tap_index = stable_tap_index;
    }

    canonical.aggregate = members[tap_index];
    canonical.aggregate.label = members[label_index].label.empty()
            ? members[tap_index].label
            : members[label_index].label;
    canonical.aggregate.match_text = truncate_runtime_text(join_string_values(merged_terms, " | "), 240);
    canonical.aggregate.resource_id = members[tap_index].resource_id.empty()
            ? members[label_index].resource_id
            : members[tap_index].resource_id;
    if (!is_stable_non_vision_candidate(canonical.aggregate) && has_stable_member && has_vision_only_member) {
        // A canonical cluster can combine a native text label with a visual-only clickable region. Treat the
        // merged concept as fused so exact-label entries can pass the normal stable-candidate safeguards while
        // still using the best tap bounds from the visual/control member.
        canonical.aggregate.source = "fused";
    }
    canonical.aggregate.clickable = false;
    canonical.aggregate.container_clickable = false;
    canonical.aggregate.badge_like = false;
    canonical.aggregate.numeric_like = false;
    canonical.aggregate.decorative_like = false;
    canonical.aggregate.repeat_group = false;
    canonical.aggregate.score = 0.0;
    for (const auto& member : members) {
        canonical.aggregate.clickable = canonical.aggregate.clickable || member.clickable;
        canonical.aggregate.container_clickable =
                canonical.aggregate.container_clickable || member.container_clickable;
        canonical.aggregate.badge_like = canonical.aggregate.badge_like || member.badge_like;
        canonical.aggregate.numeric_like = canonical.aggregate.numeric_like || member.numeric_like;
        canonical.aggregate.decorative_like =
                canonical.aggregate.decorative_like || member.decorative_like;
        canonical.aggregate.repeat_group = canonical.aggregate.repeat_group || member.repeat_group;
        canonical.aggregate.score = std::max(canonical.aggregate.score, member.score);
    }
    canonical.display_label = canonical.aggregate.label.empty()
            ? truncate_runtime_text(canonical.aggregate.match_text, 64)
            : canonical.aggregate.label;
    canonical.tap_bounds = canonical.aggregate.bounds;
    canonical.source_set = join_string_values(
            std::vector<std::string>(source_set.begin(), source_set.end()), ",");
    canonical.risk_flags = join_string_values(risk_flags, ",");
    canonical.actionability_rank = max_actionability_rank;
    return canonical;
}

std::vector<CanonicalCandidate> build_canonical_candidates(
        const std::vector<ObservationCandidate>& candidates) {
    std::vector<CanonicalCandidate> canonical_candidates;
    std::vector<bool> consumed(candidates.size(), false);
    for (size_t i = 0; i < candidates.size(); ++i) {
        if (consumed[i]) {
            continue;
        }
        std::vector<ObservationCandidate> cluster;
        cluster.push_back(candidates[i]);
        consumed[i] = true;

        bool expanded = true;
        while (expanded) {
            expanded = false;
            for (size_t j = i + 1; j < candidates.size(); ++j) {
                if (consumed[j]) {
                    continue;
                }
                bool merge = false;
                for (const auto& member : cluster) {
                    if (candidates_should_merge_canonically(member, candidates[j])) {
                        merge = true;
                        break;
                    }
                }
                if (!merge) {
                    continue;
                }
                cluster.push_back(candidates[j]);
                consumed[j] = true;
                expanded = true;
            }
        }

        canonical_candidates.push_back(build_canonical_candidate(cluster));
    }
    return canonical_candidates;
}

std::string build_canonical_candidate_summary(const std::vector<CanonicalCandidate>& candidates,
                                              size_t max_count) {
    nlohmann::json summary = nlohmann::json::array();
    for (size_t i = 0; i < candidates.size() && i < max_count; ++i) {
        const auto& candidate = candidates[i];
        nlohmann::json item = nlohmann::json::object();
        item["displayLabel"] = candidate.display_label;
        item["tapBounds"] = candidate.tap_bounds;
        item["sourceSet"] = candidate.source_set;
        item["actionabilityRank"] = candidate.actionability_rank;
        item["memberCount"] = candidate.member_count;
        if (!candidate.risk_flags.empty()) {
            item["riskFlags"] = candidate.risk_flags;
        }
        summary.push_back(std::move(item));
    }
    return truncate_runtime_text(summary.dump(), 1200);
}

bool candidates_look_like_overlapping_duplicates(const ObservationCandidate& left,
                                                 const ObservationCandidate& right) {
    if (left.bounds.empty() || right.bounds.empty() || !bounds_overlap(left.bounds, right.bounds)) {
        return false;
    }
    const std::string left_label = normalize_runtime_key(left.label);
    const std::string right_label = normalize_runtime_key(right.label);
    if (!left_label.empty() && left_label == right_label) {
        return true;
    }
    return (!left.label.empty() && contains_runtime_match(right.match_text, left.label))
            || (!right.label.empty() && contains_runtime_match(left.match_text, right.label));
}

bool candidate_label_matches_step_target_exactly(const ObservationCandidate& candidate,
                                                 const SkillStepHint& step) {
    const std::string candidate_label = normalize_runtime_key(candidate.label);
    if (candidate_label.empty()) {
        return false;
    }
    if (!step.target.empty() && candidate_label == normalize_runtime_key(step.target)) {
        return true;
    }
    for (const auto& alias : step.aliases) {
        if (!alias.empty() && candidate_label == normalize_runtime_key(alias)) {
            return true;
        }
    }
    return false;
}

bool candidates_are_overlapping_same_target_label(const ObservationCandidate& left,
                                                  const ObservationCandidate& right,
                                                  const SkillStepHint& step) {
    if (!candidates_look_like_overlapping_duplicates(left, right)) {
        return false;
    }
    const std::string left_label = normalize_runtime_key(left.label);
    const std::string right_label = normalize_runtime_key(right.label);
    return !left_label.empty()
            && left_label == right_label
            && candidate_label_matches_step_target_exactly(left, step)
            && candidate_label_matches_step_target_exactly(right, step);
}

double candidate_entry_semantic_bonus(const ObservationCandidate& candidate,
                                      const SkillStepHint& step,
                                      const ObservationSnapshot& snapshot) {
    if (!step_prefers_corner_entry(step)) {
        return 0.0;
    }

    double bonus = 0.0;
    if (candidate_anchor_type_is(candidate, "profile_entry")) {
        bonus += 42.0;
    }
    if (candidate_container_role_is(candidate, "header")) {
        bonus += 12.0;
    }
    const std::string preferred_region = preferred_region_for_step(step);
    if (!preferred_region.empty() && candidate_in_region(candidate, preferred_region, snapshot)) {
        bonus += 10.0;
    }
    return bonus;
}

double candidate_entry_mismatch_penalty(const ObservationCandidate& candidate,
                                        const SkillStepHint& step) {
    if (!step_prefers_corner_entry(step)) {
        return 0.0;
    }

    double penalty = 0.0;
    if (candidate_anchor_type_is(candidate, "back_entry")) {
        penalty += 80.0;
    }
    if (candidate_anchor_type_is(candidate, "tab_entry")) {
        penalty += 28.0;
    }
    if (candidate_anchor_type_is(candidate, "search_entry")) {
        penalty += 28.0;
    }
    if (candidate_anchor_type_is(candidate, "overflow_entry")) {
        penalty += 24.0;
    }
    if (candidate_anchor_type_is(candidate, "primary_action")) {
        penalty += 16.0;
    }
    if (candidate_container_role_is(candidate, "tab_bar")) {
        penalty += 24.0;
    }
    return penalty;
}

double candidate_card_title_semantic_bonus(const ObservationCandidate& candidate,
                                           const SkillStepHint& step) {
    if (!step_prefers_card_title_anchor(step)) {
        return 0.0;
    }

    double bonus = 0.0;
    if (candidate_label_matches_step_primary_target_exactly(candidate, step)) {
        bonus += 52.0;
    }
    if (candidate_anchor_type_is(candidate, "card_title")
            || candidate_anchor_type_is(candidate, "card_header")
            || candidate_anchor_type_is(candidate, "tile_title")) {
        bonus += 36.0;
    }
    if (candidate_container_role_is(candidate, "business_grid")
            || candidate_container_role_is(candidate, "grid")
            || candidate_container_role_is(candidate, "card")) {
        bonus += 8.0;
    }
    return bonus;
}

double candidate_card_title_mismatch_penalty(const ObservationCandidate& candidate,
                                             const SkillStepHint& step) {
    if (!step_prefers_card_title_anchor(step)) {
        return 0.0;
    }

    double penalty = 0.0;
    if (candidate_is_card_title_noise(candidate)
            && !candidate_label_matches_step_primary_target_exactly(candidate, step)) {
        penalty += 58.0;
    }
    if (!candidate_has_card_title_semantics(candidate, step)
            && !candidate_label_matches_step_primary_target_exactly(candidate, step)) {
        penalty += 20.0;
    }
    return penalty;
}

double candidate_risk_penalty(const ObservationCandidate& candidate) {
    double penalty = 0.0;
    if (candidate.badge_like) {
        penalty += 36.0;
    }
    if (candidate.numeric_like) {
        penalty += 18.0;
    }
    if (candidate.decorative_like) {
        penalty += 12.0;
    }
    if (candidate.repeat_group) {
        penalty += 10.0;
    }
    return penalty;
}

double candidate_region_bonus(const ObservationCandidate& candidate,
                              const SkillStepHint& step,
                              const ObservationSnapshot& snapshot) {
    const std::string preferred_region = preferred_region_for_step(step);
    if (preferred_region.empty()) {
        return 0.0;
    }
    if (!candidate_in_region(candidate, preferred_region, snapshot)) {
        return 0.0;
    }
    return region_bonus_for_label(preferred_region);
}

bool candidate_in_preferred_entry_region(const ObservationCandidate& candidate,
                                         const SkillStepHint& step,
                                         const ObservationSnapshot& snapshot) {
    const std::string preferred_region = preferred_region_for_step(step);
    return candidate_in_region(candidate, preferred_region, snapshot);
}

bool candidate_in_tight_corner_entry_region(const ObservationCandidate& candidate,
                                            const ObservationSnapshot& snapshot) {
    if (snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
        return false;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(candidate.bounds, center_x, center_y)) {
        return false;
    }

    const double width = static_cast<double>(snapshot.screen_width);
    const double height = static_cast<double>(snapshot.screen_height);
    return center_x <= static_cast<int>(width * 0.26)
            && center_y <= static_cast<int>(height * 0.12);
}

bool candidate_is_midpage_list_or_card_candidate(const ObservationCandidate& candidate,
                                                 const ObservationSnapshot& snapshot) {
    if (candidate_in_tight_corner_entry_region(candidate, snapshot)) {
        return false;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(candidate.bounds, center_x, center_y)) {
        return false;
    }

    const double width = static_cast<double>(snapshot.screen_width);
    const double height = static_cast<double>(snapshot.screen_height);
    if (width <= 0.0 || height <= 0.0) {
        return false;
    }

    const bool below_header_band = center_y >= static_cast<int>(height * 0.22);
    const bool within_content_band = center_y <= static_cast<int>(height * 0.88);
    const bool within_page_body = center_x >= static_cast<int>(width * 0.05)
            && center_x <= static_cast<int>(width * 0.95);
    const bool list_like = candidate_container_role_is(candidate, "list")
            || candidate_container_role_is(candidate, "card")
            || candidate.repeat_group;
    const bool card_like_action = candidate_anchor_type_is(candidate, "primary_action")
            || candidate_anchor_type_is(candidate, "tab_entry");

    return below_header_band
            && within_content_band
            && within_page_body
            && (list_like || card_like_action);
}

size_t filter_corner_entry_midpage_candidates(std::vector<ObservationCandidate>& candidates,
                                              const ObservationSnapshot& snapshot) {
    const size_t before = candidates.size();
    candidates.erase(std::remove_if(candidates.begin(), candidates.end(),
                            [&](const ObservationCandidate& candidate) {
                                return candidate_is_midpage_list_or_card_candidate(candidate, snapshot);
                            }),
            candidates.end());
    return before > candidates.size() ? (before - candidates.size()) : 0;
}

double candidate_disambiguation_score(const ObservationCandidate& candidate,
                                      const SkillStepHint& step,
                                      const ObservationSnapshot& snapshot) {
    return static_cast<double>(candidate_match_strength(candidate, step))
            + static_cast<double>(candidate_source_bonus(candidate))
            + candidate_region_bonus(candidate, step, snapshot)
            + candidate_anchor_type_bonus(candidate, step)
            + candidate_container_role_bonus(candidate, step)
            + candidate_entry_semantic_bonus(candidate, step, snapshot)
            + candidate_card_title_semantic_bonus(candidate, step)
            + (candidate.clickable ? 8.0 : 0.0)
            + (candidate.container_clickable ? 6.0 : 0.0)
            + (candidate.score * 10.0)
            - candidate_risk_penalty(candidate)
            - candidate_entry_mismatch_penalty(candidate, step)
            - candidate_card_title_mismatch_penalty(candidate, step);
}

bool region_allows_top_left_coordinate_recovery(const std::string& region) {
    const std::string normalized_region = normalize_region_label(region);
    if (normalized_region.empty()) {
        return true;
    }
    if (normalized_region.find("right") != std::string::npos
            || normalized_region.find("bottom") != std::string::npos
            || contains_runtime_match(region, u8"\u53f3")
            || contains_runtime_match(region, u8"\u4e0b")) {
        return false;
    }
    return true;
}

std::optional<ToolCall> build_corner_region_recovery_tool_call(const SkillStepHint& step,
                                                               const ObservationSnapshot& snapshot) {
    if (!step_prefers_corner_entry(step) || snapshot.snapshot_id.empty()
            || snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_region_coordinate_recovery_skipped] target={} prefers_corner={} has_snapshot={} screen_width={} screen_height={}",
                step.target,
                step_prefers_corner_entry(step),
                !snapshot.snapshot_id.empty(),
                snapshot.screen_width,
                snapshot.screen_height);
        return std::nullopt;
    }

    const std::string preferred_region = preferred_region_for_step(step);
    if (!region_allows_top_left_coordinate_recovery(preferred_region)) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_region_coordinate_recovery_skipped] target={} reason=region_not_top_left preferred_region={}",
                step.target,
                preferred_region);
        return std::nullopt;
    }

    // Corner entries such as avatars are often plain ImageViews and may not appear in
    // hybrid actionableNodes. Use a conservative header coordinate, then verify progress
    // through the normal observation/confirmation path. Keep the point inside the app
    // toolbar/header band rather than the system status bar.
    const int tap_x = std::max(1, static_cast<int>(snapshot.screen_width * 0.076));
    const int tap_y = std::max(1, static_cast<int>(snapshot.screen_height * 0.075));
    const int bound_left = std::max(0, tap_x - static_cast<int>(snapshot.screen_width * 0.055));
    const int bound_top = std::max(0, tap_y - static_cast<int>(snapshot.screen_height * 0.035));
    const int bound_right = std::min(snapshot.screen_width, tap_x + static_cast<int>(snapshot.screen_width * 0.055));
    const int bound_bottom = std::min(snapshot.screen_height, tap_y + static_cast<int>(snapshot.screen_height * 0.035));
    std::ostringstream bounds;
    bounds << "[" << bound_left << "," << bound_top << "]["
           << bound_right << "," << bound_bottom << "]";

    ToolCall tool_call;
    tool_call.id = generate_tool_id();
    tool_call.name = "android_gesture_tool";
    tool_call.arguments = nlohmann::json::object({
            {"action", "tap"},
            {"x", tap_x},
            {"y", tap_y},
            {"observation", {
                    {"snapshotId", snapshot.snapshot_id},
                    {"targetDescriptor", step.target.empty() ? "top-left entry" : step.target},
                    {"referencedBounds", bounds.str()},
                    {"recovery", "corner_region_coordinate"}
            }}
    });
    ICRAW_LOG_INFO(
            "[AgentLoop][fast_execute_region_coordinate_recovery] action=tap target={} region={} x={} y={} page={} snapshot_id={}",
            step.target,
            preferred_region.empty() ? "top_left" : preferred_region,
            tap_x,
            tap_y,
            snapshot.activity,
            snapshot.snapshot_id);
    return tool_call;
}

std::optional<ToolCall> maybe_build_fast_execute_tool_call(const ExecutionState& state,
                                                           const std::string& observation_content) {
    if (state.mode != "planned_fast_execute" || state.phase == "readout"
            || !state.active_hints || state.pending_step_index < 0
            || state.pending_step_index >= static_cast<int>(state.active_hints->steps.size())) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_fallback] reason=not_eligible mode={} phase={} has_hints={} pending_step_index={}",
                state.mode,
                state.phase,
                state.active_hints ? "true" : "false",
                state.pending_step_index);
        return std::nullopt;
    }

    const SkillStepHint& step = state.active_hints->steps[static_cast<size_t>(state.pending_step_index)];
    if (step_attempt_limit_reached(state, step, state.pending_step_index)) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_fallback] reason=max_attempts_reached step_index={} target={} attempt_count={} max_attempts={}",
                state.pending_step_index,
                truncate_runtime_text(step.target, 80),
                step_action_attempt_count(state, state.pending_step_index),
                step.max_attempts);
        return std::nullopt;
    }
    if (step.action != "tap" && step.action != "open") {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=unsupported_action action={}", step.action);
        return std::nullopt;
    }

    const ObservationSnapshot snapshot = parse_observation_snapshot(observation_content);
    if (snapshot.snapshot_id.empty()) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=missing_snapshot");
        return std::nullopt;
    }
    if (!matches_step_page(snapshot, step)) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=page_not_matched page={} activity={}",
                step.page, snapshot.activity);
        return std::nullopt;
    }
    if (step.target.empty() && step.aliases.empty()) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=missing_target");
        return std::nullopt;
    }

    const ObservationFingerprint snapshot_fingerprint = build_observation_fingerprint(snapshot);
    const std::string attempt_cache_key = build_navigation_attempt_cache_key(
            state.pending_step_index, snapshot_fingerprint.value);
    const auto attempt_it = state.navigation_attempt_cache.find(attempt_cache_key);
    if (attempt_it != state.navigation_attempt_cache.end()) {
        ICRAW_LOG_INFO(
                "[AgentLoop][navigation_attempt_cache_hit] step_index={} attempt_count={} target={} candidate={} tap_bounds={}",
                state.pending_step_index,
                attempt_it->second.attempt_count,
                truncate_runtime_text(attempt_it->second.step_target, 80),
                truncate_runtime_text(attempt_it->second.selected_candidate, 80),
                truncate_runtime_text(attempt_it->second.tap_bounds, 80));
    }
    if (attempt_it != state.navigation_attempt_cache.end()
            && attempt_it->second.attempt_count > 0
            && state.navigation_checkpoint.stagnant_rounds > 0) {
        ICRAW_LOG_INFO(
                "[AgentLoop][navigation_no_progress_detected] reason=repeat_fingerprint_after_click step_index={} attempt_count={} fingerprint={} candidate={} tap_bounds={}",
                state.pending_step_index,
                attempt_it->second.attempt_count,
                truncate_runtime_text(snapshot_fingerprint.value, 160),
                truncate_runtime_text(attempt_it->second.selected_candidate, 80),
                truncate_runtime_text(attempt_it->second.tap_bounds, 80));
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_fallback_reason] reason=no_progress_cached canonical_count=0 target={}",
                step.target);
        return std::nullopt;
    }

    std::vector<std::string> match_terms;
    if (!step.target.empty()) {
        match_terms.push_back(step.target);
    }
    for (const auto& alias : step.aliases) {
        if (!alias.empty()) {
            match_terms.push_back(alias);
        }
    }

    std::vector<ObservationCandidate> primary_matched_candidates;
    std::vector<ObservationCandidate> alias_matched_candidates;
    std::vector<ObservationCandidate> high_confidence_candidates;
    for (const auto& candidate : snapshot.actionable_candidates) {
        if (candidate.bounds.empty()) {
            continue;
        }
        const bool high_confidence = candidate.source == "vision_only"
                ? candidate.score >= FAST_EXECUTE_VISION_ONLY_SCORE_MIN
                : candidate.score >= FAST_EXECUTE_NATIVE_SCORE_MIN;
        if (!high_confidence) {
            continue;
        }
        high_confidence_candidates.push_back(candidate);

        const int primary_strength = candidate_term_match_strength(candidate, step.target, true);
        if (primary_strength > 0) {
            primary_matched_candidates.push_back(candidate);
            continue;
        }

        int alias_strength = 0;
        for (const auto& alias : step.aliases) {
            alias_strength = std::max(alias_strength, candidate_term_match_strength(candidate, alias, false));
        }
        if (alias_strength > 0) {
            alias_matched_candidates.push_back(candidate);
        }
    }

    std::vector<ObservationCandidate> matched_candidates =
            !primary_matched_candidates.empty() ? primary_matched_candidates : alias_matched_candidates;

    if (step_prefers_card_title_anchor(step) && !matched_candidates.empty()) {
        std::vector<ObservationCandidate> title_semantic_candidates;
        for (const auto& candidate : matched_candidates) {
            if (candidate_has_card_title_semantics(candidate, step)
                    && !candidate_is_card_title_noise(candidate)) {
                title_semantic_candidates.push_back(candidate);
            }
        }
        if (!title_semantic_candidates.empty()
                && title_semantic_candidates.size() < matched_candidates.size()) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_card_title_filtered] target={} original_count={} filtered_count={}",
                    step.target,
                    matched_candidates.size(),
                    title_semantic_candidates.size());
            matched_candidates = std::move(title_semantic_candidates);
        }
    }

    if (step_prefers_corner_entry(step)) {
        const size_t filtered_high_confidence =
                filter_corner_entry_midpage_candidates(high_confidence_candidates, snapshot);
        if (filtered_high_confidence > 0) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_corner_midpage_filtered] stage=high_confidence removed_count={} remaining_count={}",
                    filtered_high_confidence,
                    high_confidence_candidates.size());
        }
        const size_t filtered_matched =
                filter_corner_entry_midpage_candidates(matched_candidates, snapshot);
        if (filtered_matched > 0) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_corner_midpage_filtered] stage=matched removed_count={} remaining_count={}",
                    filtered_matched,
                    matched_candidates.size());
        }
    }

    auto canonicalize_candidates = [&](std::vector<ObservationCandidate>& candidates,
                                       const char* stage) {
        if (candidates.empty()) {
            return std::vector<CanonicalCandidate>{};
        }
        const auto canonical_candidates = build_canonical_candidates(candidates);
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_count] stage={} raw_count={} canonical_count={} target={} activity={}",
                stage,
                candidates.size(),
                canonical_candidates.size(),
                step.target,
                snapshot.activity);
        if (canonical_candidates.size() < candidates.size()) {
            candidates.clear();
            for (const auto& candidate : canonical_candidates) {
                candidates.push_back(candidate.aggregate);
            }
        }
        return canonical_candidates;
    };

    (void)canonicalize_candidates(high_confidence_candidates, "high_confidence");
    (void)canonicalize_candidates(matched_candidates, "matched");

    ICRAW_LOG_INFO(
            "[AgentLoop][fast_execute_evaluated] target={} aliases={} activity={} source={} candidate_count={} high_confidence_count={} primary_matches={} alias_matches={} warning_conflicts={}",
            step.target,
            append_unique_strings(build_string_array_json(step.aliases)).dump(),
            snapshot.activity,
            snapshot.source,
            snapshot.actionable_candidates.size(),
            high_confidence_candidates.size(),
            primary_matched_candidates.size(),
            alias_matched_candidates.size(),
            snapshot.warning_conflict_count);

    if (matched_candidates.empty() && step_prefers_corner_entry(step)) {
        std::vector<ObservationCandidate> preferred_region_high_confidence_candidates;
        for (const auto& candidate : high_confidence_candidates) {
            if (candidate_in_preferred_entry_region(candidate, step, snapshot)
                    && candidate_in_tight_corner_entry_region(candidate, snapshot)
                    && !candidate_is_corner_entry_noise(candidate)) {
                preferred_region_high_confidence_candidates.push_back(candidate);
            }
        }
        if (preferred_region_high_confidence_candidates.size() == 1) {
            const auto& only_candidate = preferred_region_high_confidence_candidates.front();
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_region_fallback] reason=single_preferred_region_candidate target={} candidate={} source={} score={}",
                    step.target,
                    only_candidate.label,
                    only_candidate.source,
                    only_candidate.score);
            matched_candidates.push_back(only_candidate);
        } else if (preferred_region_high_confidence_candidates.size() > 1) {
            matched_candidates = preferred_region_high_confidence_candidates;
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_region_fallback] reason=multiple_preferred_region_candidates target={} count={}",
                    step.target,
                    matched_candidates.size());
        }
    }

    if (matched_candidates.empty() && step_prefers_corner_entry(step)) {
        std::vector<ObservationCandidate> semantic_entry_candidates;
        for (const auto& candidate : high_confidence_candidates) {
            if (candidate_is_corner_entry_noise(candidate)) {
                continue;
            }
            if (candidate_has_corner_entry_semantics(candidate)
                    || (candidate_container_role_is(candidate, "header")
                        && candidate_in_preferred_entry_region(candidate, step, snapshot))) {
                semantic_entry_candidates.push_back(candidate);
            }
        }
        if (semantic_entry_candidates.size() > 1) {
            std::vector<ObservationCandidate> profile_entry_candidates;
            for (const auto& candidate : semantic_entry_candidates) {
                if (candidate_anchor_type_is(candidate, "profile_entry")) {
                    profile_entry_candidates.push_back(candidate);
                }
            }
            if (!profile_entry_candidates.empty()
                    && profile_entry_candidates.size() < semantic_entry_candidates.size()) {
                semantic_entry_candidates = std::move(profile_entry_candidates);
            }
        }
        if (semantic_entry_candidates.size() > 1) {
            std::vector<ObservationCandidate> preferred_region_candidates;
            for (const auto& candidate : semantic_entry_candidates) {
                if (candidate_in_preferred_entry_region(candidate, step, snapshot)
                        && candidate_in_tight_corner_entry_region(candidate, snapshot)) {
                    preferred_region_candidates.push_back(candidate);
                }
            }
            if (!preferred_region_candidates.empty()
                    && preferred_region_candidates.size() < semantic_entry_candidates.size()) {
                semantic_entry_candidates = std::move(preferred_region_candidates);
            }
        }
        if (semantic_entry_candidates.size() == 1) {
            const auto& only_candidate = semantic_entry_candidates.front();
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_semantic_fallback] reason=single_entry_semantic_candidate target={} candidate={} source={} score={}",
                    step.target,
                    only_candidate.label,
                    only_candidate.source,
                    only_candidate.score);
            matched_candidates.push_back(only_candidate);
        } else if (!semantic_entry_candidates.empty()) {
            matched_candidates = semantic_entry_candidates;
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_semantic_fallback] reason=multiple_entry_semantic_candidates target={} count={}",
                    step.target,
                    matched_candidates.size());
        }
    }

    if (matched_candidates.empty()
            && high_confidence_candidates.size() == 1
            && !step_prefers_card_title_anchor(step)) {
        const auto& only_candidate = high_confidence_candidates.front();
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_alias_fallback] reason=single_high_confidence_candidate target={} aliases={} candidate={} source={} score={}",
                step.target,
                append_unique_strings(build_string_array_json(step.aliases)).dump(),
                only_candidate.label,
                only_candidate.source,
                only_candidate.score);
        matched_candidates.push_back(only_candidate);
    }

    if (matched_candidates.size() > 1 && step_prefers_card_title_anchor(step)) {
        std::vector<ObservationCandidate> exact_title_candidates;
        for (const auto& candidate : matched_candidates) {
            if (candidate_label_matches_step_primary_target_exactly(candidate, step)) {
                exact_title_candidates.push_back(candidate);
            }
        }
        if (exact_title_candidates.size() == 1) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_card_title_exact_disambiguated] target={} winner={} original_count={}",
                    step.target,
                    exact_title_candidates.front().label,
                    matched_candidates.size());
            matched_candidates = std::move(exact_title_candidates);
        } else if (!exact_title_candidates.empty()
                && exact_title_candidates.size() < matched_candidates.size()) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_card_title_exact_filtered] target={} original_count={} filtered_count={}",
                    step.target,
                    matched_candidates.size(),
                    exact_title_candidates.size());
            matched_candidates = std::move(exact_title_candidates);
        }
    }

    if (matched_candidates.size() > 1) {
        if (step_prefers_corner_entry(step)) {
            std::vector<ObservationCandidate> tight_corner_candidates;
            for (const auto& candidate : matched_candidates) {
                if (candidate_in_tight_corner_entry_region(candidate, snapshot)) {
                    tight_corner_candidates.push_back(candidate);
                }
            }
            if (!tight_corner_candidates.empty()
                    && tight_corner_candidates.size() < matched_candidates.size()) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_tight_corner_filtered] target={} original_count={} filtered_count={}",
                        step.target,
                        matched_candidates.size(),
                        tight_corner_candidates.size());
                matched_candidates = std::move(tight_corner_candidates);
            }
        }
    }

    if (!matched_candidates.empty() && step_prefers_corner_entry(step)) {
        std::vector<ObservationCandidate> strict_corner_candidates;
        for (const auto& candidate : matched_candidates) {
            if (candidate_in_tight_corner_entry_region(candidate, snapshot)) {
                strict_corner_candidates.push_back(candidate);
            }
        }
        if (strict_corner_candidates.empty()) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_fallback] reason=corner_candidate_out_of_region count={} target={}",
                    matched_candidates.size(),
                    step.target);
            auto recovery_call = build_corner_region_recovery_tool_call(step, snapshot);
            if (recovery_call.has_value()) {
                return recovery_call;
            }
            return std::nullopt;
        }
        if (strict_corner_candidates.size() < matched_candidates.size()) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_corner_guard_filtered] target={} original_count={} filtered_count={}",
                    step.target,
                    matched_candidates.size(),
                    strict_corner_candidates.size());
            matched_candidates = std::move(strict_corner_candidates);
        }
    }

    if (matched_candidates.size() > 1) {
        if (step_prefers_corner_entry(step)) {
            std::vector<ObservationCandidate> profile_entry_candidates;
            for (const auto& candidate : matched_candidates) {
                if (candidate_anchor_type_is(candidate, "profile_entry")) {
                    profile_entry_candidates.push_back(candidate);
                }
            }
            if (!profile_entry_candidates.empty()
                    && profile_entry_candidates.size() < matched_candidates.size()) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_profile_entry_filtered] target={} original_count={} filtered_count={}",
                        step.target,
                        matched_candidates.size(),
                        profile_entry_candidates.size());
                matched_candidates = std::move(profile_entry_candidates);
            }
        }
    }

    if (matched_candidates.size() > 1) {
        if (step_prefers_corner_entry(step)) {
            std::vector<ObservationCandidate> preferred_region_candidates;
            for (const auto& candidate : matched_candidates) {
                if (candidate_in_preferred_entry_region(candidate, step, snapshot)) {
                    preferred_region_candidates.push_back(candidate);
                }
            }
            if (preferred_region_candidates.size() == 1) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_region_disambiguated] target={} winner={} source={} score={}",
                        step.target,
                        preferred_region_candidates.front().label,
                        preferred_region_candidates.front().source,
                        preferred_region_candidates.front().score);
                matched_candidates = preferred_region_candidates;
            } else if (!preferred_region_candidates.empty()
                    && preferred_region_candidates.size() < matched_candidates.size()) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_region_filtered] target={} original_count={} filtered_count={}",
                        step.target,
                        matched_candidates.size(),
                        preferred_region_candidates.size());
                matched_candidates = preferred_region_candidates;
            }
        }
    }

    if (matched_candidates.size() > 1) {
        std::vector<ObservationCandidate> exact_label_candidates;
        for (const auto& candidate : matched_candidates) {
            if (candidate_label_matches_step_target_exactly(candidate, step)) {
                exact_label_candidates.push_back(candidate);
            }
        }
        if (!step_prefers_corner_entry(step) && exact_label_candidates.size() == 1) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_exact_label_disambiguated] target={} winner={} original_count={}",
                    step.target,
                    exact_label_candidates.front().label,
                    matched_candidates.size());
            matched_candidates = std::move(exact_label_candidates);
        }
    }

    if (matched_candidates.size() > 1) {
        std::sort(matched_candidates.begin(), matched_candidates.end(),
                [&](const ObservationCandidate& left, const ObservationCandidate& right) {
                    return candidate_disambiguation_score(left, step, snapshot)
                            > candidate_disambiguation_score(right, step, snapshot);
                });
        if (matched_candidates.size() >= 2) {
            const double top_score = candidate_disambiguation_score(matched_candidates[0], step, snapshot);
            const double second_score = candidate_disambiguation_score(matched_candidates[1], step, snapshot);
            const double min_gap = step_prefers_corner_entry(step) ? 8.0 : 16.0;
            const bool overlapping_duplicates = candidates_look_like_overlapping_duplicates(
                    matched_candidates[0], matched_candidates[1]);
            const bool same_target_label_duplicates = candidates_are_overlapping_same_target_label(
                    matched_candidates[0], matched_candidates[1], step);
            const bool top_more_actionable = candidate_actionability_rank(matched_candidates[0])
                    > candidate_actionability_rank(matched_candidates[1]);
            const bool top_has_stronger_entry_semantics =
                    step_prefers_corner_entry(step)
                    && candidate_has_corner_entry_semantics(matched_candidates[0])
                    && !candidate_has_corner_entry_semantics(matched_candidates[1]);
            if (top_score >= second_score + min_gap) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_disambiguated] target={} winner={} winner_score={} runner_up={} runner_up_score={}",
                        step.target,
                        matched_candidates[0].label,
                        top_score,
                        matched_candidates[1].label,
                        second_score);
                matched_candidates.resize(1);
            } else if (same_target_label_duplicates) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_same_label_duplicate_disambiguated] target={} winner={} winner_score={} runner_up={} runner_up_score={} top_rank={} runner_up_rank={}",
                        step.target,
                        matched_candidates[0].label,
                        top_score,
                        matched_candidates[1].label,
                        second_score,
                        candidate_actionability_rank(matched_candidates[0]),
                        candidate_actionability_rank(matched_candidates[1]));
                matched_candidates.resize(1);
            } else if (overlapping_duplicates
                    && (top_more_actionable
                        || top_has_stronger_entry_semantics
                        || top_score >= second_score + 4.0)) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_overlap_disambiguated] target={} winner={} winner_score={} runner_up={} runner_up_score={} top_rank={} runner_up_rank={}",
                        step.target,
                        matched_candidates[0].label,
                        top_score,
                        matched_candidates[1].label,
                        second_score,
                        candidate_actionability_rank(matched_candidates[0]),
                        candidate_actionability_rank(matched_candidates[1]));
                matched_candidates.resize(1);
            }
        }
    }

    if (matched_candidates.size() != 1) {
        if (matched_candidates.empty() && step_prefers_corner_entry(step)) {
            auto recovery_call = build_corner_region_recovery_tool_call(step, snapshot);
            if (recovery_call.has_value()) {
                return recovery_call;
            }
        }
        const auto fallback_canonical = build_canonical_candidates(matched_candidates);
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_fallback_reason] reason=ambiguous_candidate canonical_count={} target={} summary={}",
                fallback_canonical.size(),
                step.target,
                truncate_runtime_text(
                        build_canonical_candidate_summary(fallback_canonical, CANONICAL_CANDIDATE_SUMMARY_COUNT),
                        220));
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=ambiguous_candidate count={} target={} aliases={}",
                matched_candidates.size(),
                step.target,
                append_unique_strings(build_string_array_json(step.aliases)).dump());
        return std::nullopt;
    }
    std::string conflict_reason;
    if (should_block_fast_execute_on_conflicts(snapshot, step, matched_candidates[0], conflict_reason)) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_fallback] reason={} warning_count={} target={} candidate={} candidate_source={} clickable={} container_clickable={} badge_like={} numeric_like={}",
                conflict_reason,
                snapshot.warning_conflict_count,
                step.target,
                matched_candidates[0].label,
                matched_candidates[0].source,
                matched_candidates[0].clickable,
                matched_candidates[0].container_clickable,
                matched_candidates[0].badge_like,
                matched_candidates[0].numeric_like);
        return std::nullopt;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(matched_candidates[0].bounds, center_x, center_y)) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=invalid_bounds bounds={}",
                matched_candidates[0].bounds);
        return std::nullopt;
    }

    ToolCall tool_call;
    tool_call.id = generate_tool_id();
    tool_call.name = "android_gesture_tool";
    tool_call.arguments = nlohmann::json::object({
            {"action", "tap"},
            {"x", center_x},
            {"y", center_y},
            {"observation", {
                    {"snapshotId", snapshot.snapshot_id},
                    {"targetDescriptor", matched_candidates[0].label.empty() ? step.target : matched_candidates[0].label},
                    {"referencedBounds", matched_candidates[0].bounds}
            }}
    });
    const auto selected_canonical = build_canonical_candidates(matched_candidates);
    if (!selected_canonical.empty()) {
        const auto& selected = selected_canonical.front();
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_selected] target={} display_label={} tap_bounds={} source_set={} actionability_rank={} member_count={} risk_flags={}",
                step.target,
                truncate_runtime_text(selected.display_label, 80),
                truncate_runtime_text(selected.tap_bounds, 80),
                truncate_runtime_text(selected.source_set, 80),
                selected.actionability_rank,
                selected.member_count,
                truncate_runtime_text(selected.risk_flags, 80));
    }
    ICRAW_LOG_INFO("[AgentLoop][fast_execute_hit] action=tap target={} source={} score={} page={}",
            step.target,
            matched_candidates[0].source,
            matched_candidates[0].score,
            snapshot.activity);
    return tool_call;
}


