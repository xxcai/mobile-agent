package com.hh.agent.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DefaultMockMiniAppQuerySource implements MiniAppQuerySource {
    private final List<MiniAppQueryResult> results;

    private DefaultMockMiniAppQuerySource(List<MiniAppQueryResult> results) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
    }

    static MiniAppQuerySource create() {
        List<MiniAppQueryResult> seed = new ArrayList<>();
        seed.add(new MiniAppQueryResult(
                "h5://1001001",
                "费控报销",
                "费用报销入口"));
        seed.add(new MiniAppQueryResult(
                "h5://1001002",
                "差旅申请",
                "差旅申请入口"));
        return new DefaultMockMiniAppQuerySource(seed);
    }

    @Override
    public List<MiniAppQueryResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = query.trim().toLowerCase();
        List<MiniAppQueryResult> matches = new ArrayList<>();
        for (MiniAppQueryResult result : results) {
            String description = result.getDescription() == null ? "" : result.getDescription();
            String haystack = (result.getAppName() + " " + description).toLowerCase();
            if (haystack.contains(normalized)) {
                matches.add(result);
            }
        }
        return matches;
    }
}
