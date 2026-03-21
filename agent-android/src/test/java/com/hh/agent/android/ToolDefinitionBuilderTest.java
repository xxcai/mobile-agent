package com.hh.agent.android;

import com.hh.agent.core.ToolDefinition;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolDefinitionBuilderTest {

    @Test
    public void buildsSchemaAndExampleFromDeclaredParams() throws Exception {
        ToolDefinition definition = ToolDefinition.builder("发送消息", "向指定联系人发送文本消息")
                .intentExamples("给李四发消息")
                .stringParam("contact_id", "联系人ID", true, "003")
                .stringParam("message", "消息内容", true, "明天下午3点开会")
                .build();

        assertEquals(
                "{\"type\":\"object\",\"properties\":{\"contact_id\":{\"type\":\"string\",\"description\":\"联系人ID\"},\"message\":{\"type\":\"string\",\"description\":\"消息内容\"}},\"required\":[\"contact_id\",\"message\"]}",
                definition.getArgsSchemaJsonString()
        );
        assertEquals(
                "{\"contact_id\":\"003\",\"message\":\"明天下午3点开会\"}",
                definition.getArgsExampleJsonString()
        );
    }

    @Test
    public void allowsEmptyIntentExamplesAndOptionalExamples() throws Exception {
        ToolDefinition definition = ToolDefinition.builder("读取剪贴板", "读取当前剪贴板中的文本内容")
                .boolParam("trim", "是否裁剪空白", false)
                .build();

        assertEquals(0, definition.getIntentExamples().size());
        assertEquals("{}", definition.getArgsExampleJsonString());
        assertTrue(definition.getArgsSchemaJsonString().contains("\"required\":[]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyDescription() {
        ToolDefinition.builder("发送消息", "   ")
                .stringParam("message", "消息内容", true)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateParamName() {
        ToolDefinition.builder("发送消息", "向指定联系人发送文本消息")
                .stringParam("message", "消息内容", true)
                .stringParam("message", "重复字段", false)
                .build();
    }
}
