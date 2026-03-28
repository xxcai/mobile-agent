package com.hh.agent.android.viewcontext;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;

public class ViewContextSourcePolicyRegistryTest {

    @Test
    public void setActivePolicy_storesProvidedPolicy() {
        ActivityViewContextSourcePolicy policy = new ActivityViewContextSourcePolicy() {
            @Override
            public Map<String, String> getActivitySourceMap() {
                return new HashMap<>();
            }
        };

        ViewContextSourcePolicyRegistry.setActivePolicy(policy);

        assertSame(policy, ViewContextSourcePolicyRegistry.getActivePolicy());
        ViewContextSourcePolicyRegistry.setActivePolicy(ActivityViewContextSourcePolicy.EMPTY);
    }

    @Test
    public void setActivePolicy_fallsBackToEmptyWhenNull() {
        ViewContextSourcePolicyRegistry.setActivePolicy(null);

        assertSame(ActivityViewContextSourcePolicy.EMPTY, ViewContextSourcePolicyRegistry.getActivePolicy());
    }
}
