package com.hh.agent.library.tools;

import android.widget.Toast;
import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

/**
 * ShowToast tool implementation.
 * Displays a toast message on the Android device.
 */
public class ShowToastTool implements ToolExecutor {

    private final android.app.Activity activity;

    public ShowToastTool(android.app.Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getName() {
        return "show_toast";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            if (!args.has("message")) {
                return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"message\"}";
            }

            String message = args.getString("message");
            int duration = args.optInt("duration", Toast.LENGTH_SHORT);

            final String finalMessage = message;
            final int finalDuration = duration;

            activity.runOnUiThread(() -> {
                Toast.makeText(activity, finalMessage, finalDuration).show();
            });

            return "{\"success\": true, \"result\": \"toast_shown\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}
