package com.hh.agent.app;

import android.content.Context;
import android.content.Intent;

import com.hh.agent.BusinessWebActivity;
import com.hh.agent.RouteNativeDemoActivity;
import com.hh.agent.android.route.HostRouteInvoker;

final class DemoHostRouteInvoker implements HostRouteInvoker {
    private final Context appContext;

    DemoHostRouteInvoker(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void open(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("uri cannot be null or empty");
        }
        if (uri.startsWith("ui://miniapp/")) {
            Intent intent = new Intent(appContext, BusinessWebActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(BusinessWebActivity.EXTRA_TITLE, deriveTitle(uri));
            intent.putExtra(BusinessWebActivity.EXTRA_HTML_CONTENT, buildMiniAppHtml(uri));
            appContext.startActivity(intent);
            return;
        }
        if (uri.startsWith("ui://")) {
            Intent intent = new Intent(appContext, RouteNativeDemoActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(RouteNativeDemoActivity.EXTRA_TITLE, deriveTitle(uri));
            intent.putExtra(RouteNativeDemoActivity.EXTRA_URI, uri);
            appContext.startActivity(intent);
            return;
        }
        throw new IllegalArgumentException("unsupported_uri: " + uri);
    }

    private String deriveTitle(String uri) {
        int slashIndex = uri.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < uri.length() - 1) {
            return uri.substring(slashIndex + 1);
        }
        return uri;
    }

    private String buildMiniAppHtml(String uri) {
        return "<p>Mini app route opened.</p><p><strong>URI:</strong> " + escapeHtml(uri) + "</p>";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
