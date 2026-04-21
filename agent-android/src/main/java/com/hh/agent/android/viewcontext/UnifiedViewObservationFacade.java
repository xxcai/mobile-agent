package com.hh.agent.android.viewcontext;

import java.util.Arrays;
import java.util.List;

public final class UnifiedViewObservationFacade {
    private static final List<UnifiedObservationAdapter> ADAPTERS = Arrays.asList(
            new HybridObservationAdapter(),
            new NativeXmlObservationAdapter(),
            new WebDomObservationAdapter(),
            new VisualObservationAdapter()
    );

    private UnifiedViewObservationFacade() {
    }

    public static UnifiedViewObservation build(String source,
                                               String activityClassName,
                                               String interactionDomain,
                                               String targetHint,
                                               String pageUrl,
                                               String pageTitle,
                                               String nativeViewXml,
                                               String webDom,
                                               String visualObservationJson,
                                               String hybridObservationJson,
                                               String screenSnapshot) throws Exception {
        for (UnifiedObservationAdapter adapter : ADAPTERS) {
            if (adapter.supports(source)) {
                UnifiedViewObservation observation = adapter.adapt(source,
                        activityClassName,
                        interactionDomain,
                        targetHint,
                        pageUrl,
                        pageTitle,
                        nativeViewXml,
                        webDom,
                        visualObservationJson,
                        hybridObservationJson,
                        screenSnapshot);
                if (observation != null) {
                    return observation;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported observation source: " + source);
    }
}
