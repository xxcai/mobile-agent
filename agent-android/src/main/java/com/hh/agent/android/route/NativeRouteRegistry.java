package com.hh.agent.android.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NativeRouteRegistry {
    private final List<NativeRouteRegistryEntry> entries;

    public NativeRouteRegistry(List<NativeRouteRegistryEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries cannot be null");
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<NativeRouteRegistryEntry> getEntries() {
        return entries;
    }
}
