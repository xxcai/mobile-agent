package com.hh.agent.android.route;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RouteResolverBridgeTest {

    @Test
    public void resolvesSingleNativeCandidateFromModuleSearch() throws Exception {
        RouteResolver resolver = new RouteResolver(
                new AllowAllUriAccessPolicy(),
                new NoOpRouteScorer(),
                new NativeRouteBridge() {
                    @Override
                    public List<NativeRouteRecord> findByUri(String uri) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<NativeRouteRecord> searchByModule(String module, List<String> keywords) {
                        return Collections.singletonList(new NativeRouteRecord(
                                "ui://myapp.im/createGroup",
                                "myapp.im",
                                "创建群聊",
                                "创建新的群聊"));
                    }

                    @Override
                    public List<NativeRouteRecord> searchByKeywords(List<String> keywords) {
                        return Collections.emptyList();
                    }
                },
                null);

        RouteResolution result = resolver.resolve(RouteHint.fromJson(new JSONObject()
                .put("targetTypeHint", "native")
                .put("nativeModule", "myapp.im")
                .put("keywords", new JSONArray().put("群聊"))));

        JSONObject json = result.toJson();
        assertEquals("resolved", json.getString("status"));
        assertEquals("ui://myapp.im/createGroup", json.getJSONObject("recommendedTarget").getString("uri"));
    }

    @Test
    public void returnsCandidatesWhenNativeAndWeCodeBothMatch() throws Exception {
        RouteResolver resolver = new RouteResolver(
                new AllowAllUriAccessPolicy(),
                new NoOpRouteScorer(),
                new NativeRouteBridge() {
                    @Override
                    public List<NativeRouteRecord> findByUri(String uri) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<NativeRouteRecord> searchByModule(String module, List<String> keywords) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<NativeRouteRecord> searchByKeywords(List<String> keywords) {
                        return Collections.singletonList(new NativeRouteRecord(
                                "ui://myapp.expense/records",
                                "myapp.expense",
                                "报销记录",
                                "查看报销记录"));
                    }
                },
                query -> Collections.singletonList(new WeCodeRouteRecord(
                        "h5://1001001",
                        "费控报销",
                        "费用报销入口")));

        RouteResolution result = resolver.resolve(RouteHint.fromJson(new JSONObject()
                .put("keywords", new JSONArray().put("报销"))));

        JSONObject json = result.toJson();
        assertEquals("candidates", json.getString("status"));
        assertEquals(2, json.getJSONArray("candidates").length());
    }

    @Test
    public void resolvesWeCodeOnlyWhenTargetTypeHintExplicitlyRequestsWeCode() throws Exception {
        RouteResolver resolver = new RouteResolver(
                new AllowAllUriAccessPolicy(),
                new NoOpRouteScorer(),
                new NativeRouteBridge() {
                    @Override
                    public List<NativeRouteRecord> findByUri(String uri) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<NativeRouteRecord> searchByModule(String module, List<String> keywords) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<NativeRouteRecord> searchByKeywords(List<String> keywords) {
                        return Collections.singletonList(new NativeRouteRecord(
                                "ui://myapp.expense/records",
                                "myapp.expense",
                                "报销记录",
                                "查看报销记录"));
                    }
                },
                query -> Collections.singletonList(new WeCodeRouteRecord(
                        "h5://1001001",
                        "费控报销",
                        "费用报销入口")));

        RouteResolution result = resolver.resolve(RouteHint.fromJson(new JSONObject()
                .put("targetTypeHint", "wecode")
                .put("weCodeName", "报销")
                .put("keywords", new JSONArray().put("报销"))));

        JSONObject json = result.toJson();
        assertEquals("resolved", json.getString("status"));
        assertEquals("wecode", json.getJSONObject("recommendedTarget").getString("targetType"));
        assertEquals("h5://1001001", json.getJSONObject("recommendedTarget").getString("uri"));
    }

    @Test
    public void returnsNotFoundWhenBridgesExistButNoResultMatches() throws Exception {
        RouteResolver resolver = new RouteResolver(
                new AllowAllUriAccessPolicy(),
                new NoOpRouteScorer(),
                new NativeRouteBridge() {
                    @Override
                    public List<NativeRouteRecord> findByUri(String uri) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<NativeRouteRecord> searchByModule(String module, List<String> keywords) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<NativeRouteRecord> searchByKeywords(List<String> keywords) {
                        return Collections.emptyList();
                    }
                },
                query -> Collections.emptyList());

        RouteResolution result = resolver.resolve(RouteHint.fromJson(new JSONObject()
                .put("keywords", new JSONArray().put("不存在的入口"))));

        JSONObject json = result.toJson();
        assertEquals("not_found", json.getString("status"));
        assertEquals("bridge_not_connected_or_no_match", json.getJSONObject("diagnostics").getString("reason"));
    }
}
