package com.hh.agent.core.log;

/**
 * Agent 日志接口。
 * 由宿主或上层模块注入具体实现，未注入时使用默认实现。
 */
public interface AgentLogger {

    void d(String tag, String message);

    void i(String tag, String message);

    void w(String tag, String message);

    void e(String tag, String message);

    void e(String tag, String message, Throwable throwable);
}
