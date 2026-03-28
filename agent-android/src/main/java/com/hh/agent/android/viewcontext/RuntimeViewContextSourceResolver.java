package com.hh.agent.android.viewcontext;

import android.app.Activity;

import androidx.annotation.Nullable;

public class RuntimeViewContextSourceResolver {

    private final ViewContextSourceSelector policySelector;
    private final WebViewAreaFallbackSourceResolver fallbackResolver;
    private final StableForegroundActivityProvider stableForegroundActivityProvider;

    public RuntimeViewContextSourceResolver(ViewContextSourceSelector policySelector,
                                            WebViewAreaFallbackSourceResolver fallbackResolver,
                                            StableForegroundActivityProvider stableForegroundActivityProvider) {
        this.policySelector = policySelector;
        this.fallbackResolver = fallbackResolver;
        this.stableForegroundActivityProvider = stableForegroundActivityProvider;
    }

    public ViewContextSourceSelection resolve() {
        Activity activity = stableForegroundActivityProvider.getStableForegroundActivity();
        ViewContextSourceSelection policySelection = policySelector.selectByPolicy(activity);
        if (policySelection.hasPolicyMatch()) {
            return policySelection;
        }
        return fallbackResolver.resolve(activity);
    }

    public static RuntimeViewContextSourceResolver createDefault() {
        return new RuntimeViewContextSourceResolver(
                new ViewContextSourceSelector(ViewContextSourcePolicyRegistry.getActivePolicy()),
                new WebViewAreaFallbackSourceResolver(),
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return InProcessViewHierarchyDumper.getCurrentStableForegroundActivity();
                    }
                }
        );
    }
}
