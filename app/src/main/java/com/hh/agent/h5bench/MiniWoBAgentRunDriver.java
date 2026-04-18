package com.hh.agent.h5bench;

import com.hh.agent.core.api.MobileAgentApi;
import com.hh.agent.core.api.impl.NativeMobileAgentApi;
import com.hh.agent.core.event.AgentEventListener;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MiniWoBAgentRunDriver {
    private final MobileAgentApi mobileAgentApi;

    public MiniWoBAgentRunDriver() {
        this(NativeMobileAgentApi.getInstance());
    }

    MiniWoBAgentRunDriver(MobileAgentApi mobileAgentApi) {
        this.mobileAgentApi = mobileAgentApi;
    }

    public int runTask(MiniWoBTaskDefinition task) throws Exception {
        String sessionKey = "h5bench-" + task.getTaskId() + "-" + UUID.randomUUID();
        mobileAgentApi.clearHistoryAndLongTermMemory(sessionKey);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger toolUseCount = new AtomicInteger();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        mobileAgentApi.sendMessageStream(buildPrompt(task), sessionKey, new AgentEventListener() {
            @Override
            public void onTextDelta(String text) {
            }

            @Override
            public void onReasoningDelta(String text) {
            }

            @Override
            public void onToolUse(String id, String name, String argumentsJson) {
                toolUseCount.incrementAndGet();
            }

            @Override
            public void onToolResult(String id, String result) {
            }

            @Override
            public void onMessageEnd(String finishReason) {
                latch.countDown();
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                errorRef.set(new IllegalStateException(errorCode + ": " + errorMessage));
                latch.countDown();
            }
        });

        if (!latch.await(task.getTimeoutMs(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("agent_run_timeout");
        }
        if (errorRef.get() != null) {
            throw errorRef.get();
        }
        return Math.min(task.getMaxSteps(), toolUseCount.get());
    }

    private String buildPrompt(MiniWoBTaskDefinition task) {
        return "You are running a MiniWoB benchmark task.\n"
                + "Task ID: " + task.getTaskId() + "\n"
                + "Instruction: " + task.getInstruction() + "\n"
                + "Category: " + task.getCategory() + "\n"
                + "Only solve the current task in the currently loaded page.";
    }
}
