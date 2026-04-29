package com.hh.agent;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 用于验证应用内普通页面会继续显示悬浮球。
 */
public class FloatingBallVisibleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Floating Ball Visible");
        setContentView(createContentView(
                "测试路径：进入本页后悬浮球应继续显示。\n" +
                        "这是应用内普通 Activity，不在隐藏列表里。"
        ));
    }

    private LinearLayout createContentView(String text) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        int padding = dp(24);
        container.setPadding(padding, padding, padding, padding);

        TextView description = new TextView(this);
        description.setText(text);
        description.setTextSize(16f);
        container.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView nativeLabel = new TextView(this);
        nativeLabel.setText("Clipboard Paste Native Input");
        nativeLabel.setTextSize(14f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(24);
        container.addView(nativeLabel, labelParams);

        EditText nativeInput = new EditText(this);
        nativeInput.setHint("Tap here, then run clipboard paste dryrun");
        nativeInput.setSingleLine(false);
        nativeInput.setMinLines(3);
        nativeInput.setGravity(Gravity.TOP | Gravity.START);
        nativeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        container.addView(nativeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView h5Label = new TextView(this);
        h5Label.setText("Clipboard Paste H5 Textarea");
        h5Label.setTextSize(14f);
        LinearLayout.LayoutParams h5LabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        h5LabelParams.topMargin = dp(24);
        container.addView(h5Label, h5LabelParams);

        WebView h5Input = new WebView(this);
        h5Input.loadDataWithBaseURL(
                "https://mobile-agent.local/",
                "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                        + "<style>body{margin:0;font-family:sans-serif;}textarea{box-sizing:border-box;width:100%;height:140px;font-size:18px;padding:12px;}</style>"
                        + "</head><body><textarea autofocus placeholder='Tap here, then run clipboard paste dryrun'></textarea></body></html>",
                "text/html",
                "UTF-8",
                null);
        container.addView(h5Input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(160)));

        return container;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
