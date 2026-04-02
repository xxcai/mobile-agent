package com.hh.agent.app;

import com.hh.agent.android.route.WeCodeQueryResult;
import com.hh.agent.android.route.WeCodeQuerySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DefaultMockWeCodeQuerySource implements WeCodeQuerySource {
    private final List<WeCodeQueryResult> results;

    private DefaultMockWeCodeQuerySource(List<WeCodeQueryResult> results) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
    }

    static WeCodeQuerySource create() {
        List<WeCodeQueryResult> seed = new ArrayList<>();
        seed.add(new WeCodeQueryResult(
                "h5://1001001",
                "费控报销",
                "费用报销入口"));
        seed.add(new WeCodeQueryResult(
                "h5://1001002",
                "差旅申请",
                "差旅申请入口"));
        return new DefaultMockWeCodeQuerySource(seed);
    }

    @Override
    public List<WeCodeQueryResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = query.trim().toLowerCase();
        List<WeCodeQueryResult> matches = new ArrayList<>();
        for (WeCodeQueryResult result : results) {
            String description = result.getDescription() == null ? "" : result.getDescription();
            String haystack = (result.getAppName() + " " + description).toLowerCase();
            if (haystack.contains(normalized)) {
                matches.add(result);
            }
        }
        return matches;
    }
}
