package com.hh.agent.android.viewcontext;

public interface UnifiedObservationAdapter {
    boolean supports(String source);

    UnifiedViewObservation adapt(String source,
                                 String activityClassName,
                                 String interactionDomain,
                                 String targetHint,
                                 String pageUrl,
                                 String pageTitle,
                                 String nativeViewXml,
                                 String webDom,
                                 String visualObservationJson,
                                 String hybridObservationJson,
                                 String screenSnapshot) throws Exception;
}
