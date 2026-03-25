package com.hh.agent;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class BusinessWebActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_HTML_CONTENT = "html_content";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_web);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String htmlContent = getIntent().getStringExtra(EXTRA_HTML_CONTENT);
        if (title == null) {
            title = "业务页面";
        }
        if (htmlContent == null) {
            htmlContent = "<p>页面内容为空。</p>";
        }

        ImageView backButton = findViewById(R.id.businessWebBackButton);
        TextView titleView = findViewById(R.id.businessWebTitleView);
        WebView webView = findViewById(R.id.businessWebView);

        backButton.setOnClickListener(v -> finish());
        titleView.setText(title);
        configureWebView(webView, title, htmlContent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView webView, String title, String htmlContent) {
        webView.getSettings().setJavaScriptEnabled(false);
        webView.getSettings().setDomStorageEnabled(false);
        String html = loadHtmlTemplate()
                .replace("__TITLE__", escapeHtml(title))
                .replace("__CONTENT__", htmlContent)
                .replace("__STAMP__", escapeHtml("Mock local HTML"));
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private String loadHtmlTemplate() {
        try (InputStream inputStream = getAssets().open("business_page.html");
             InputStreamReader reader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException exception) {
            return "<html><body><h1>__TITLE__</h1><div>__CONTENT__</div></body></html>";
        }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
