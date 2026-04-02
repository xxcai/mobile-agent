package com.hh.agent.shortcut;

import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.HostRouteInvoker;
import com.hh.agent.android.route.NativeRouteBridge;
import com.hh.agent.android.route.NoOpHostRouteInvoker;
import com.hh.agent.android.route.RouteOpener;
import com.hh.agent.android.route.RouteResolver;
import com.hh.agent.android.route.WeCodeRouteBridge;
import com.hh.agent.android.route.manifest.ManifestBackedRouteUriComposer;
import com.hh.agent.app.manifest.InMemoryRouteManifestAssetSource;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

public class OpenResolvedRouteShortcutTest {

    @Test
    public void execute_composesUriBeforeOpening() throws Exception {
        CapturingHostRouteInvoker hostRouteInvoker = new CapturingHostRouteInvoker();
        OpenResolvedRouteShortcut shortcut = new OpenResolvedRouteShortcut(
                runtime(hostRouteInvoker),
                ManifestBackedRouteUriComposer.fromAssetSource(
                        new InMemoryRouteManifestAssetSource("app.json")
                                .addFile("mobile_agent/manifests/app.json", "{\n"
                                        + "  \"module\": \"myapp.app\",\n"
                                        + "  \"routes\": [{\"path\": \"ui://myapp.im/createGroup\", \"params\": [{\"name\": \"source\", \"encode\": \"url\"}]}]\n"
                                        + "}")));

        ToolResultJson result = execute(shortcut, new JSONObject()
                .put("targetType", "native")
                .put("uri", "ui://myapp.im/createGroup")
                .put("title", "createGroup")
                .put("routeArgs", new JSONObject()
                        .put("source", new JSONObject()
                                .put("value", "agent card")
                                .put("encoded", false))));

        assertTrue(result.success);
        assertEquals("ui://myapp.im/createGroup?source=agent+card", hostRouteInvoker.openedUri);
    }

    @Test
    public void execute_returnsErrorWhenEncodedStateMissing() throws Exception {
        OpenResolvedRouteShortcut shortcut = new OpenResolvedRouteShortcut(
                runtime(new NoOpHostRouteInvoker()),
                ManifestBackedRouteUriComposer.fromAssetSource(
                        new InMemoryRouteManifestAssetSource("app.json")
                                .addFile("mobile_agent/manifests/app.json", "{\n"
                                        + "  \"module\": \"myapp.app\",\n"
                                        + "  \"routes\": [{\"path\": \"ui://myapp.expense/records\", \"params\": [{\"name\": \"payload\", \"encode\": \"base64\"}]}]\n"
                                        + "}")));

        ToolResultJson result = execute(shortcut, new JSONObject()
                .put("targetType", "native")
                .put("uri", "ui://myapp.expense/records")
                .put("title", "records")
                .put("routeArgs", new JSONObject()
                        .put("payload", new JSONObject()
                                .put("value", "{\"tab\":\"message\"}"))));

        assertFalse(result.success);
        assertEquals("invalid_target", result.error);
    }

    private AndroidRouteRuntime runtime(HostRouteInvoker hostRouteInvoker) {
        RouteResolver routeResolver = new RouteResolver(
                null,
                null,
                emptyNativeBridge(),
                emptyWeCodeBridge());
        RouteOpener routeOpener = new RouteOpener(
                () -> com.hh.agent.android.route.HostForegroundPreparationResult.success(true, true),
                hostRouteInvoker);
        return new AndroidRouteRuntime(routeResolver, routeOpener, hostRouteInvoker);
    }

    private NativeRouteBridge emptyNativeBridge() {
        return new NativeRouteBridge() {
            @Override
            public java.util.List<com.hh.agent.android.route.NativeRouteRecord> findByUri(String uri) {
                return Collections.emptyList();
            }

            @Override
            public java.util.List<com.hh.agent.android.route.NativeRouteRecord> searchByModule(String module, java.util.List<String> keywords) {
                return Collections.emptyList();
            }

            @Override
            public java.util.List<com.hh.agent.android.route.NativeRouteRecord> searchByKeywords(java.util.List<String> keywords) {
                return Collections.emptyList();
            }
        };
    }

    private WeCodeRouteBridge emptyWeCodeBridge() {
        return query -> Collections.emptyList();
    }

    private ToolResultJson execute(OpenResolvedRouteShortcut shortcut, JSONObject args) {
        try {
            JSONObject root = new JSONObject(shortcut.execute(args).toJsonString());
            return new ToolResultJson(
                    root.getBoolean("success"),
                    root.optString("error", null));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse tool result", exception);
        }
    }

    private static final class ToolResultJson {
        private final boolean success;
        private final String error;

        private ToolResultJson(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    private static final class CapturingHostRouteInvoker implements HostRouteInvoker {
        private String openedUri;

        @Override
        public void open(String uri) {
            this.openedUri = uri;
        }
    }
}
