#pragma once

// Internal AgentLoop implementation chunk. Included only by agent_loop.cpp.

void append_visible_text_fragment(std::string& visible_text,
                                  const std::string& fragment,
                                  size_t max_chars = OBSERVATION_VISIBLE_TEXT_MAX_CHARS) {
    const std::string trimmed = trim_whitespace(fragment);
    if (trimmed.empty() || visible_text.size() >= max_chars) {
        return;
    }
    if (!visible_text.empty()) {
        visible_text.push_back(' ');
    }
    const size_t remaining = max_chars - visible_text.size();
    visible_text += truncate_runtime_text(trimmed, remaining);
}

void append_json_text_fields(std::string& visible_text,
                             const nlohmann::json& object,
                             const std::vector<std::string>& keys) {
    if (!object.is_object()) {
        return;
    }
    for (const auto& key : keys) {
        append_visible_text_fragment(visible_text, first_string_value(object, {key}));
    }
}

void append_json_text_array(std::string& visible_text,
                            const nlohmann::json& array,
                            const std::vector<std::string>& keys,
                            size_t max_items = 80) {
    if (!array.is_array()) {
        return;
    }
    size_t count = 0;
    for (const auto& item : array) {
        if (count++ >= max_items || visible_text.size() >= OBSERVATION_VISIBLE_TEXT_MAX_CHARS) {
            break;
        }
        if (item.is_string()) {
            append_visible_text_fragment(visible_text, item.get<std::string>());
        } else if (item.is_object()) {
            append_json_text_fields(visible_text, item, keys);
        }
    }
}

void append_native_xml_text_attributes(std::string& visible_text,
                                       const std::string& xml,
                                       size_t max_values = 120) {
    static const std::vector<std::string> attributes = {
        "text", "content-desc", "contentDescription"
    };
    size_t appended = 0;
    for (const auto& attribute : attributes) {
        std::string pattern = attribute + "=\"";
        size_t pos = 0;
        while (appended < max_values && visible_text.size() < OBSERVATION_VISIBLE_TEXT_MAX_CHARS) {
            pos = xml.find(pattern, pos);
            if (pos == std::string::npos) {
                break;
            }
            pos += pattern.size();
            const size_t end = xml.find('"', pos);
            if (end == std::string::npos) {
                break;
            }
            append_visible_text_fragment(visible_text, xml.substr(pos, end - pos));
            pos = end + 1;
            appended++;
        }
    }
}

ObservationSnapshot parse_observation_snapshot(const std::string& content) {
    ObservationSnapshot snapshot;
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return snapshot;
        }

        snapshot.activity = json.value("activityClassName", "");
        snapshot.source = json.value("source", "");
        snapshot.visual_mode = json.value("visualObservationMode", "");
        snapshot.snapshot_id = json.value("snapshotId", "");
        snapshot.screen_width = json.value("screenSnapshotWidth", 0);
        snapshot.screen_height = json.value("screenSnapshotHeight", 0);
        append_visible_text_fragment(snapshot.visible_text, snapshot.activity);
        if (json.contains("nativeViewXml") && json["nativeViewXml"].is_string()) {
            append_native_xml_text_attributes(snapshot.visible_text,
                    json["nativeViewXml"].get<std::string>());
        }
        if (json.contains("screenVisionCompact") && json["screenVisionCompact"].is_object()) {
            const auto& compact = json["screenVisionCompact"];
            append_json_text_fields(snapshot.visible_text, compact, {"summary"});
            if (compact.contains("page")) {
                if (compact["page"].is_string()) {
                    append_visible_text_fragment(snapshot.visible_text,
                            compact["page"].get<std::string>());
                } else if (compact["page"].is_object()) {
                    append_json_text_fields(snapshot.visible_text, compact["page"],
                            {"title", "name", "summary"});
                }
            }
            for (const auto& key : {"texts", "controls", "items", "topTexts", "topControls", "topItems"}) {
                if (compact.contains(key)) {
                    append_json_text_array(snapshot.visible_text, compact[key],
                            {"text", "label", "title", "name", "contentDescription", "content_description"});
                }
            }
        }
        if (!json.contains("hybridObservation") || !json["hybridObservation"].is_object()) {
            return snapshot;
        }

        const auto& hybrid = json["hybridObservation"];
        snapshot.summary = hybrid.value("summary", "");
        append_visible_text_fragment(snapshot.visible_text, snapshot.summary);
        if (hybrid.contains("page") && hybrid["page"].is_object()) {
            const auto& page = hybrid["page"];
            append_json_text_fields(snapshot.visible_text, page, {"title", "name", "summary"});
            if (snapshot.screen_width <= 0) {
                snapshot.screen_width = page.value("width", 0);
            }
            if (snapshot.screen_height <= 0) {
                snapshot.screen_height = page.value("height", 0);
            }
        }
        if (hybrid.contains("visibleItems")) {
            append_json_text_array(snapshot.visible_text, hybrid["visibleItems"],
                    {"text", "label", "title", "name", "summary"});
        }
        if (hybrid.contains("conflicts") && hybrid["conflicts"].is_array()) {
            for (const auto& conflict : hybrid["conflicts"]) {
                if (!conflict.is_object()) {
                    continue;
                }
                const std::string severity = conflict.value("severity", "");
                if (severity == "warning") {
                    snapshot.has_warning_conflict = true;
                    snapshot.warning_conflict_count++;
                    ObservationConflict parsed_conflict;
                    parsed_conflict.code = conflict.value("code", "");
                    parsed_conflict.severity = severity;
                    parsed_conflict.message = conflict.value("message", "");
                    parsed_conflict.bounds = conflict.value("bounds", "");
                    snapshot.warning_conflicts.push_back(std::move(parsed_conflict));
                }
            }
        }
        if (hybrid.contains("actionableNodes") && hybrid["actionableNodes"].is_array()) {
            for (const auto& node : hybrid["actionableNodes"]) {
                if (!node.is_object()) {
                    continue;
                }
                ObservationCandidate candidate;
                candidate.label = first_string_value(node,
                        {"text", "visionLabel", "resourceId", "className", "id"});
                candidate.match_text = trim_whitespace(
                        first_string_value(node, {"text"}) + " "
                        + first_string_value(node, {"visionLabel"}) + " "
                        + first_string_value(node, {"contentDescription", "content_description"}) + " "
                        + first_string_value(node, {"parentSemanticContext", "parent_semantic_context"}) + " "
                        + first_string_value(node, {"resourceId"}) + " "
                        + first_string_value(node, {"className"}) + " "
                        + first_string_value(node, {"anchorType", "anchor_type"}) + " "
                        + first_string_value(node, {"containerRole", "container_role"}) + " "
                        + first_string_value(node, {"id"}));
                candidate.source = node.value("source", "");
                candidate.resource_id = trim_whitespace(first_string_value(node, {"resourceId"}));
                candidate.bounds = node.value("bounds", "");
                candidate.region = trim_whitespace(first_string_value(node, {"region"}));
                candidate.anchor_type = trim_whitespace(first_string_value(node, {"anchorType", "anchor_type"}));
                candidate.container_role = trim_whitespace(first_string_value(node, {"containerRole", "container_role"}));
                candidate.clickable = node.value("clickable", false);
                candidate.container_clickable = node.value("containerClickable", false);
                candidate.badge_like = node.value("badgeLike", false);
                candidate.numeric_like = node.value("numericLike", false);
                candidate.decorative_like = node.value("decorativeLike", false);
                candidate.repeat_group = node.value("repeatGroup", false);
                candidate.score = node.value("score", 0.0);
                append_visible_text_fragment(snapshot.visible_text, candidate.label);
                append_visible_text_fragment(snapshot.visible_text, candidate.match_text);
                snapshot.actionable_candidates.push_back(std::move(candidate));
            }
        }
        if (snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
            int max_right = 0;
            int max_bottom = 0;
            for (const auto& candidate : snapshot.actionable_candidates) {
                int left = 0;
                int top = 0;
                int right = 0;
                int bottom = 0;
                if (std::sscanf(candidate.bounds.c_str(),
                            "[%d,%d][%d,%d]",
                            &left,
                            &top,
                            &right,
                            &bottom) == 4) {
                    max_right = std::max(max_right, right);
                    max_bottom = std::max(max_bottom, bottom);
                }
            }
            if (snapshot.screen_width <= 0) {
                snapshot.screen_width = max_right;
            }
            if (snapshot.screen_height <= 0) {
                snapshot.screen_height = max_bottom;
            }
        }
    } catch (...) {
        return snapshot;
    }
    return snapshot;
}

ObservationFingerprint build_observation_fingerprint(const ObservationSnapshot& snapshot) {
    ObservationFingerprint fingerprint;
    fingerprint.activity = snapshot.activity;
    fingerprint.summary = truncate_runtime_text(snapshot.summary, 120);

    std::set<std::string> seen_labels;
    for (const auto& candidate : snapshot.actionable_candidates) {
        std::string label = trim_whitespace(candidate.label);
        if (label.empty()) {
            label = truncate_runtime_text(candidate.match_text, 60);
        }
        if (label.empty()) {
            continue;
        }
        const std::string normalized = normalize_for_runtime_match(label);
        if (normalized.empty() || seen_labels.count(normalized) > 0) {
            continue;
        }
        seen_labels.insert(normalized);
        fingerprint.actionable_labels.push_back(truncate_runtime_text(label, 48));
        if (fingerprint.actionable_labels.size() >= 6) {
            break;
        }
    }

    std::set<std::string> seen_conflicts;
    for (const auto& conflict : snapshot.warning_conflicts) {
        const std::string code = conflict.code.empty() ? conflict.message : conflict.code;
        const std::string normalized = normalize_for_runtime_match(code);
        if (normalized.empty() || seen_conflicts.count(normalized) > 0) {
            continue;
        }
        seen_conflicts.insert(normalized);
        fingerprint.conflict_codes.push_back(truncate_runtime_text(code, 48));
        if (fingerprint.conflict_codes.size() >= 4) {
            break;
        }
    }

    std::ostringstream stream;
    stream << normalize_for_runtime_match(snapshot.activity) << "|";
    stream << normalize_for_runtime_match(truncate_runtime_text(snapshot.summary, 160)) << "|";
    for (const auto& label : fingerprint.actionable_labels) {
        stream << normalize_for_runtime_match(label) << ",";
    }
    stream << "|";
    for (const auto& code : fingerprint.conflict_codes) {
        stream << normalize_for_runtime_match(code) << ",";
    }
    fingerprint.value = truncate_runtime_text(stream.str(), 512);
    return fingerprint;
}

std::string join_fingerprint_values(const std::vector<std::string>& values) {
    std::ostringstream stream;
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            stream << ",";
        }
        stream << values[i];
    }
    return stream.str();
}

std::string build_navigation_attempt_cache_key(int step_index,
                                               const std::string& fingerprint) {
    if (step_index < 0 || fingerprint.empty()) {
        return "";
    }
    std::ostringstream stream;
    stream << step_index << "|" << truncate_runtime_text(fingerprint, 256);
    return stream.str();
}

std::vector<CanonicalCandidate> build_canonical_candidates(
        const std::vector<ObservationCandidate>& candidates);
std::string build_canonical_candidate_summary(const std::vector<CanonicalCandidate>& candidates,
                                              size_t max_count);
std::string build_navigation_observation_summary(const std::string& content,
                                                 const std::string& canonical_candidate_summary);
std::string build_readout_observation_summary(const std::string& content);
std::string build_navigation_attempt_cache_key(int step_index,
                                               const std::string& fingerprint);
bool candidate_label_matches_step_target_exactly(const ObservationCandidate& candidate,
                                                 const SkillStepHint& step);

std::string activity_simple_name(const std::string& activity);
bool matches_activity_name(const std::string& actual_activity,
                           const std::string& expected_activity);
bool snapshot_has_step_target_visible(const ObservationSnapshot& snapshot,
                                      const SkillStepHint& step);

int step_page_match_score(const ObservationSnapshot& snapshot, const SkillStepHint& step) {
    // Keep generic step matching conservative. Full visible_text often contains
    // bottom-tab labels or nearby card titles that should not be treated as
    // proof that we already reached a later page.
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    const bool activity_hit =
            !step.activity.empty() && matches_activity_name(snapshot.activity, step.activity);
    const bool page_hit = !step.page.empty() && contains_runtime_match(page_context, step.page);
    const bool target_visible = snapshot_has_step_target_visible(snapshot, step);
    if (step.readout) {
        if (page_hit && target_visible) {
            return 5;
        }
        if (target_visible) {
            return 4;
        }
        if (page_hit) {
            return 2;
        }
    }
    if (page_hit && target_visible) {
        return 4;
    }
    if (page_hit) {
        return 3;
    }
    if (activity_hit && target_visible) {
        return 2;
    }
    if (activity_hit) {
        return 1;
    }
    if (step.activity.empty() && step.page.empty()) {
        return target_visible ? 2 : 1;
    }
    return 0;
}

bool matches_step_page(const ObservationSnapshot& snapshot, const SkillStepHint& step) {
    return step_page_match_score(snapshot, step) > 0;
}

bool snapshot_has_step_target_visible(const ObservationSnapshot& snapshot,
                                      const SkillStepHint& step) {
    const std::string page_context = snapshot.activity + " " + snapshot.summary + " " + snapshot.visible_text;
    if (!step.target.empty() && contains_runtime_match(page_context, step.target)) {
        return true;
    }
    for (const auto& alias : step.aliases) {
        if (!alias.empty() && contains_runtime_match(page_context, alias)) {
            return true;
        }
    }
    return false;
}

bool step_requires_visible_target_for_arrival(const SkillStepHint& step) {
    return (step.action == "tap" || step.action == "open")
            && (!step.target.empty() || !step.aliases.empty());
}

bool matches_goal_page(const ObservationSnapshot& snapshot, const std::string& goal) {
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    return contains_runtime_match(page_context, goal);
}

std::string visible_observation_context(const ObservationSnapshot& snapshot) {
    return snapshot.activity + " " + snapshot.summary + " " + snapshot.visible_text;
}

bool matches_any_predicate(const std::string& text,
                           const std::vector<std::string>& predicates) {
    if (predicates.empty()) {
        return true;
    }
    for (const auto& predicate : predicates) {
        if (!predicate.empty() && contains_runtime_match(text, predicate)) {
            return true;
        }
    }
    return false;
}

bool is_weak_completion_signal(const std::string& predicate) {
    const std::string normalized = normalize_for_runtime_match(predicate);
    if (normalized.empty()) {
        return true;
    }
    static const std::set<std::string> weak_signals = {
        u8"\u6210\u529f",      // 成功
        u8"\u4eca\u5929",      // 今天
        u8"\u4eca\u65e5",      // 今日
        u8"\u9875\u9762",      // 页面
        u8"\u5185\u5bb9",      // 内容
        u8"\u5217\u8868",      // 列表
        u8"\u67e5\u770b",      // 查看
        "success",
        "done",
        "read",
        "content",
        "page"
    };
    return weak_signals.count(normalized) > 0;
}

bool is_time_completion_signal(const std::string& predicate) {
    const std::string normalized = normalize_for_runtime_match(predicate);
    return normalized.find(u8"\u65f6\u95f4") != std::string::npos
            || normalized.find("time") != std::string::npos;
}

bool contains_clock_time_value(const std::string& text) {
    for (size_t i = 0; i + 2 < text.size(); ++i) {
        const unsigned char ch = static_cast<unsigned char>(text[i]);
        if (!std::isdigit(ch)) {
            continue;
        }
        size_t j = i;
        int hour_digits = 0;
        while (j < text.size()
                && std::isdigit(static_cast<unsigned char>(text[j]))
                && hour_digits < 2) {
            j++;
            hour_digits++;
        }
        if (hour_digits == 0 || j >= text.size()) {
            continue;
        }
        const unsigned char separator = static_cast<unsigned char>(text[j]);
        const bool ascii_colon = separator == ':';
        const bool full_width_colon = j + 2 < text.size()
                && static_cast<unsigned char>(text[j]) == 0xEF
                && static_cast<unsigned char>(text[j + 1]) == 0xBC
                && static_cast<unsigned char>(text[j + 2]) == 0x9A;
        if (!ascii_colon && !full_width_colon) {
            continue;
        }
        j += ascii_colon ? 1 : 3;
        int minute_digits = 0;
        while (j < text.size()
                && std::isdigit(static_cast<unsigned char>(text[j]))
                && minute_digits < 2) {
            j++;
            minute_digits++;
        }
        if (minute_digits == 2) {
            return true;
        }
    }
    return false;
}

bool matches_success_predicate_for_stop(const std::string& text,
                                        const std::vector<std::string>& predicates) {
    for (const auto& predicate : predicates) {
        if (predicate.empty() || !contains_runtime_match(text, predicate)) {
            continue;
        }
        if (is_time_completion_signal(predicate) && !contains_clock_time_value(text)) {
            continue;
        }
        return true;
    }
    return false;
}

bool matches_any_strong_completion_predicate(const std::string& text,
                                             const std::vector<std::string>& predicates) {
    for (const auto& predicate : predicates) {
        if (is_weak_completion_signal(predicate) || is_time_completion_signal(predicate)) {
            continue;
        }
        if (contains_runtime_match(text, predicate)) {
            return true;
        }
    }
    return false;
}

bool matches_stop_condition(const ObservationSnapshot& snapshot,
                            const StopConditionSpec& stop_condition,
                            const std::string& goal) {
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    const std::string visible_context = visible_observation_context(snapshot);
    const bool page_hit = matches_any_predicate(page_context, stop_condition.page_predicates);
    const bool content_hit = stop_condition.content_predicates.empty()
            ? (matches_goal_page(snapshot, goal) || contains_runtime_match(visible_context, goal))
            : matches_any_predicate(visible_context, stop_condition.content_predicates);
    const bool success_hit = !stop_condition.success_signals.empty()
            && matches_success_predicate_for_stop(visible_context, stop_condition.success_signals);
    const bool strong_success_hit = matches_any_strong_completion_predicate(
            visible_context, stop_condition.success_signals);
    const bool failure_hit = !stop_condition.failure_signals.empty()
            && matches_any_predicate(visible_context, stop_condition.failure_signals);
    if (failure_hit && !strong_success_hit) {
        return false;
    }
    if (strong_success_hit) {
        return true;
    }
    if (success_hit && page_hit) {
        return true;
    }
    if (page_hit && content_hit && stop_condition.success_signals.empty()) {
        return true;
    }
    if (page_hit
            && stop_condition.requires_readout
            && !stop_condition.content_predicates.empty()
            && matches_goal_page(snapshot, goal)) {
        return true;
    }
    return false;
}

bool is_mock_observation_result(const nlohmann::json& json) {
    if (!json.is_object()) {
        return false;
    }
    const bool mock = json.value("mock", false);
    const std::string source = json.value("source", "");
    const std::string selection_status = json.value("selectionStatus", "");
    if (mock) {
        return true;
    }
    if (source == "web_dom") {
        return true;
    }
    if (selection_status != "FALLBACK_RESOLVED") {
        return false;
    }
    const bool missing_snapshot = !json.contains("snapshotId") || json.value("snapshotId", "").empty();
    const bool missing_activity = json.value("activityClassName", "").empty();
    const bool missing_native = !json.contains("nativeViewXml") || json["nativeViewXml"].is_null();
    const bool missing_hybrid = !json.contains("hybridObservation") || !json["hybridObservation"].is_object();
    return (missing_snapshot && missing_activity) || (missing_native && missing_hybrid);
}

