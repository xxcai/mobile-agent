package com.hh.agent.android.route;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QuerySourceBackedMiniAppRouteBridge implements MiniAppRouteBridge {
    private static final String TAG = "MiniAppRouteBridge";

    private final MiniAppQuerySource miniAppQuerySource;

    public QuerySourceBackedMiniAppRouteBridge(MiniAppQuerySource miniAppQuerySource) {
        if (miniAppQuerySource == null) {
            throw new IllegalArgumentException("miniAppQuerySource cannot be null");
        }
        this.miniAppQuerySource = miniAppQuerySource;
    }

    @Override
    public List<MiniAppRouteRecord> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<MiniAppQueryResult> results = miniAppQuerySource.search(query);
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }
            List<MiniAppRouteRecord> records = new ArrayList<>();
            for (MiniAppQueryResult result : results) {
                records.add(new MiniAppRouteRecord(
                        result.getUri(),
                        result.getAppName(),
                        result.getDescription()));
            }
            return records;
        } catch (Exception exception) {
            Log.e(TAG, "Mini app query failed for query=" + query, exception);
            return Collections.emptyList();
        }
    }
}
