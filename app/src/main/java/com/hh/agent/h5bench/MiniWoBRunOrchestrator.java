package com.hh.agent.h5bench;

import java.util.List;
import java.util.concurrent.Executor;

public interface MiniWoBRunOrchestrator {
    List<MiniWoBRunRecord> runBenchmarks() throws Exception;

    interface Provider {
        MiniWoBRunOrchestrator getMiniWoBRunOrchestrator();
    }

    interface ExecutorProvider {
        Executor getMiniWoBRunExecutor();
    }
}
