package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
import org.json.JSONObject;

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
                return adapter.adapt(source,
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
            }
        }
        throw new IllegalArgumentException("Unsupported observation source: " + source);
    }

    static class HybridObservationAdapter implements UnifiedObservationAdapter {
        @Override
        public boolean supports(String source) {
            return "hybrid_observation".equals(source);
        }

        @Override
        public UnifiedViewObservation adapt(String source, String activityClassName,
                                           String interactionDomain, String targetHint,
                                           String pageUrl, String pageTitle,
                                           String nativeViewXml, String webDom,
                                           String visualObservationJson,
                                           String hybridObservationJson,
                                           String screenSnapshot) throws Exception {
            JSONObject raw = new JSONObject();
            if (hybridObservationJson != null) {
                raw.put("hybridObservation", new JSONObject(hybridObservationJson));
            }
            return new UnifiedViewObservation(
                    source,
                    interactionDomain,
                    activityClassName,
                    targetHint,
                    pageUrl,
                    pageTitle,
                    null,
                    "[]",
                    "[]",
                    null,
                    raw.toString()
            );
        }
    }

    static class VisualObservationAdapter implements UnifiedObservationAdapter {
        @Override
        public boolean supports(String source) {
            return "screen_snapshot".equals(source) || "visual_observation".equals(source);
        }

        @Override
        public UnifiedViewObservation adapt(String source, String activityClassName,
                                           String interactionDomain, String targetHint,
                                           String pageUrl, String pageTitle,
                                           String nativeViewXml, String webDom,
                                           String visualObservationJson,
                                           String hybridObservationJson,
                                           String screenSnapshot) throws Exception {
            JSONObject raw = new JSONObject();
            if (visualObservationJson != null) {
                raw.put("visualObservation", new JSONObject(visualObservationJson));
            }
            if (screenSnapshot != null) {
                raw.put("screenSnapshot", screenSnapshot);
            }
            return new UnifiedViewObservation(
                    source,
                    interactionDomain,
                    activityClassName,
                    targetHint,
                    pageUrl,
                    pageTitle,
                    null,
                    "[]",
                    "[]",
                    null,
                    raw.toString()
            );
        }
    }
}
