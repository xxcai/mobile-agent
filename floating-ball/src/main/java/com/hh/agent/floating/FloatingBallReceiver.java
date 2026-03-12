package com.hh.agent.floating;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 悬浮球广播接收器
 * 接收APP_FOREGROUND/APP_BACKGROUND广播，控制悬浮球显示/隐藏
 */
public class FloatingBallReceiver extends BroadcastReceiver {

    public static final String ACTION_APP_FOREGROUND = "com.hh.agent.action.APP_FOREGROUND";
    public static final String ACTION_APP_BACKGROUND = "com.hh.agent.action.APP_BACKGROUND";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        FloatingBallManager manager = FloatingBallManager.getInstance(context);

        if (ACTION_APP_FOREGROUND.equals(action)) {
            // 应用进入前台，显示悬浮球
            manager.show();
        } else if (ACTION_APP_BACKGROUND.equals(action)) {
            // 应用进入后台，隐藏悬浮球
            manager.hide();
        }
    }
}
