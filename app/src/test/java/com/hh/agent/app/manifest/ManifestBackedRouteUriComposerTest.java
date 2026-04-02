package com.hh.agent.app.manifest;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ManifestBackedRouteUriComposerTest {

    @Test
    public void compose_urlEncodesWhenManifestRequiresAndEncodedFalse() throws Exception {
        ManifestBackedRouteUriComposer composer = createComposer("{\n"
                + "  \"module\": \"myapp.app\",\n"
                + "  \"routes\": [{\"path\": \"ui://myapp.im/createGroup\", \"params\": [{\"name\": \"source\", \"encode\": \"url\"}]}]\n"
                + "}");

        String uri = composer.compose("ui://myapp.im/createGroup", new JSONObject()
                .put("source", new JSONObject()
                        .put("value", "agent card")
                        .put("encoded", false)));

        assertEquals("ui://myapp.im/createGroup?source=agent+card", uri);
    }

    @Test
    public void compose_keepsEncodedValueWhenEncodedTrue() throws Exception {
        ManifestBackedRouteUriComposer composer = createComposer("{\n"
                + "  \"module\": \"myapp.app\",\n"
                + "  \"routes\": [{\"path\": \"ui://myapp.im/createGroup\", \"params\": [{\"name\": \"source\", \"encode\": \"url\"}]}]\n"
                + "}");

        String uri = composer.compose("ui://myapp.im/createGroup", new JSONObject()
                .put("source", new JSONObject()
                        .put("value", "agent+card")
                        .put("encoded", true)));

        assertEquals("ui://myapp.im/createGroup?source=agent+card", uri);
    }

    @Test
    public void compose_base64EncodesWhenConfigured() throws Exception {
        ManifestBackedRouteUriComposer composer = createComposer("{\n"
                + "  \"module\": \"myapp.app\",\n"
                + "  \"routes\": [{\"path\": \"ui://myapp.expense/records\", \"params\": [{\"name\": \"payload\", \"encode\": \"base64\"}]}]\n"
                + "}");

        String uri = composer.compose("ui://myapp.expense/records", new JSONObject()
                .put("payload", new JSONObject()
                        .put("value", "{\"tab\":\"message\"}")
                        .put("encoded", false)));

        assertEquals("ui://myapp.expense/records?payload=eyJ0YWIiOiJtZXNzYWdlIn0=", uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void compose_requiresEncodedStateWhenEncodeConfigured() throws Exception {
        ManifestBackedRouteUriComposer composer = createComposer("{\n"
                + "  \"module\": \"myapp.app\",\n"
                + "  \"routes\": [{\"path\": \"ui://myapp.expense/records\", \"params\": [{\"name\": \"payload\", \"encode\": \"base64\"}]}]\n"
                + "}");

        composer.compose("ui://myapp.expense/records", new JSONObject()
                .put("payload", new JSONObject()
                        .put("value", "{\"tab\":\"message\"}")));
    }

    @Test
    public void compose_leavesRawValueWhenNoEncodeConfigured() throws Exception {
        ManifestBackedRouteUriComposer composer = createComposer("{\n"
                + "  \"module\": \"myapp.app\",\n"
                + "  \"routes\": [{\"path\": \"ui://myapp.search/selectActivity\", \"params\": [{\"name\": \"scope\"}]}]\n"
                + "}");

        String uri = composer.compose("ui://myapp.search/selectActivity", new JSONObject()
                .put("scope", new JSONObject().put("value", "A&B")));

        assertEquals("ui://myapp.search/selectActivity?scope=A&B", uri);
    }

    private ManifestBackedRouteUriComposer createComposer(String json) throws Exception {
        return ManifestBackedRouteUriComposer.fromAssetSource(
                new InMemoryRouteManifestAssetSource("app.json")
                        .addFile("mobile_agent/manifests/app.json", json));
    }
}
