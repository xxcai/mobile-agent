package com.hh.agent.android.log;

/**
 * Agent 日志统一入口。
 * 内部持有当前生效的 logger，默认使用 Android Log 实现。
 */
public final class AgentLogs {

    private static volatile AgentLogger logger = new DefaultAgentLogger();

    private AgentLogs() {
    }

    public static AgentLogger getLogger() {
        return logger;
    }

    public static void setLogger(AgentLogger customLogger) {
        logger = customLogger != null ? customLogger : new DefaultAgentLogger();
    }

    public static void resetLogger() {
        logger = new DefaultAgentLogger();
    }

    public static void d(String tag, String message) {
        logger.d(tag, message);
    }

    public static void i(String tag, String message) {
        logger.i(tag, message);
    }

    public static void w(String tag, String message) {
        logger.w(tag, message);
    }

    public static void e(String tag, String message) {
        logger.e(tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        logger.e(tag, message, throwable);
    }
}
