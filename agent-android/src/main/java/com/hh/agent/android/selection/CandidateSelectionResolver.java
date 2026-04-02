package com.hh.agent.android.selection;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class CandidateSelectionResolver {

    public JSONObject resolve(JSONObject selectionJson, String selectionText, String domainHint) {
        if (selectionJson == null || selectionJson.length() == 0) {
            return null;
        }
        String normalizedSelection = normalize(selectionText);
        if (normalizedSelection == null) {
            return null;
        }
        String selectionDomain = normalize(selectionJson.optString("domain", null));
        String normalizedDomainHint = normalize(domainHint);
        if (normalizedDomainHint != null && selectionDomain != null && !normalizedDomainHint.equals(selectionDomain)) {
            return null;
        }

        JSONArray items = selectionJson.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return null;
        }

        JSONObject ordinalMatch = resolveOrdinal(items, normalizedSelection);
        if (ordinalMatch != null) {
            return ordinalMatch;
        }
        return resolveAlias(items, normalizedSelection);
    }

    private JSONObject resolveOrdinal(JSONArray items, String normalizedSelection) {
        if ("前者".equals(normalizedSelection)) {
            return items.optJSONObject(0);
        }
        if ("后者".equals(normalizedSelection)) {
            return items.length() >= 2 ? items.optJSONObject(1) : null;
        }
        Integer ordinal = parseOrdinal(normalizedSelection);
        if (ordinal == null || ordinal <= 0 || ordinal > items.length()) {
            return null;
        }
        return items.optJSONObject(ordinal - 1);
    }

    private JSONObject resolveAlias(JSONArray items, String normalizedSelection) {
        JSONObject exactMatch = null;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                continue;
            }
            if (matchesItem(item, normalizedSelection)) {
                if (exactMatch != null) {
                    return null;
                }
                exactMatch = item;
            }
        }
        return exactMatch;
    }

    private boolean matchesItem(JSONObject item, String normalizedSelection) {
        if (matchesText(item.optString("label", null), normalizedSelection)) {
            return true;
        }
        if (matchesText(item.optString("stableKey", null), normalizedSelection)) {
            return true;
        }
        JSONArray aliases = item.optJSONArray("aliases");
        if (aliases == null) {
            return false;
        }
        for (int index = 0; index < aliases.length(); index++) {
            if (matchesText(aliases.optString(index, null), normalizedSelection)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesText(String candidate, String normalizedSelection) {
        String normalizedCandidate = normalize(candidate);
        if (normalizedCandidate == null) {
            return false;
        }
        return normalizedCandidate.equals(normalizedSelection)
                || normalizedCandidate.contains(normalizedSelection)
                || normalizedSelection.contains(normalizedCandidate);
    }

    private Integer parseOrdinal(String normalizedSelection) {
        if (normalizedSelection == null) {
            return null;
        }
        if (normalizedSelection.matches("\\d+")) {
            return Integer.parseInt(normalizedSelection);
        }
        switch (normalizedSelection) {
            case "第一个":
            case "第1个":
            case "第1":
            case "第一个人":
                return 1;
            case "第二个":
            case "第2个":
            case "第2":
                return 2;
            case "第三个":
            case "第3个":
            case "第3":
                return 3;
            default:
                return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
