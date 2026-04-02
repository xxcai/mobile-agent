package com.hh.agent.android.route;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RegistryBackedNativeRouteBridgeTest {

    @Test
    public void searchByKeywords_matchesRouteKeywords() {
        NativeRouteRegistry registry = new NativeRouteRegistry(Collections.singletonList(
                new NativeRouteRegistryEntry(
                        "ui://myapp.login/resetPassword",
                        "myapp.login",
                        "修改密码页面",
                        Arrays.asList("忘记密码", "找回密码"))));
        RegistryBackedNativeRouteBridge bridge = new RegistryBackedNativeRouteBridge(registry);

        List<NativeRouteRecord> matches = bridge.searchByKeywords(Collections.singletonList("找回密码"));

        assertEquals(1, matches.size());
        assertEquals("ui://myapp.login/resetPassword", matches.get(0).getUri());
    }

    @Test
    public void searchByKeywords_returnsMultipleMatchesWhenSameKeywordHitsMultipleRoutes() {
        NativeRouteRegistry registry = new NativeRouteRegistry(Arrays.asList(
                new NativeRouteRegistryEntry(
                        "ui://myapp.login/resetPassword",
                        "myapp.login",
                        "登录页找回密码页面",
                        Collections.singletonList("密码")),
                new NativeRouteRegistryEntry(
                        "ui://myapp.settings/changePassword",
                        "myapp.settings",
                        "账号安全修改密码页面",
                        Collections.singletonList("密码"))));
        RegistryBackedNativeRouteBridge bridge = new RegistryBackedNativeRouteBridge(registry);

        List<NativeRouteRecord> matches = bridge.searchByKeywords(Collections.singletonList("密码"));

        assertEquals(2, matches.size());
    }
}
