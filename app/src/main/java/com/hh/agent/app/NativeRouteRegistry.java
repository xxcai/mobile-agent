package com.hh.agent.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NativeRouteRegistry {
    private final List<NativeRouteRegistryEntry> entries;

    NativeRouteRegistry(List<NativeRouteRegistryEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries cannot be null");
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    List<NativeRouteRegistryEntry> getEntries() {
        return entries;
    }
}
