package com.hh.agent.android.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Step 06 placeholder scorer. Keeps candidate order unchanged.
 */
public final class NoOpRouteScorer implements RouteScorer {

    @Override
    public List<RouteTarget> scoreAndSort(List<RouteTarget> candidates, RouteHint routeHint) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(candidates));
    }
}
