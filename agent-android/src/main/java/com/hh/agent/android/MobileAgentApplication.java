package com.hh.agent.android;

import android.app.Application;
import com.hh.agent.library.api.NativeMobileAgentApi;

/**
 * Mobile Agent Application
 * 在应用启动时初始化会话持久化
 */
public class MobileAgentApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化 NativeMobileAgentApi 的上下文
        NativeMobileAgentApi.getInstance().initializeContext(this);

        // 从本地存储恢复所有会话
        int loadedCount = NativeMobileAgentApi.getInstance().loadAllSessions();
        System.out.println("[MobileAgentApplication] Restored " + loadedCount + " sessions from storage");
    }
}
