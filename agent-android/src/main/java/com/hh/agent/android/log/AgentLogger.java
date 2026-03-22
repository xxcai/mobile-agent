package com.hh.agent.android.log;

/**
 * agent-android 兼容日志接口。
 * 实际协议已下沉到 agent-core，这里保留旧类型名以兼容现有调用方。
 */
public interface AgentLogger extends com.hh.agent.core.log.AgentLogger {
}
