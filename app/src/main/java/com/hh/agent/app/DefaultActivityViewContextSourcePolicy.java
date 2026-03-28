package com.hh.agent.app;

import com.hh.agent.BusinessWebActivity;
import com.hh.agent.android.viewcontext.ActivityViewContextSourcePolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultActivityViewContextSourcePolicy implements ActivityViewContextSourcePolicy {

    private final Map<String, String> activitySourceMap;

    private DefaultActivityViewContextSourcePolicy(Map<String, String> activitySourceMap) {
        this.activitySourceMap = activitySourceMap;
    }

    public static ActivityViewContextSourcePolicy create() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(BusinessWebActivity.class.getName(), "web_dom");
        return new DefaultActivityViewContextSourcePolicy(Collections.unmodifiableMap(map));
    }

    @Override
    public Map<String, String> getActivitySourceMap() {
        return activitySourceMap;
    }
}
