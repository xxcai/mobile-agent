package com.hh.agent.android.viewcontext;

import android.app.Activity;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ViewContextSourceSelectorTest {

    @Test
    public void selectByPolicy_matchesCurrentActivityClass() {
        ViewContextSourceSelector selector = new ViewContextSourceSelector(policyFor(
                CurrentActivity.class.getName(), "web_dom"
        ));

        ViewContextSourceSelection selection = selector.selectByPolicy(CurrentActivity.class);

        assertTrue(selection.hasPolicyMatch());
        assertEquals(ViewContextSourceSelection.Status.POLICY_MATCHED, selection.getStatus());
        assertEquals("web_dom", selection.getSource());
        assertEquals(CurrentActivity.class.getName(), selection.getMatchedActivityClassName());
    }

    @Test
    public void selectByPolicy_matchesParentActivityClass() {
        ViewContextSourceSelector selector = new ViewContextSourceSelector(policyFor(
                ParentActivity.class.getName(), "native_xml"
        ));

        ViewContextSourceSelection selection = selector.selectByPolicy(ChildActivity.class);

        assertTrue(selection.hasPolicyMatch());
        assertEquals("native_xml", selection.getSource());
        assertEquals(ParentActivity.class.getName(), selection.getMatchedActivityClassName());
    }

    @Test
    public void selectByPolicy_returnsNoPolicyMatchWhenClassChainMisses() {
        ViewContextSourceSelector selector = new ViewContextSourceSelector(policyFor(
                UnrelatedActivity.class.getName(), "web_dom"
        ));

        ViewContextSourceSelection selection = selector.selectByPolicy(ChildActivity.class);

        assertFalse(selection.hasPolicyMatch());
        assertEquals(ViewContextSourceSelection.Status.NO_POLICY_MATCH, selection.getStatus());
        assertNull(selection.getSource());
        assertNull(selection.getMatchedActivityClassName());
    }

    @Test
    public void selectByPolicy_ignoresBlankPolicyValue() {
        ViewContextSourceSelector selector = new ViewContextSourceSelector(policyFor(
                CurrentActivity.class.getName(), "   "
        ));

        ViewContextSourceSelection selection = selector.selectByPolicy(CurrentActivity.class);

        assertFalse(selection.hasPolicyMatch());
        assertEquals(ViewContextSourceSelection.Status.NO_POLICY_MATCH, selection.getStatus());
    }

    private ActivityViewContextSourcePolicy policyFor(String activityClassName, String source) {
        final Map<String, String> map = new HashMap<>();
        map.put(activityClassName, source);
        return new ActivityViewContextSourcePolicy() {
            @Override
            public Map<String, String> getActivitySourceMap() {
                return map;
            }
        };
    }

    public static class ParentActivity extends Activity {
    }

    public static class ChildActivity extends ParentActivity {
    }

    public static class CurrentActivity extends Activity {
    }

    public static class UnrelatedActivity extends Activity {
    }
}
