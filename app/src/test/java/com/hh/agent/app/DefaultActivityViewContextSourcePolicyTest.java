package com.hh.agent.app;

import com.hh.agent.BusinessWebActivity;
import com.hh.agent.android.viewcontext.ActivityViewContextSourcePolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultActivityViewContextSourcePolicyTest {

    @Test
    public void create_containsBusinessWebActivityPolicy() {
        ActivityViewContextSourcePolicy policy = DefaultActivityViewContextSourcePolicy.create();

        assertTrue(policy.getActivitySourceMap().containsKey(BusinessWebActivity.class.getName()));
        assertEquals("web_dom",
                policy.getActivitySourceMap().get(BusinessWebActivity.class.getName()));
    }
}
