package com.hh.agent.android.viewcontext;

public interface ViewObservationProjectionStrategy {
    boolean supports(String implementationKey);

    ViewObservationProjection project(String tree, String nodes, String nodesFormat);
}
