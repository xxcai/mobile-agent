package com.hh.agent.android.viewcontext;

import java.util.Arrays;
import java.util.List;

public final class ViewObservationProjectionFacade {
    private static final List<ViewObservationProjectionStrategy> STRATEGIES = Arrays.asList(
            new NativeAccessibilityProjectionStrategy(),
            new NativeViewXmlProjectionStrategy(),
            new WebProjectionStrategy()
    );

    private ViewObservationProjectionFacade() {
    }

    public static ViewObservationProjection project(String implementationKey,
                                                   String tree,
                                                   String nodes,
                                                   String nodesFormat) {
        for (ViewObservationProjectionStrategy strategy : STRATEGIES) {
            if (strategy.supports(implementationKey)) {
                return strategy.project(tree, nodes, nodesFormat);
            }
        }
        return new ViewObservationProjection(tree, nodes, nodesFormat);
    }
}
