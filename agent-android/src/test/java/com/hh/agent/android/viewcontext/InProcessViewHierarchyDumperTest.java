package com.hh.agent.android.viewcontext;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InProcessViewHierarchyDumperTest {

    @Test
    public void buildNodeOpenTag_keepsCoreAttributes() {
        String xml = InProcessViewHierarchyDumper.buildNodeOpenTag(
                7,
                "android.widget.TextView",
                "com.hh.agent:id/title",
                "Hello",
                "[1,2][3,4]"
        );

        assertTrue(xml.contains("index=\"7\""));
        assertTrue(xml.contains("class=\"android.widget.TextView\""));
        assertTrue(xml.contains("resource-id=\"com.hh.agent:id/title\""));
        assertTrue(xml.contains("text=\"Hello\""));
        assertTrue(xml.contains("bounds=\"[1,2][3,4]\""));
        assertTrue(xml.endsWith(">"));
    }

    @Test
    public void buildNodeOpenTag_omitsEmptyOptionalAttributes() {
        String xml = InProcessViewHierarchyDumper.buildNodeOpenTag(
                1,
                "android.view.View",
                "",
                null,
                "[0,0][10,10]"
        );

        assertFalse(xml.contains("resource-id="));
        assertFalse(xml.contains("text="));
        assertTrue(xml.contains("class=\"android.view.View\""));
        assertTrue(xml.contains("bounds=\"[0,0][10,10]\""));
    }

    @Test
    public void buildNodeOpenTag_doesNotEmitRemovedBooleanAttributes() {
        String xml = InProcessViewHierarchyDumper.buildNodeOpenTag(
                3,
                "android.widget.Button",
                "com.hh.agent:id/action",
                "Send",
                "[10,20][30,40]"
        );

        assertFalse(xml.contains("clickable="));
        assertFalse(xml.contains("enabled="));
        assertFalse(xml.contains("focusable="));
        assertFalse(xml.contains("visible="));
    }
}
