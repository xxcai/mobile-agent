package com.hh.agent.android.route;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RouteRuntimeTest {

    @Test
    public void routeHintNormalizesKeywordsAndSearchableFields() throws Exception {
        JSONObject raw = new JSONObject()
                .put("targetTypeHint", "wecode")
                .put("weCodeName", "  报销  ")
                .put("keywords", new JSONArray()
                        .put(" 报销 ")
                        .put("")
                        .put("费用报销")
                        .put("报销")
                        .put("A")
                        .put("B")
                        .put("C")
                        .put("D"));

        RouteHint routeHint = RouteHint.fromJson(raw);

        assertEquals("wecode", routeHint.getTargetTypeHint());
        assertEquals("报销", routeHint.getWeCodeName());
        assertEquals(5, routeHint.getKeywords().size());
        assertEquals("报销", routeHint.getKeywords().get(0));
        assertEquals("费用报销", routeHint.getKeywords().get(1));
    }

    @Test
    public void resolveReturnsInsufficientHintWhenNoSearchableFieldsExist() throws Exception {
        AndroidRouteRuntime runtime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        null,
                        null),
                null);

        RouteResolution result = runtime.resolve(RouteHint.empty());

        JSONObject json = result.toJson();
        assertEquals("insufficient_hint", json.getString("status"));
        assertEquals("missing_searchable_fields", json.getJSONObject("diagnostics").getString("reason"));
    }

    @Test
    public void resolveDirectUriReturnsResolvedTargetWithFallbackTitle() throws Exception {
        AndroidRouteRuntime runtime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        null,
                        null),
                null);

        RouteHint routeHint = RouteHint.fromJson(new JSONObject()
                .put("targetTypeHint", "native")
                .put("uri", "ui://myapp.search/selectActivity"));

        RouteResolution result = runtime.resolve(routeHint);

        JSONObject json = result.toJson();
        assertEquals("resolved", json.getString("status"));
        JSONObject target = json.getJSONObject("recommendedTarget");
        assertEquals("native", target.getString("targetType"));
        assertEquals("ui://myapp.search/selectActivity", target.getString("uri"));
        assertEquals("selectActivity", target.getString("title"));
        assertEquals("direct_uri", target.getString("source"));
        assertEquals("uri_direct", target.getString("matchMode"));
    }

    @Test
    public void resolveInvalidUriReturnsInsufficientHint() throws Exception {
        AndroidRouteRuntime runtime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        null,
                        null),
                null);

        RouteHint routeHint = RouteHint.fromJson(new JSONObject()
                .put("uri", "not-a-uri"));

        RouteResolution result = runtime.resolve(routeHint);

        JSONObject json = result.toJson();
        assertEquals("insufficient_hint", json.getString("status"));
        assertEquals("invalid_uri_format", json.getJSONObject("diagnostics").getString("reason"));
    }

    @Test
    public void resolveNonUriSearchableHintReturnsNotFoundUntilBridgesAreConnected() throws Exception {
        AndroidRouteRuntime runtime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        null,
                        null),
                null);

        RouteHint routeHint = RouteHint.fromJson(new JSONObject()
                .put("targetTypeHint", "wecode")
                .put("weCodeName", "报销")
                .put("keywords", new JSONArray().put("费用报销")));

        RouteResolution result = runtime.resolve(routeHint);

        JSONObject json = result.toJson();
        assertEquals("not_found", json.getString("status"));
        assertEquals("bridge_not_connected_or_no_match", json.getJSONObject("diagnostics").getString("reason"));
        assertFalse(json.has("recommendedTarget"));
    }

    @Test
    public void routeHintRejectsLegacyMiniAppFields() throws Exception {
        JSONObject raw = new JSONObject()
                .put("targetTypeHint", "miniapp")
                .put("miniAppName", "  报销  ");

        RouteHint routeHint = RouteHint.fromJson(raw);

        assertEquals("unknown", routeHint.getTargetTypeHint());
        assertEquals(null, routeHint.getWeCodeName());
    }
}
