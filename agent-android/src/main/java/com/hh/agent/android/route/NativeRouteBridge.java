package com.hh.agent.android.route;

import java.util.List;

public interface NativeRouteBridge {

    List<NativeRouteRecord> findByUri(String uri);

    List<NativeRouteRecord> searchByModule(String module, List<String> keywords);

    List<NativeRouteRecord> searchByKeywords(List<String> keywords);
}
