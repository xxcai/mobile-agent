package com.hh.agent.android.route;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QuerySourceBackedWeCodeRouteBridge implements WeCodeRouteBridge {
    private static final String TAG = "WeCodeRouteBridge";

    private final WeCodeQuerySource weCodeQuerySource;

    public QuerySourceBackedWeCodeRouteBridge(WeCodeQuerySource weCodeQuerySource) {
        if (weCodeQuerySource == null) {
            throw new IllegalArgumentException("weCodeQuerySource cannot be null");
        }
        this.weCodeQuerySource = weCodeQuerySource;
    }

    @Override
    public List<WeCodeRouteRecord> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<WeCodeQueryResult> results = weCodeQuerySource.search(query);
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }
            List<WeCodeRouteRecord> records = new ArrayList<>();
            for (WeCodeQueryResult result : results) {
                records.add(new WeCodeRouteRecord(
                        result.getUri(),
                        result.getAppName(),
                        result.getDescription()));
            }
            return records;
        } catch (Exception exception) {
            Log.e(TAG, "WeCode query failed for query=" + query, exception);
            return Collections.emptyList();
        }
    }
}
