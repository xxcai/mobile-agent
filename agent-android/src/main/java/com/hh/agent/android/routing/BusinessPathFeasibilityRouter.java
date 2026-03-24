package com.hh.agent.android.routing;

import com.hh.agent.core.api.impl.NativeMobileAgentApi;
import com.hh.agent.core.tool.ToolExecutor;

import org.json.JSONObject;

import java.util.Map;

public final class BusinessPathFeasibilityRouter {

    interface RouterClient {
        String runStateless(String systemPrompt, String message);
    }

    private static final RouterClient DEFAULT_CLIENT =
            (systemPrompt, message) -> NativeMobileAgentApi.getInstance()
                    .runStateless(systemPrompt, message);

    private BusinessPathFeasibilityRouter() {
    }

    public static BusinessPathFeasibilityDecision route(String userRequest,
                                                        Map<String, ToolExecutor> tools) {
        return route(userRequest, tools, DEFAULT_CLIENT);
    }

    static BusinessPathFeasibilityDecision route(String userRequest,
                                                 Map<String, ToolExecutor> tools,
                                                 RouterClient client) {
        BusinessPathFeasibilityDecision fallbackDecision =
                DeterministicFeasibilityRouter.route(userRequest, tools);

        if (client == null) {
            return fallbackDecision;
        }

        String systemPrompt = FeasibilityRouterPromptFactory.buildSystemPrompt(tools);
        String rawResponse;
        try {
            rawResponse = client.runStateless(systemPrompt, userRequest != null ? userRequest : "");
        } catch (Exception ignored) {
            return fallbackDecision;
        }

        BusinessPathFeasibilityDecision decision =
                FeasibilityRouterResponseParser.parse(rawResponse, tools);
        return decision != null ? decision : fallbackDecision;
    }
}
