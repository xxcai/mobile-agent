package com.hh.agent.android.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RegistryBackedNativeRouteBridge implements NativeRouteBridge {
    private final NativeRouteRegistry nativeRouteRegistry;

    public RegistryBackedNativeRouteBridge(NativeRouteRegistry nativeRouteRegistry) {
        if (nativeRouteRegistry == null) {
            throw new IllegalArgumentException("nativeRouteRegistry cannot be null");
        }
        this.nativeRouteRegistry = nativeRouteRegistry;
    }

    @Override
    public List<NativeRouteRecord> findByUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<NativeRouteRecord> matches = new ArrayList<>();
        for (NativeRouteRegistryEntry entry : nativeRouteRegistry.getEntries()) {
            if (uri.equals(entry.getUri())) {
                matches.add(toRecord(entry));
            }
        }
        return matches;
    }

    @Override
    public List<NativeRouteRecord> searchByModule(String module, List<String> keywords) {
        if (module == null || module.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<NativeRouteRecord> matches = new ArrayList<>();
        for (NativeRouteRegistryEntry entry : nativeRouteRegistry.getEntries()) {
            if (!module.equals(entry.getModule())) {
                continue;
            }
            if (keywords == null || keywords.isEmpty() || matchesKeyword(entry, keywords)) {
                matches.add(toRecord(entry));
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
        for (NativeRouteRegistryEntry entry : nativeRouteRegistry.getEntries()) {
            if (matchesKeyword(entry, keywords)) {
                matches.add(toRecord(entry));
            }
        }
        return matches;
    }

    private NativeRouteRecord toRecord(NativeRouteRegistryEntry entry) {
        String uri = entry.getUri();
        return new NativeRouteRecord(
                uri,
                entry.getModule(),
                deriveTitle(uri),
                entry.getDescription()
        );
    }

    private boolean matchesKeyword(NativeRouteRegistryEntry entry, List<String> keywords) {
        String pageSegment = deriveTitle(entry.getUri()).toLowerCase();
        String description = entry.getDescription() == null ? "" : entry.getDescription();
        String haystack = (entry.getUri() + " " + entry.getModule() + " " + pageSegment + " " + description
                + " " + joinKeywords(entry.getKeywords()))
                .toLowerCase();
        for (String keyword : keywords) {
            if (keyword != null && haystack.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String deriveTitle(String uri) {
        int slashIndex = uri.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < uri.length() - 1) {
            return uri.substring(slashIndex + 1);
        }
        return uri;
    }

    private String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        return String.join(" ", keywords);
    }
}
