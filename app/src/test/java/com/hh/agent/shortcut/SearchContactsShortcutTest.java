package com.hh.agent.shortcut;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchContactsShortcutTest {

    @Test
    public void execute_returnsCandidateSelectionWithStablePayloadsForAmbiguousContacts() throws Exception {
        SearchContactsShortcut shortcut = new SearchContactsShortcut();

        JSONObject root = new JSONObject(shortcut.execute(new JSONObject().put("query", "张三")).toJsonString());

        assertTrue(root.getBoolean("success"));
        assertEquals(2, root.getInt("candidateCount"));
        JSONObject candidateSelection = root.getJSONObject("candidateSelection");
        assertEquals("contact", candidateSelection.getString("domain"));
        JSONArray items = candidateSelection.getJSONArray("items");
        assertEquals(2, items.length());
        assertEquals(1, items.getJSONObject(0).getInt("index"));
        assertEquals("001", items.getJSONObject(0).getString("stableKey"));
        assertEquals("001", items.getJSONObject(0).getJSONObject("payload").getString("contact_id"));
        assertEquals("技术部", items.getJSONObject(0).getJSONObject("payload").getString("department"));
    }
}
