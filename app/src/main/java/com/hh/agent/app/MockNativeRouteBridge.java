package com.hh.agent.app;

import com.hh.agent.android.route.NativeRouteBridge;
import com.hh.agent.android.route.NativeRouteRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MockNativeRouteBridge implements NativeRouteBridge {
    private final List<NativeRouteRecord> records;

    MockNativeRouteBridge() {
        List<NativeRouteRecord> seed = new ArrayList<>();
        seed.add(new NativeRouteRecord(
                "ui://myapp.im/createGroup",
                "myapp.im",
                "创建群聊",
                "创建新的群聊"));
        seed.add(new NativeRouteRecord(
                "ui://myapp.search/selectActivity",
                "myapp.search",
                "选择搜索范围",
                "搜索页目标选择页"));
        seed.add(new NativeRouteRecord(
                "ui://myapp.expense/records",
                "myapp.expense",
                "报销记录",
                "查看报销记录"));
        records = Collections.unmodifiableList(seed);
    }

    @Override
    public List<NativeRouteRecord> findByUri(String uri) {
        if (uri == null) {
            return Collections.emptyList();
        }
        List<NativeRouteRecord> matches = new ArrayList<>();
        for (NativeRouteRecord record : records) {
            if (uri.equals(record.getUri())) {
                matches.add(record);
            }
        }
        return matches;
    }

    @Override
    public List<NativeRouteRecord> searchByModule(String module, List<String> keywords) {
        if (module == null) {
            return Collections.emptyList();
        }
        List<NativeRouteRecord> matches = new ArrayList<>();
        for (NativeRouteRecord record : records) {
            if (!module.equals(record.getModule())) {
                continue;
            }
            if (keywords == null || keywords.isEmpty() || matchesKeyword(record, keywords)) {
                matches.add(record);
            }
        }
        return matches;
    }

    @Override
    public List<NativeRouteRecord> searchByKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }
        List<NativeRouteRecord> matches = new ArrayList<>();
        for (NativeRouteRecord record : records) {
            if (matchesKeyword(record, keywords)) {
                matches.add(record);
            }
        }
        return matches;
    }

    private boolean matchesKeyword(NativeRouteRecord record, List<String> keywords) {
        String haystack = (record.getTitle() + " " + record.getDescription() + " " + record.getModule())
                .toLowerCase();
        for (String keyword : keywords) {
            if (keyword != null && haystack.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
