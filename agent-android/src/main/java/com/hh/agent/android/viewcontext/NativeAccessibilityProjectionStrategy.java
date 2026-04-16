package com.hh.agent.android.viewcontext;

final class NativeAccessibilityProjectionStrategy implements ViewObservationProjectionStrategy {
    static final String IMPLEMENTATION_KEY = "native_accessibility";

    @Override
    public boolean supports(String implementationKey) {
        return IMPLEMENTATION_KEY.equals(implementationKey);
    }

    @Override
    public ViewObservationProjection project(String tree, String nodes, String nodesFormat) {
        return new ViewObservationProjection(tree, nodes, nodesFormat);
    }
}
