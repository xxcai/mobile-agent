package com.hh.agent.android.log;

/**
 * Agent 日志统一入口。
 * agent-android 继续负责结构化日志格式和默认 tag；
 * 底层 logger 状态与默认实现已复用 agent-core。
 */
public final class AgentLogs {

    public static final String DEFAULT_TAG = "AgentAndroid";

    private AgentLogs() {
    }

    public static AgentLogger getLogger() {
        com.hh.agent.core.log.AgentLogger logger = com.hh.agent.core.log.AgentLogs.getLogger();
        if (logger instanceof AgentLogger) {
            return (AgentLogger) logger;
        }
        return new CoreLoggerAdapter(logger);
    }

    public static void setLogger(AgentLogger customLogger) {
        com.hh.agent.core.log.AgentLogs.setLogger(customLogger);
    }

    public static void resetLogger() {
        com.hh.agent.core.log.AgentLogs.resetLogger();
    }

    public static void debug(String scope, String event) {
        debug(scope, event, null);
    }

    public static void debug(String scope, String event, String detail) {
        com.hh.agent.core.log.AgentLogs.getLogger().d(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void info(String scope, String event) {
        info(scope, event, null);
    }

    public static void info(String scope, String event, String detail) {
        com.hh.agent.core.log.AgentLogs.getLogger().i(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void warn(String scope, String event) {
        warn(scope, event, null);
    }

    public static void warn(String scope, String event, String detail) {
        com.hh.agent.core.log.AgentLogs.getLogger().w(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void error(String scope, String event, String detail) {
        com.hh.agent.core.log.AgentLogs.getLogger().e(DEFAULT_TAG, buildMessage(scope, event, detail));
    }

    public static void error(String scope, String event, String detail, Throwable throwable) {
        com.hh.agent.core.log.AgentLogs.getLogger()
                .e(DEFAULT_TAG, buildMessage(scope, event, detail), throwable);
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

    private static final class CoreLoggerAdapter implements AgentLogger {

        private final com.hh.agent.core.log.AgentLogger delegate;

        private CoreLoggerAdapter(com.hh.agent.core.log.AgentLogger delegate) {
            this.delegate = delegate;
        }

        @Override
        public void d(String tag, String message) {
            delegate.d(tag, message);
        }

        @Override
        public void i(String tag, String message) {
            delegate.i(tag, message);
        }

        @Override
        public void w(String tag, String message) {
            delegate.w(tag, message);
        }

        @Override
        public void e(String tag, String message) {
            delegate.e(tag, message);
        }

        @Override
        public void e(String tag, String message, Throwable throwable) {
            delegate.e(tag, message, throwable);
        }
    }
}
