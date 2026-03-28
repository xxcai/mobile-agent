package com.hh.agent.app;

import com.hh.agent.android.route.NativeRouteRegistry;
import com.hh.agent.android.route.NativeRouteRegistryEntry;

import java.util.ArrayList;
import java.util.List;

final class DefaultNativeRouteRegistry {
    private DefaultNativeRouteRegistry() {
    }

    static NativeRouteRegistry create() {
        List<NativeRouteRegistryEntry> entries = new ArrayList<>();
        entries.add(new NativeRouteRegistryEntry(
                "ui://myapp.im/createGroup",
                "myapp.im",
                "创建群聊页面"));
        entries.add(new NativeRouteRegistryEntry(
                "ui://myapp.search/selectActivity",
                "myapp.search",
                "搜索页范围选择页面"));
        entries.add(new NativeRouteRegistryEntry(
                "ui://myapp.expense/records",
                "myapp.expense",
                "报销记录页面"));
        return new NativeRouteRegistry(entries);
    }
}
