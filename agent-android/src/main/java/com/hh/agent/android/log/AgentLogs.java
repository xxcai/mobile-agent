package com.hh.agent.android.log;

import android.util.Log;

/**
 * Agent 鏃ュ織缁熶竴鍏ュ彛銆?
 * agent-android 缁х画璐熻矗缁撴瀯鍖栨棩蹇楁牸寮忓拰榛樿 tag锛?
 * 搴曞眰 logger 鐘舵€佷笌榛樿瀹炵幇宸插鐢?agent-core銆?
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
        String message = buildMessage(scope, event, detail);
        safeCoreDebug(message);
        safeDebug(DEFAULT_TAG, message);
    }

    public static void info(String scope, String event) {
        info(scope, event, null);
    }

    public static void info(String scope, String event, String detail) {
        String message = buildMessage(scope, event, detail);
        safeCoreInfo(message);
        safeInfo(DEFAULT_TAG, message);
    }

    public static void warn(String scope, String event) {
        warn(scope, event, null);
    }

    public static void warn(String scope, String event, String detail) {
        String message = buildMessage(scope, event, detail);
        safeCoreWarn(message);
        safeWarn(DEFAULT_TAG, message);
    }

    public static void error(String scope, String event, String detail) {
        String message = buildMessage(scope, event, detail);
        safeCoreError(message);
        safeError(DEFAULT_TAG, message);
    }

    public static void error(String scope, String event, String detail, Throwable throwable) {
        String message = buildMessage(scope, event, detail);
        safeCoreError(message, throwable);
        safeError(DEFAULT_TAG, message, throwable);
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

    private static void safeCoreDebug(String message) {
        try {
            com.hh.agent.core.log.AgentLogs.getLogger().d(DEFAULT_TAG, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeCoreInfo(String message) {
        try {
            com.hh.agent.core.log.AgentLogs.getLogger().i(DEFAULT_TAG, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeCoreWarn(String message) {
        try {
            com.hh.agent.core.log.AgentLogs.getLogger().w(DEFAULT_TAG, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeCoreError(String message) {
        try {
            com.hh.agent.core.log.AgentLogs.getLogger().e(DEFAULT_TAG, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeCoreError(String message, Throwable throwable) {
        try {
            com.hh.agent.core.log.AgentLogs.getLogger().e(DEFAULT_TAG, message, throwable);
        } catch (Throwable ignored) {
        }
    }

    private static void safeDebug(String tag, String message) {
        try {
            Log.d(tag, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeInfo(String tag, String message) {
        try {
            Log.i(tag, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeWarn(String tag, String message) {
        try {
            Log.w(tag, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeError(String tag, String message) {
        try {
            Log.e(tag, message);
        } catch (Throwable ignored) {
        }
    }

    private static void safeError(String tag, String message, Throwable throwable) {
        try {
            Log.e(tag, message, throwable);
        } catch (Throwable ignored) {
        }
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