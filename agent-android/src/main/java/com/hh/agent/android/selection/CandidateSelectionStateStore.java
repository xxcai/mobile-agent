package com.hh.agent.android.selection;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CandidateSelectionStateStore {
    private final Map<String, JSONObject> selectionsBySession = new LinkedHashMap<>();

    public synchronized void save(String sessionKey, JSONObject selectionJson) {
        String normalizedSessionKey = normalizeSessionKey(sessionKey);
        if (normalizedSessionKey == null || selectionJson == null || selectionJson.length() == 0) {
            return;
        }
        selectionsBySession.put(normalizedSessionKey, copy(selectionJson));
    }

    public synchronized JSONObject get(String sessionKey) {
        String normalizedSessionKey = normalizeSessionKey(sessionKey);
        if (normalizedSessionKey == null) {
            return null;
        }
        JSONObject selection = selectionsBySession.get(normalizedSessionKey);
        return selection == null ? null : copy(selection);
    }

    public synchronized void clear(String sessionKey) {
        String normalizedSessionKey = normalizeSessionKey(sessionKey);
        if (normalizedSessionKey == null) {
            return;
        }
        selectionsBySession.remove(normalizedSessionKey);
    }

    private String normalizeSessionKey(String sessionKey) {
        if (sessionKey == null) {
            return null;
        }
        String trimmed = sessionKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JSONObject copy(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to copy candidate selection state", exception);
        }
    }
}
