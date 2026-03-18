package com.hh.agent.android.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一线程池管理器
 *
 * 集中管理应用内的线程池，提供：
 * - executeAgentStream: 流式任务专用单线程池
 * - executeAgentIO: IO 密集型任务缓存线程池
 * - shutdown: 统一关闭所有线程池
 */
public class ThreadPoolManager {

    /**
     * 流式任务专用线程池（单线程）
     * 用于 StreamingManager 的流式 API 调用
     */
    private static final ExecutorService STREAM_EXECUTOR =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("agent-stream-1");
            return t;
        });

    /**
     * IO 密集型任务线程池（缓存线程池）
     * 用于文件读取、工具调用等 IO 操作
     */
    private static final ExecutorService IO_EXECUTOR =
        Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("agent-io-" + t.getId());
            return t;
        });

    /**
     * 私有构造函数，防止实例化
     */
    private ThreadPoolManager() {}

    /**
     * 执行流式任务（单线程池）
     *
     * @param task 要执行的任务
     */
    public static void executeAgentStream(Runnable task) {
        STREAM_EXECUTOR.execute(task);
    }

    /**
     * 执行 IO 密集型任务（缓存线程池）
     *
     * @param task 要执行的任务
     */
    public static void executeAgentIO(Runnable task) {
        IO_EXECUTOR.execute(task);
    }

    /**
     * 关闭所有线程池
     * 应在应用退出时调用
     */
    public static void shutdown() {
        STREAM_EXECUTOR.shutdown();
        IO_EXECUTOR.shutdown();
    }
}
