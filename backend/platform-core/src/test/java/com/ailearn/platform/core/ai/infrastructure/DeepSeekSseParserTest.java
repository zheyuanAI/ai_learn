package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.model.AiModelRoundResult;
import com.ailearn.platform.core.ai.model.AiModelStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** DeepSeek Responses API 语义化 SSE 解析回归。 */
class DeepSeekSseParserTest {

    @Test
    void shouldParseTextFunctionCallUsageAndTerminalEvent() {
        List<String> deltas = new ArrayList<>();
        DeepSeekSseParser parser = new DeepSeekSseParser(new ObjectMapper(), deltas::add);

        feed(parser, "{\"type\":\"response.created\",\"response\":{\"id\":\"resp-1\",\"model\":\"deepseek-v4-flash\"}}");
        feed(parser, "{\"type\":\"response.output_text.delta\",\"delta\":\"库存\"}");
        feed(parser, "{\"type\":\"response.output_text.delta\",\"delta\":\"正常\"}");
        feed(parser, "{\"type\":\"response.output_item.done\",\"item\":{\"type\":\"function_call\",\"call_id\":\"call-1\",\"name\":\"queryTrace\",\"arguments\":\"{\\\"entity_type\\\":\\\"SALES_ORDER\\\",\\\"entity_id\\\":\\\"00000000-0000-0000-0000-000000000001\\\"}\"}}");
        feed(parser, "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp-1\",\"model\":\"deepseek-v4-flash\",\"usage\":{\"input_tokens\":12,\"output_tokens\":7}}}");

        AiModelRoundResult result = parser.finish();

        assertEquals(AiModelStatus.Completed, result.status());
        assertEquals("resp-1", result.responseId());
        assertEquals("deepseek-v4-flash", result.modelId());
        assertEquals("库存正常", result.text());
        assertEquals(List.of("库存", "正常"), deltas);
        assertEquals("queryTrace", result.functionCalls().getFirst().name());
        assertEquals(12, result.inputTokens());
        assertEquals(7, result.outputTokens());
    }

    @Test
    void shouldIgnoreReasoningAndFailWhenTerminalEventIsMissing() {
        List<String> deltas = new ArrayList<>();
        DeepSeekSseParser parser = new DeepSeekSseParser(new ObjectMapper(), deltas::add);

        feed(parser, "{\"type\":\"response.reasoning_text.delta\",\"delta\":\"内部推理\"}");
        AiModelRoundResult result = parser.finish();

        assertEquals(AiModelStatus.Failed, result.status());
        assertEquals("", result.text());
        assertEquals(List.of(), deltas);
        assertEquals("DeepSeek SSE 缺少终止事件", result.errorMessage());
    }

    private static void feed(DeepSeekSseParser parser, String json) {
        parser.acceptLine("event: ignored");
        parser.acceptLine("data: " + json);
        parser.acceptLine("");
    }
}
