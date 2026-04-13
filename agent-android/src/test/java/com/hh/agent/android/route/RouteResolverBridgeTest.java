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
        JSONObject candidateSelection = json.getJSONObject("candidateSelection");
        assertEquals("route", candidateSelection.getString("domain"));
        assertEquals(2, candidateSelection.getJSONArray("items").length());
        assertEquals("native:ui://myapp.expense/records",
                candidateSelection.getJSONArray("items").getJSONObject(0).getString("stableKey"));
        assertEquals("ui://myapp.expense/records",
                candidateSelection.getJSONArray("items").getJSONObject(0).getJSONObject("payload").getString("uri"));
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

    @Test
    public void returnsCandidatesWhenKeywordMatchesMultipleNativeRoutes() throws Exception {
        NativeRouteRegistry registry = new NativeRouteRegistry(java.util.Arrays.asList(
                new NativeRouteRegistryEntry(
                        "ui://myapp.login/resetPassword",
                        "myapp.login",
                        "登录页找回密码页面",
                        java.util.Collections.singletonList("密码")),
                new NativeRouteRegistryEntry(
                        "ui://myapp.settings/changePassword",
                        "myapp.settings",
                        "账号安全修改密码页面",
                        java.util.Collections.singletonList("密码"))));
        RouteResolver resolver = new RouteResolver(
                new AllowAllUriAccessPolicy(),
                new NoOpRouteScorer(),
                new RegistryBackedNativeRouteBridge(registry),
                null);

        RouteResolution result = resolver.resolve(RouteHint.fromJson(new JSONObject()
                .put("keywords", new JSONArray().put("密码"))));

        JSONObject json = result.toJson();
        assertEquals("candidates", json.getString("status"));
        assertEquals(2, json.getJSONArray("candidates").length());
    }
}
