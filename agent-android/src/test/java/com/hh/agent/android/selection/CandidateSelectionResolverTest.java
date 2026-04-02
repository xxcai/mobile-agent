package com.hh.agent.android.selection;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CandidateSelectionResolverTest {

    @Test
    public void resolve_supportsOrdinalAndAliasSelection() throws Exception {
        CandidateSelectionResolver resolver = new CandidateSelectionResolver();
        JSONObject selection = new JSONObject()
                .put("domain", "contact")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("index", 1)
                                .put("label", "张三（技术部）")
                                .put("aliases", new JSONArray().put("张三").put("技术部那个"))
                                .put("payload", new JSONObject().put("contact_id", "001")))
                        .put(new JSONObject()
                                .put("index", 2)
                                .put("label", "张三（市场部）")
                                .put("aliases", new JSONArray().put("市场部那个"))
                                .put("payload", new JSONObject().put("contact_id", "002"))));

        JSONObject ordinal = resolver.resolve(selection, "第一个", "contact");
        JSONObject alias = resolver.resolve(selection, "技术部那个", "contact");

        assertNotNull(ordinal);
        assertNotNull(alias);
        assertEquals("001", ordinal.getJSONObject("payload").getString("contact_id"));
        assertEquals("001", alias.getJSONObject("payload").getString("contact_id"));
    }
}
