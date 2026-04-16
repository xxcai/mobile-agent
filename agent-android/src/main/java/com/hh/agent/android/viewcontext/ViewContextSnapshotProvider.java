package com.hh.agent.android.viewcontext;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Host-process-only view context provider used in Step 02 before accessibility support.
 */
public final class ViewContextSnapshotProvider {

    private static final String INTERACTION_DOMAIN_NATIVE = "native";
    private static final String SOURCE_NATIVE_XML = "native_xml";
    private static final String OBSERVATION_SCOPE_CURRENT_TURN = "current_turn";

    private static final int DISCOVERY_MAX_TEXTS = 10;
    private static final int DISCOVERY_MAX_CONTROLS = 12;
    private static final int DISCOVERY_MAX_SECTIONS = 6;
    private static final int DISCOVERY_MAX_ITEMS = 8;
    private static final int DISCOVERY_MAX_ACTIONABLE = 14;
    private static final int DISCOVERY_MAX_CONFLICTS = 6;
    private static final int DISCOVERY_MAX_NATIVE_NODES = 32;
    private static final int FOLLOW_UP_MAX_TEXTS = 4;
    private static final int FOLLOW_UP_MAX_CONTROLS = 5;
    private static final int FOLLOW_UP_MAX_SECTIONS = 2;
    private static final int FOLLOW_UP_MAX_ITEMS = 4;
    private static final int READOUT_MAX_TEXTS = 10;
    private static final int READOUT_MAX_CONTROLS = 6;
    private static final int READOUT_MAX_SECTIONS = 4;
    private static final int READOUT_MAX_ITEMS = 8;
    private static final int FOLLOW_UP_MAX_ACTIONABLE = 6;
    private static final int FOLLOW_UP_MAX_CONFLICTS = 3;
    private static final int READOUT_MAX_ACTIONABLE = 4;
    private static final int READOUT_MAX_CONFLICTS = 2;
    private static final int FOLLOW_UP_MAX_NATIVE_NODES = 14;
    private static final int READOUT_MAX_NATIVE_NODES = 10;

    private static final Pattern HIERARCHY_ACTIVITY_PATTERN =
            Pattern.compile("<hierarchy[^>]*activity=\"([^\"]+)\"");
    private static final Pattern NODE_TAG_PATTERN = Pattern.compile("<node\\s+[^>]*>");

    private ViewContextSnapshotProvider() {
    }

    public static ToolResult getCurrentNativeViewSnapshot(String targetHint) {
        return getCurrentNativeViewSnapshot(targetHint, false, ObservationDetailMode.DISCOVERY);
    }

    public static ToolResult getCurrentNativeViewSnapshot(String targetHint, boolean includeRawFallback) {
        return getCurrentNativeViewSnapshot(targetHint, includeRawFallback, ObservationDetailMode.DISCOVERY);
    }

    public static ToolResult getCurrentNativeViewSnapshot(String targetHint,
                                                          boolean includeRawFallback,
                                                          ObservationDetailMode detailMode) {
        Activity activity = InProcessViewHierarchyDumper.getCurrentStableForegroundActivity();
        if (activity == null) {
            AgentLogs.warn("ViewContextSnapshotProvider", "foreground_activity_missing", "target_hint=" + sanitize(targetHint));
            return ToolResult.error("view_context_unavailable", "Foreground activity did not stabilize in time");
        }

        InProcessViewHierarchyDumper.DumpResult dumpResult =
                InProcessViewHierarchyDumper.dumpHierarchy(activity, targetHint);
        if (!dumpResult.success) {
            AgentLogs.warn("ViewContextSnapshotProvider", "native_dump_failed",
                    "activity=" + dumpResult.activityClassName + " message=" + dumpResult.errorMessage);
            return ToolResult.error("view_context_unavailable", dumpResult.errorMessage);
        }

        ScreenSnapshotAnalysis visualAnalysis = tryAnalyzeScreenSnapshot(activity, targetHint, detailMode);
        return buildObservationToolResult(
                SOURCE_NATIVE_XML,
                targetHint,
                dumpResult.activityClassName,
                "in_process_view_tree",
                dumpResult.xml,
                visualAnalysis,
                includeRawFallback,
                detailMode
        );
    }

    public static ToolResult getCurrentScreenSnapshot(String targetHint) {
        return getCurrentScreenSnapshot(targetHint, false, ObservationDetailMode.DISCOVERY);
    }

    public static ToolResult getCurrentScreenSnapshot(String targetHint, boolean includeRawFallback) {
        return getCurrentScreenSnapshot(targetHint, includeRawFallback, ObservationDetailMode.DISCOVERY);
    }

    public static ToolResult getCurrentScreenSnapshot(String targetHint,
                                                      boolean includeRawFallback,
                                                      ObservationDetailMode detailMode) {
        return ScreenSnapshotObservationProvider.createDefault().getCurrentSnapshot(targetHint, includeRawFallback, detailMode);
    }

    static ToolResult buildObservationToolResult(String source,
                                                 @Nullable String targetHint,
                                                 String activityClassName,
                                                 String observationMode,
                                                 @Nullable String nativeViewXml,
                                                 @Nullable ScreenSnapshotAnalysis analysis) {
        return buildObservationToolResult(
                source,
                targetHint,
                activityClassName,
                observationMode,
                nativeViewXml,
                analysis,
                false,
                ObservationDetailMode.DISCOVERY
        );
    }

    static ToolResult buildObservationToolResult(String source,
                                                 @Nullable String targetHint,
                                                 String activityClassName,
                                                 String observationMode,
                                                 @Nullable String nativeViewXml,
                                                 @Nullable ScreenSnapshotAnalysis analysis,
                                                 boolean includeRawFallback) {
        return buildObservationToolResult(
                source,
                targetHint,
                activityClassName,
                observationMode,
                nativeViewXml,
                analysis,
                includeRawFallback,
                ObservationDetailMode.DISCOVERY
        );
    }

    static ToolResult buildObservationToolResult(String source,
                                                 @Nullable String targetHint,
                                                 String activityClassName,
                                                 String observationMode,
                                                 @Nullable String nativeViewXml,
                                                 @Nullable ScreenSnapshotAnalysis analysis,
                                                 boolean includeRawFallback,
                                                 @Nullable ObservationDetailMode detailMode) {
        ObservationDetailMode safeDetailMode = detailMode != null ? detailMode : ObservationDetailMode.DISCOVERY;
        String visualObservationJson = analysis != null ? analysis.compactObservationJson : null;
        String screenSnapshotRef = analysis != null ? analysis.screenSnapshotRef : null;
        Integer imageWidth = analysis != null ? analysis.imageWidth : null;
        Integer imageHeight = analysis != null ? analysis.imageHeight : null;
        String fullHybridObservationJson = HybridObservationComposer.compose(
                source,
                activityClassName,
                targetHint,
                nativeViewXml,
                visualObservationJson,
                imageWidth != null ? imageWidth : 0,
                imageHeight != null ? imageHeight : 0
        );
        String reducedNativeViewXml = reduceNativeViewXml(nativeViewXml, targetHint, safeDetailMode, includeRawFallback);
        String hybridObservationJson = reduceHybridObservation(fullHybridObservationJson, safeDetailMode, includeRawFallback);
        String compactObservationJson = reduceCompactObservation(visualObservationJson, safeDetailMode, includeRawFallback);
        UnifiedViewObservation unifiedObservation = tryBuildUnifiedObservation(
                source,
                activityClassName,
                INTERACTION_DOMAIN_NATIVE,
                targetHint,
                null,
                null,
                nativeViewXml,
                null,
                visualObservationJson,
                fullHybridObservationJson,
                screenSnapshotRef
        );

        ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.createSnapshot(
                activityClassName,
                source,
                INTERACTION_DOMAIN_NATIVE,
                targetHint,
                unifiedObservation,
                nativeViewXml,
                null,
                null,
                null,
                visualObservationJson,
                screenSnapshotRef,
                fullHybridObservationJson
        );

        AgentLogs.info("ViewContextSnapshotProvider", "observation_built",
                "source=" + source
                        + " activity=" + activityClassName
                        + " observation_mode=" + observationMode
                        + " detail_mode=" + safeDetailMode.wireValue()
                        + " visual_mode=" + (analysis != null ? analysis.observationMode : "none")
                        + " native_xml_length=" + lengthOf(reducedNativeViewXml)
                        + " compact_length=" + lengthOf(compactObservationJson)
                        + " hybrid_length=" + lengthOf(hybridObservationJson)
                        + " raw_included=" + includeRawFallback
                        + " snapshot_id=" + snapshot.snapshotId);

        return ToolResult.success()
                .with("source", source)
                .with("interactionDomain", INTERACTION_DOMAIN_NATIVE)
                .with("mock", false)
                .with("targetHint", targetHint)
                .with("activityClassName", activityClassName)
                .with("observationMode", observationMode)
                .with("observationDetailMode", safeDetailMode.wireValue())
                .with("visualObservationMode", analysis != null ? analysis.observationMode : null)
                .with("snapshotId", snapshot.snapshotId)
                .with("snapshotCreatedAtEpochMs", snapshot.createdAtEpochMs)
                .with("snapshotScope", OBSERVATION_SCOPE_CURRENT_TURN)
                .with("snapshotCurrentTurnOnly", snapshot.currentTurnOnly)
                .with("rawFallbackIncluded", includeRawFallback)
                .withJson("uiTree", snapshot.uiTreeJson)
                .withJson("screenElements", snapshot.screenElementsJson)
                .with("pageSummary", snapshot.pageSummary)
                .withJson("quality", snapshot.qualityJson)
                .withJson("raw", snapshot.rawJson)
                .with("nativeViewXml", reducedNativeViewXml)
                .with("webDom", (String) null)
                .with("screenSnapshot", screenSnapshotRef)
                .with("screenSnapshotWidth", imageWidth)
                .with("screenSnapshotHeight", imageHeight)
                .withJson("screenVisionCompact", compactObservationJson)
                .withJson("screenVisionRaw", includeRawFallback && analysis != null ? analysis.rawObservationJson : null)
                .withJson("hybridObservation", hybridObservationJson);
    }

    @Nullable
    private static UnifiedViewObservation tryBuildUnifiedObservation(String source,
                                                                     String activityClassName,
                                                                     String interactionDomain,
                                                                     @Nullable String targetHint,
                                                                     @Nullable String pageUrl,
                                                                     @Nullable String pageTitle,
                                                                     @Nullable String nativeViewXml,
                                                                     @Nullable String webDom,
                                                                     @Nullable String visualObservationJson,
                                                                     @Nullable String hybridObservationJson,
                                                                     @Nullable String screenSnapshot) {
        try {
            return UnifiedViewObservationFacade.build(
                    source,
                    activityClassName,
                    interactionDomain,
                    targetHint,
                    pageUrl,
                    pageTitle,
                    nativeViewXml,
                    webDom,
                    visualObservationJson,
                    hybridObservationJson,
                    screenSnapshot
            );
        } catch (Exception exception) {
            AgentLogs.warn("ViewContextSnapshotProvider", "canonical_observation_failed",
                    "source=" + source
                            + " activity=" + activityClassName
                            + " message=" + exception.getMessage());
            return null;
        }
    }

    @Nullable
    private static ScreenSnapshotAnalysis tryAnalyzeScreenSnapshot(Activity activity,
                                                                   @Nullable String targetHint,
                                                                   ObservationDetailMode detailMode) {
        ScreenSnapshotAnalyzer analyzer = ScreenSnapshotAnalyzerHolder.getInstance().getAnalyzer();
        if (analyzer == null) {
            AgentLogs.debug("ViewContextSnapshotProvider", "screen_snapshot_skipped",
                    "activity=" + activity.getClass().getName()
                            + " detail_mode=" + detailMode.wireValue()
                            + " reason=no_analyzer");
            return null;
        }
        long startMs = System.currentTimeMillis();
        try {
            ScreenSnapshotAnalysis analysis = analyzer.analyze(activity, targetHint);
            long durationMs = System.currentTimeMillis() - startMs;
            AgentLogs.info("ViewContextSnapshotProvider", "screen_snapshot_complete",
                    "activity=" + activity.getClass().getName()
                            + " detail_mode=" + detailMode.wireValue()
                            + " duration_ms=" + durationMs
                            + " visual_mode=" + analysis.observationMode
                            + " compact_length=" + lengthOf(analysis.compactObservationJson)
                            + " image=" + analysis.imageWidth + "x" + analysis.imageHeight);
            return analysis;
        } catch (Throwable throwable) {
            long durationMs = System.currentTimeMillis() - startMs;
            AgentLogs.warn("ViewContextSnapshotProvider", "screen_snapshot_failed",
                    "activity=" + activity.getClass().getName()
                            + " detail_mode=" + detailMode.wireValue()
                            + " duration_ms=" + durationMs
                            + " message=" + throwable.getMessage());
            return null;
        }
    }

    private static String reduceHybridObservation(@Nullable String hybridObservationJson,
                                                  ObservationDetailMode detailMode,
                                                  boolean includeRawFallback) {
        if (includeRawFallback || !hasText(hybridObservationJson)) {
            return hybridObservationJson;
        }
        switch (detailMode) {
            case DISCOVERY:
                return reduceHybridDiscovery(hybridObservationJson);
            case FOLLOW_UP:
                return reduceHybridFollowUp(hybridObservationJson);
            case READOUT:
                return reduceHybridReadout(hybridObservationJson);
            default:
                return reduceHybridDiscovery(hybridObservationJson);
        }
    }

    private static String reduceCompactObservation(@Nullable String screenVisionCompactJson,
                                                   ObservationDetailMode detailMode,
                                                   boolean includeRawFallback) {
        if (includeRawFallback || !hasText(screenVisionCompactJson)) {
            return screenVisionCompactJson;
        }
        switch (detailMode) {
            case FOLLOW_UP:
                return reduceCompact(screenVisionCompactJson,
                        FOLLOW_UP_MAX_TEXTS,
                        FOLLOW_UP_MAX_CONTROLS,
                        FOLLOW_UP_MAX_SECTIONS,
                        FOLLOW_UP_MAX_ITEMS,
                        false);
            case READOUT:
                return reduceCompact(screenVisionCompactJson,
                        READOUT_MAX_TEXTS,
                        READOUT_MAX_CONTROLS,
                        READOUT_MAX_SECTIONS,
                        READOUT_MAX_ITEMS,
                        false);
            case DISCOVERY:
            default:
                return reduceCompact(screenVisionCompactJson,
                        DISCOVERY_MAX_TEXTS,
                        DISCOVERY_MAX_CONTROLS,
                        DISCOVERY_MAX_SECTIONS,
                        DISCOVERY_MAX_ITEMS,
                        true);
        }
    }

    private static String reduceNativeViewXml(@Nullable String nativeViewXml,
                                              @Nullable String targetHint,
                                              ObservationDetailMode detailMode,
                                              boolean includeRawFallback) {
        if (includeRawFallback || !hasText(nativeViewXml)) {
            return nativeViewXml;
        }
        int maxNodes;
        switch (detailMode) {
            case READOUT:
                maxNodes = READOUT_MAX_NATIVE_NODES;
                break;
            case FOLLOW_UP:
                maxNodes = FOLLOW_UP_MAX_NATIVE_NODES;
                break;
            case DISCOVERY:
            default:
                maxNodes = DISCOVERY_MAX_NATIVE_NODES;
                break;
        }
        String excerpt = buildNativeExcerpt(
                nativeViewXml,
                targetHint,
                maxNodes,
                detailMode
        );
        return hasText(excerpt) ? excerpt : nativeViewXml;
    }

    private static String stripHybridDebug(@Nullable String hybridObservationJson, boolean includeRawFallback) {
        if (includeRawFallback || !hasText(hybridObservationJson)) {
            return hybridObservationJson;
        }
        try {
            JSONObject source = new JSONObject(hybridObservationJson);
            source.remove("debug");
            return source.toString();
        } catch (Exception ignored) {
            return hybridObservationJson;
        }
    }

    private static String reduceHybridFollowUp(@Nullable String hybridObservationJson) {
        if (!hasText(hybridObservationJson)) {
            return hybridObservationJson;
        }
        try {
            JSONObject source = new JSONObject(hybridObservationJson);
            JSONObject reduced = new JSONObject();
            copy(source, reduced, "schemaVersion");
            copy(source, reduced, "mode");
            copy(source, reduced, "primarySource");
            copy(source, reduced, "activityClassName");
            copy(source, reduced, "targetHint");
            copy(source, reduced, "summary");
            copy(source, reduced, "executionHint");
            copy(source, reduced, "page");
            copy(source, reduced, "availableSignals");
            copy(source, reduced, "quality");
            reduced.put("actionableNodes", trimHybridActionableNodes(source.optJSONArray("actionableNodes"), FOLLOW_UP_MAX_ACTIONABLE));
            reduced.put("conflicts", trimHybridConflicts(source.optJSONArray("conflicts"), FOLLOW_UP_MAX_CONFLICTS));
            return reduced.toString();
        } catch (Exception ignored) {
            return hybridObservationJson;
        }
    }

    private static String reduceHybridDiscovery(@Nullable String hybridObservationJson) {
        if (!hasText(hybridObservationJson)) {
            return hybridObservationJson;
        }
        try {
            JSONObject source = new JSONObject(hybridObservationJson);
            JSONObject reduced = new JSONObject();
            copy(source, reduced, "schemaVersion");
            copy(source, reduced, "mode");
            copy(source, reduced, "primarySource");
            copy(source, reduced, "activityClassName");
            copy(source, reduced, "targetHint");
            copy(source, reduced, "summary");
            copy(source, reduced, "executionHint");
            copy(source, reduced, "page");
            copy(source, reduced, "availableSignals");
            copy(source, reduced, "quality");
            reduced.put("actionableNodes", trimHybridActionableNodes(source.optJSONArray("actionableNodes"), DISCOVERY_MAX_ACTIONABLE));
            reduced.put("conflicts", trimHybridConflicts(source.optJSONArray("conflicts"), DISCOVERY_MAX_CONFLICTS));
            return reduced.toString();
        } catch (Exception ignored) {
            return hybridObservationJson;
        }
    }

    private static String reduceHybridReadout(@Nullable String hybridObservationJson) {
        if (!hasText(hybridObservationJson)) {
            return hybridObservationJson;
        }
        try {
            JSONObject source = new JSONObject(hybridObservationJson);
            JSONObject reduced = new JSONObject();
            copy(source, reduced, "schemaVersion");
            copy(source, reduced, "mode");
            copy(source, reduced, "primarySource");
            copy(source, reduced, "activityClassName");
            copy(source, reduced, "targetHint");
            copy(source, reduced, "summary");
            copy(source, reduced, "page");
            copy(source, reduced, "availableSignals");
            copy(source, reduced, "quality");
            reduced.put("actionableNodes", trimHybridReadoutActionableNodes(source.optJSONArray("actionableNodes"), READOUT_MAX_ACTIONABLE));
            reduced.put("conflicts", trimHybridReadoutConflicts(source.optJSONArray("conflicts"), READOUT_MAX_CONFLICTS));
            return reduced.toString();
        } catch (Exception ignored) {
            return hybridObservationJson;
        }
    }

    private static JSONArray trimHybridActionableNodes(@Nullable JSONArray array, int limit) throws Exception {
        JSONArray reduced = new JSONArray();
        if (array == null) {
            return reduced;
        }
        for (int i = 0; i < Math.min(limit, array.length()); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject copy = new JSONObject();
            putIfPresent(item, copy, "id");
            putIfPresent(item, copy, "source");
            putIfPresent(item, copy, "text");
            putIfPresent(item, copy, "contentDescription");
            putIfPresent(item, copy, "className");
            putIfPresent(item, copy, "resourceId");
            putIfPresent(item, copy, "visionLabel");
            putIfPresent(item, copy, "visionRole");
            putIfPresent(item, copy, "nativeNodeIndex");
            putIfPresent(item, copy, "region");
            putIfPresent(item, copy, "anchorType");
            putIfPresent(item, copy, "containerRole");
            putIfPresent(item, copy, "parentSemanticContext");
            putIfPresent(item, copy, "bounds");
            putIfPresent(item, copy, "score");
            putIfPresent(item, copy, "actionability");
            putIfPresent(item, copy, "matchScore");
            putIfPresent(item, copy, "clickable");
            putIfPresent(item, copy, "containerClickable");
            putIfPresent(item, copy, "enabled");
            putIfPresent(item, copy, "selected");
            putIfPresent(item, copy, "badgeLike");
            putIfPresent(item, copy, "numericLike");
            putIfPresent(item, copy, "decorativeLike");
            putIfPresent(item, copy, "repeatGroup");
            reduced.put(copy);
        }
        return reduced;
    }

    private static JSONArray trimHybridConflicts(@Nullable JSONArray array, int limit) throws Exception {
        JSONArray reduced = new JSONArray();
        if (array == null) {
            return reduced;
        }
        for (int i = 0; i < Math.min(limit, array.length()); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject copy = new JSONObject();
            putIfPresent(item, copy, "code");
            putIfPresent(item, copy, "severity");
            putIfPresent(item, copy, "message");
            putIfPresent(item, copy, "bounds");
            putIfPresent(item, copy, "nativeNodeIndex");
            reduced.put(copy);
        }
        return reduced;
    }

    private static JSONArray trimHybridReadoutActionableNodes(@Nullable JSONArray array, int limit) throws Exception {
        JSONArray reduced = new JSONArray();
        if (array == null) {
            return reduced;
        }
        for (int i = 0; i < Math.min(limit, array.length()); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject copy = new JSONObject();
            putIfPresent(item, copy, "id");
            putIfPresent(item, copy, "source");
            putIfPresent(item, copy, "text");
            putIfPresent(item, copy, "contentDescription");
            putIfPresent(item, copy, "className");
            putIfPresent(item, copy, "resourceId");
            putIfPresent(item, copy, "visionLabel");
            putIfPresent(item, copy, "visionRole");
            putIfPresent(item, copy, "bounds");
            putIfPresent(item, copy, "score");
            putIfPresent(item, copy, "actionability");
            reduced.put(copy);
        }
        return reduced;
    }

    private static JSONArray trimHybridReadoutConflicts(@Nullable JSONArray array, int limit) throws Exception {
        JSONArray reduced = new JSONArray();
        if (array == null) {
            return reduced;
        }
        for (int i = 0; i < Math.min(limit, array.length()); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject copy = new JSONObject();
            putIfPresent(item, copy, "code");
            putIfPresent(item, copy, "severity");
            putIfPresent(item, copy, "message");
            reduced.put(copy);
        }
        return reduced;
    }

    private static String reduceCompact(@Nullable String screenVisionCompactJson,
                                        int maxTexts,
                                        int maxControls,
                                        int maxSections,
                                        int maxItems,
                                        boolean keepDropSummaryDebug) {
        if (!hasText(screenVisionCompactJson)) {
            return screenVisionCompactJson;
        }
        try {
            JSONObject source = new JSONObject(screenVisionCompactJson);
            JSONObject reduced = new JSONObject();
            copy(source, reduced, "summary");
            copy(source, reduced, "page");
            reduced.put("counts", new JSONObject()
                    .put("texts", length(source.optJSONArray("texts")))
                    .put("controls", length(source.optJSONArray("controls")))
                    .put("sections", length(source.optJSONArray("sections")))
                    .put("items", length(source.optJSONArray("items"))));
            reduced.put("texts", trimSignalArray(source.optJSONArray("texts"), maxTexts, true));
            reduced.put("controls", trimSignalArray(source.optJSONArray("controls"), maxControls, false));
            reduced.put("sections", trimRegionArray(source.optJSONArray("sections"), maxSections));
            reduced.put("items", trimRegionArray(source.optJSONArray("items"), maxItems));
            JSONObject debug = source.optJSONObject("debug");
            if (keepDropSummaryDebug && debug != null && debug.has("dropSummary")) {
                JSONObject reducedDebug = new JSONObject();
                reducedDebug.put("dropSummary", debug.opt("dropSummary"));
                reduced.put("debug", reducedDebug);
            }
            return reduced.toString();
        } catch (Exception ignored) {
            return screenVisionCompactJson;
        }
    }

    private static JSONArray trimSignalArray(@Nullable JSONArray array, int limit, boolean textSignal) throws Exception {
        JSONArray reduced = new JSONArray();
        if (array == null) {
            return reduced;
        }
        for (int i = 0; i < Math.min(limit, array.length()); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject copy = new JSONObject();
            putIfPresent(item, copy, "id");
            putIfPresent(item, copy, textSignal ? "text" : "type");
            if (!textSignal) {
                putIfPresent(item, copy, "label");
                putIfPresent(item, copy, "role");
            }
            putIfPresent(item, copy, "bbox");
            putIfPresent(item, copy, "confidence");
            putIfPresent(item, copy, "importance");
            reduced.put(copy);
        }
        return reduced;
    }

    private static JSONArray trimRegionArray(@Nullable JSONArray array, int limit) throws Exception {
        JSONArray reduced = new JSONArray();
        if (array == null) {
            return reduced;
        }
        for (int i = 0; i < Math.min(limit, array.length()); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject copy = new JSONObject();
            putIfPresent(item, copy, "id");
            putIfPresent(item, copy, "type");
            putIfPresent(item, copy, "sectionId");
            putIfPresent(item, copy, "summaryText");
            putIfPresent(item, copy, "bbox");
            putIfPresent(item, copy, "importance");
            putIfPresent(item, copy, "matchedNativeNodeCount");
            putIfPresent(item, copy, "collapsedItemCount");
            putIfPresent(item, copy, "textCount");
            putIfPresent(item, copy, "controlCount");
            reduced.put(copy);
        }
        return reduced;
    }

    private static String buildNativeExcerpt(@Nullable String nativeViewXml,
                                             @Nullable String targetHint,
                                             int limit,
                                             ObservationDetailMode detailMode) {
        if (!hasText(nativeViewXml)) {
            return nativeViewXml;
        }
        Matcher hierarchyMatcher = HIERARCHY_ACTIVITY_PATTERN.matcher(nativeViewXml);
        String activityClassName = hierarchyMatcher.find() ? hierarchyMatcher.group(1) : null;
        Matcher nodeMatcher = NODE_TAG_PATTERN.matcher(nativeViewXml);
        List<NativeExcerptNode> nodes = new ArrayList<>();
        int ordinal = 0;
        while (nodeMatcher.find()) {
            String tag = nodeMatcher.group();
            nodes.add(new NativeExcerptNode(ordinal, tag, scoreNativeTag(tag, targetHint, ordinal)));
            ordinal++;
        }
        if (nodes.isEmpty()) {
            return nativeViewXml;
        }

        LinkedHashSet<Integer> selectedOrdinals = new LinkedHashSet<>();
        final int leadingNodes = detailMode == ObservationDetailMode.DISCOVERY ? 8 : 2;
        for (int i = 0; i < Math.min(leadingNodes, nodes.size()); i++) {
            selectedOrdinals.add(i);
        }

        List<NativeExcerptNode> ranked = new ArrayList<>(nodes);
        Collections.sort(ranked, new Comparator<NativeExcerptNode>() {
            @Override
            public int compare(NativeExcerptNode left, NativeExcerptNode right) {
                int scoreCompare = Integer.compare(right.score, left.score);
                return scoreCompare != 0 ? scoreCompare : Integer.compare(left.ordinal, right.ordinal);
            }
        });
        for (int i = 0; i < ranked.size() && selectedOrdinals.size() < limit; i++) {
            selectedOrdinals.add(ranked.get(i).ordinal);
        }

        List<NativeExcerptNode> ordered = new ArrayList<>();
        for (NativeExcerptNode node : nodes) {
            if (selectedOrdinals.contains(node.ordinal)) {
                ordered.add(node);
            }
        }
        if (ordered.isEmpty()) {
            return nativeViewXml;
        }

        StringBuilder excerpt = new StringBuilder();
        excerpt.append("<hierarchy");
        if (hasText(activityClassName)) {
            excerpt.append(" activity=\"").append(escapeXml(activityClassName)).append("\"");
        }
        excerpt.append(" excerpt=\"true\"")
                .append(" detail-mode=\"").append(detailMode.wireValue()).append("\"")
                .append(" selected-nodes=\"").append(ordered.size()).append("\"")
                .append(" total-nodes=\"").append(nodes.size()).append("\"")
                .append(">");
        for (NativeExcerptNode node : ordered) {
            excerpt.append(toSelfClosingNodeTag(node.tag));
        }
        excerpt.append("</hierarchy>");
        return excerpt.toString();
    }

    private static int scoreNativeTag(String tag, @Nullable String targetHint, int ordinal) {
        int score = 0;
        if (ordinal < 3) {
            score += 140 - (ordinal * 10);
        } else if (ordinal < 8) {
            score += 20;
        }
        if (tag.contains(" text=\"")) {
            score += 36;
        }
        if (tag.contains(" resource-id=\"")) {
            score += 24;
        }
        if (tag.contains("TextView") || tag.contains("Button") || tag.contains("ImageView") || tag.contains("EditText")) {
            score += 12;
        }
        if (containsNormalizedMatch(tag, targetHint)) {
            score += 260;
        }
        if (hintSuggestsProfileEntry(targetHint)) {
            if (isTopLeftTag(tag) && tag.contains("ImageView")) {
                score += 220;
            } else if (isTopLeftTag(tag)) {
                score += 120;
            }
            if (containsNormalizedMatch(tag, "mainTitle") || containsNormalizedMatch(tag, "tab_text")
                    || containsNormalizedMatch(tag, "im_session_cluster_tab_item_name")) {
                score -= 120;
            }
        }
        return score;
    }

    private static boolean hintSuggestsProfileEntry(@Nullable String targetHint) {
        return containsNormalizedMatch(targetHint, "涓汉涓績")
                || containsNormalizedMatch(targetHint, "澶村儚")
                || containsNormalizedMatch(targetHint, "鎴戠殑")
                || containsNormalizedMatch(targetHint, "profile")
                || containsNormalizedMatch(targetHint, "avatar");
    }

    private static boolean isTopLeftTag(@Nullable String tag) {
        if (!hasText(tag)) {
            return false;
        }
        Matcher matcher = Pattern.compile("bounds=\\\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\\\"").matcher(tag);
        if (!matcher.find()) {
            return false;
        }
        try {
            int left = Integer.parseInt(matcher.group(1));
            int top = Integer.parseInt(matcher.group(2));
            int right = Integer.parseInt(matcher.group(3));
            int bottom = Integer.parseInt(matcher.group(4));
            int centerX = (left + right) / 2;
            int centerY = (top + bottom) / 2;
            return centerX <= 360 && centerY <= 520;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean containsNormalizedMatch(@Nullable String haystack, @Nullable String needle) {
        if (!hasText(haystack) || !hasText(needle)) {
            return false;
        }
        return normalizeForMatch(haystack).contains(normalizeForMatch(needle));
    }

    private static String normalizeForMatch(@Nullable String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch >= 128) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    private static String toSelfClosingNodeTag(String openTag) {
        if (openTag == null || openTag.isEmpty()) {
            return "";
        }
        if (openTag.endsWith("/>")) {
            return openTag;
        }
        if (openTag.endsWith(">")) {
            return openTag.substring(0, openTag.length() - 1) + "/>";
        }
        return openTag + "/>";
    }

    private static String escapeXml(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void copy(JSONObject source, JSONObject target, String key) throws Exception {
        if (source.has(key)) {
            target.put(key, source.opt(key));
        }
    }

    private static void putIfPresent(JSONObject source, JSONObject target, String key) throws Exception {
        if (source.has(key)) {
            target.put(key, source.opt(key));
        }
    }

    private static int length(@Nullable JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private static int lengthOf(@Nullable String value) {
        return value == null ? 0 : value.length();
    }

    private static String sanitize(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 64) {
            return normalized;
        }
        return normalized.substring(0, 64) + "...";
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class NativeExcerptNode {
        final int ordinal;
        final String tag;
        final int score;

        NativeExcerptNode(int ordinal, String tag, int score) {
            this.ordinal = ordinal;
            this.tag = tag;
            this.score = score;
        }
    }
}
