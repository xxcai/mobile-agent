package com.hh.agent.app;

import com.hh.agent.android.route.MiniAppRouteBridge;
import com.hh.agent.android.route.MiniAppRouteRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MockMiniAppRouteBridge implements MiniAppRouteBridge {
    private final List<MiniAppRouteRecord> records;

    MockMiniAppRouteBridge() {
        List<MiniAppRouteRecord> seed = new ArrayList<>();
        seed.add(new MiniAppRouteRecord(
                "h5://1001001",
                "费控报销",
                "费用报销入口"));
        seed.add(new MiniAppRouteRecord(
                "h5://1001002",
                "差旅申请",
                "差旅申请入口"));
        records = Collections.unmodifiableList(seed);
    }

    @Override
    public List<MiniAppRouteRecord> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = query.trim().toLowerCase();
        List<MiniAppRouteRecord> matches = new ArrayList<>();
        for (MiniAppRouteRecord record : records) {
            String description = record.getDescription() == null ? "" : record.getDescription();
            String haystack = (record.getTitle() + " " + description)
                    .toLowerCase();
            if (haystack.contains(normalized)) {
                matches.add(record);
            }
        }
        return matches;
    }
}
