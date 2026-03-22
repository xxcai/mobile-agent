package com.hh.agent.android.log;

/**
 * Agent 日志统一入口。
 * 内部持有当前生效的 logger，默认使用 Android Log 实现。
 */
public final class AgentLogs {

    public static final String DEFAULT_TAG = "AgentAndroid";

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

    public static void debug(String scope, String event) {
        debug(scope, event, null);
    }

    public static void debug(String scope, String event, String detail) {
        logger.d(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void info(String scope, String event) {
        info(scope, event, null);
    }

    public static void info(String scope, String event, String detail) {
        logger.i(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void warn(String scope, String event) {
        warn(scope, event, null);
    }

    public static void warn(String scope, String event, String detail) {
        logger.w(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void error(String scope, String event, String detail) {
        logger.e(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void error(String scope, String event, String detail, Throwable throwable) {
        logger.e(DEFAULT_TAG, buildMessage(scope, event, detail), throwable);
    }

    private static String buildMessage(String scope, String event, String detail) {
        StringBuilder builder = new StringBuilder();
        builder.append("[")
                .append(scope)
                .append("][")
                .append(event)
                .append("]");

        if (detail != null && !detail.isEmpty()) {
            builder.append(" ").append(detail);
        }
        return builder.toString();
    }
}
