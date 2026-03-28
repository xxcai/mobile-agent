package com.hh.agent.android.route;

import java.util.List;

/**
 * Scoring strategy hook for route candidates.
 */
public interface RouteScorer {

    List<RouteTarget> scoreAndSort(List<RouteTarget> candidates, RouteHint routeHint);
}
