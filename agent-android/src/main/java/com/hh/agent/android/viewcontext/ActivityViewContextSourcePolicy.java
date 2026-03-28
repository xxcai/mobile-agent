package com.hh.agent.android.viewcontext;

import java.util.Collections;
import java.util.Map;

/**
 * App-provided Activity -> source policy used by runtime source selection.
 */
public interface ActivityViewContextSourcePolicy {

    ActivityViewContextSourcePolicy EMPTY = new ActivityViewContextSourcePolicy() {
        @Override
        public Map<String, String> getActivitySourceMap() {
            return Collections.emptyMap();
        }
    };

    Map<String, String> getActivitySourceMap();
}
