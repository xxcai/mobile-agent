package com.hh.agent.app.manifest;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ManifestBackedRouteModuleResolverTest {

    @Test
    public void inferModule_returnsSingleModuleWhenKeywordsOnlyMatchOneManifest() throws Exception {
        ManifestBackedRouteModuleResolver resolver = ManifestBackedRouteModuleResolver.fromAssetSource(
                new InMemoryRouteManifestAssetSource("app.json", "settings.json")
                        .addFile("mobile_agent/manifests/app.json", "{\n"
                                + "  \"module\": \"myapp.im\",\n"
                                + "  \"routes\": [{\"path\": \"ui://myapp.im/createGroup\", \"keywords\": [\"IM\", \"群聊\", \"建群\"]}]\n"
                                + "}")
                        .addFile("mobile_agent/manifests/settings.json", "{\n"
                                + "  \"module\": \"myapp.settings\",\n"
                                + "  \"routes\": [{\"path\": \"ui://myapp.settings/changePassword\", \"keywords\": [\"设置\", \"密码\"]}]\n"
                                + "}"));

        assertEquals("myapp.im", resolver.inferModule(Arrays.asList("创建群聊", "建群")));
    }

    @Test
    public void inferModule_returnsNullWhenKeywordsMatchMultipleModules() throws Exception {
        ManifestBackedRouteModuleResolver resolver = ManifestBackedRouteModuleResolver.fromAssetSource(
                new InMemoryRouteManifestAssetSource("a.json", "b.json")
                        .addFile("mobile_agent/manifests/a.json", "{\n"
                                + "  \"module\": \"myapp.login\",\n"
                                + "  \"routes\": [{\"path\": \"ui://myapp.login/resetPassword\", \"keywords\": [\"密码\", \"找回密码\"]}]\n"
                                + "}")
                        .addFile("mobile_agent/manifests/b.json", "{\n"
                                + "  \"module\": \"myapp.settings\",\n"
                                + "  \"routes\": [{\"path\": \"ui://myapp.settings/changePassword\", \"keywords\": [\"密码\", \"修改密码\"]}]\n"
                                + "}"));

        assertNull(resolver.inferModule(Arrays.asList("密码")));
    }

    @Test
    public void inferModule_returnsNullWhenNoKeywordMatches() throws Exception {
        ManifestBackedRouteModuleResolver resolver = ManifestBackedRouteModuleResolver.fromAssetSource(
                new InMemoryRouteManifestAssetSource("app.json")
                        .addFile("mobile_agent/manifests/app.json", "{\n"
                                + "  \"module\": \"myapp.expense\",\n"
                                + "  \"routes\": [{\"path\": \"ui://myapp.expense/records\", \"keywords\": [\"报销\", \"费用记录\"]}]\n"
                                + "}"));

        assertNull(resolver.inferModule(Arrays.asList("通讯录")));
    }
}
