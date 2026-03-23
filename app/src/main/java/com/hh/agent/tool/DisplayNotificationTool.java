package com.hh.agent.tool;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;
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
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder("显示通知", "在设备上展示一条系统通知")
                .intentExamples("弹一个通知提醒我开会", "显示通知标题为待办，内容为下午三点开会")
                .stringParam("title", "通知标题", true, "会议提醒")
                .stringParam("content", "通知内容", true, "下午3点开会")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            if (!args.has("title")) {
                return ToolResult.error("missing_required_param")
                        .with("param", "title");
            }
            if (!args.has("content")) {
                return ToolResult.error("missing_required_param")
                        .with("param", "content");
            }

            String title = args.getString("title");
            String content = args.getString("content");

            NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                return ToolResult.error("notification_manager_unavailable");
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

            notificationManager.notify(NOTIFICATION_ID, builder.build());

            return ToolResult.success().with("result", "notification_shown");
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }
}
