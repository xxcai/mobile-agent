package com.hh.agent.library.tools;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

/**
 * DisplayNotification tool implementation.
 * Displays a notification on the Android device.
 */
public class DisplayNotificationTool implements ToolExecutor {

    private static final String CHANNEL_ID = "android_tool_notifications";
    private static final String CHANNEL_NAME = "Android Tools";
    private static final int NOTIFICATION_ID = 1001;

    private final Context context;

    public DisplayNotificationTool(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public String getName() {
        return "display_notification";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            if (!args.has("title")) {
                return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"title\"}";
            }
            if (!args.has("content")) {
                return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"content\"}";
            }

            String title = args.getString("title");
            String content = args.getString("content");

            NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                return "{\"success\": false, \"error\": \"notification_manager_unavailable\"}";
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

            notificationManager.notify(NOTIFICATION_ID, builder.build());

            return "{\"success\": true, \"result\": \"notification_shown\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}
