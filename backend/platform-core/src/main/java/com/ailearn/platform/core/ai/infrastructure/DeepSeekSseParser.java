package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.model.AiFunctionCall;
import com.ailearn.platform.core.ai.model.AiModelRoundResult;
import com.ailearn.platform.core.ai.model.AiModelStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** DeepSeek Responses API 语义化 SSE 解析器，不依赖 `[DONE]`。 */
public class DeepSeekSseParser {
    private final ObjectMapper objectMapper;
    private final Consumer<String> textDeltaConsumer;
    private final StringBuilder eventData = new StringBuilder();
    private final StringBuilder text = new StringBuilder();
    private final List<AiFunctionCall> functionCalls = new ArrayList<>();
    private String responseId = "";
    private String modelId = "";
    private int inputTokens;
    private int outputTokens;
    private AiModelStatus status;
    private String errorMessage = "";

    /** 注入 JSON 解析器和正文增量监听器。 */
    public DeepSeekSseParser(ObjectMapper objectMapper, Consumer<String> textDeltaConsumer) {
        this.objectMapper = objectMapper;
        this.textDeltaConsumer = textDeltaConsumer == null ? ignored -> { } : textDeltaConsumer;
    }

    /**
     * 接收 HTTP BodyHandlers.ofLines 产生的一行 SSE。
     * 空行触发一个事件解析；event/id/retry 行不作为 JSON 数据处理。
     */
    public void acceptLine(String line) {
        if (line == null || line.isEmpty()) {
            flushEvent();
            return;
        }
        if (line.startsWith("data:")) {
            if (!eventData.isEmpty()) {
                eventData.append('\n');
            }
            eventData.append(line.substring(5).stripLeading());
        }
    }

    /** 结束输入并返回完整的一轮响应。 */
    public AiModelRoundResult finish() {
        flushEvent();
        if (status == null) {
            status = AiModelStatus.Failed;
            errorMessage = "DeepSeek SSE 缺少终止事件";
        }
        return new AiModelRoundResult(responseId, modelId, text.toString(), functionCalls,
                inputTokens, outputTokens, status, errorMessage);
    }

    private void flushEvent() {
        if (eventData.isEmpty()) {
            return;
        }
        String json = eventData.toString();
        eventData.setLength(0);
        try {
            handle(objectMapper.readTree(json));
        } catch (IOException exception) {
            status = AiModelStatus.Failed;
            errorMessage = "DeepSeek SSE 事件无法解析";
        }
    }

    private void handle(JsonNode event) {
        String type = text(event, "type");
        if (type.isBlank()) {
            type = text(event, "event");
        }
        switch (type) {
            case "response.created", "response.in_progress" -> readResponseIdentity(event.path("response"));
            case "response.output_text.delta" -> appendText(text(event, "delta"));
            case "response.output_item.done" -> readFunctionCall(event.path("item"));
            case "response.completed" -> complete(event.path("response"), AiModelStatus.Completed);
            case "response.incomplete" -> complete(event.path("response"), AiModelStatus.Incomplete);
            case "response.failed" -> complete(event.path("response"), AiModelStatus.Failed);
            default -> {
                // 其他 reasoning/content 生命周期事件不向浏览器透传，也不影响最终结果。
            }
        }
    }

    private void appendText(String delta) {
        if (!delta.isEmpty()) {
            text.append(delta);
            textDeltaConsumer.accept(delta);
        }
    }

    private void readFunctionCall(JsonNode item) {
        if (!"function_call".equals(text(item, "type"))) {
            return;
        }
        functionCalls.add(new AiFunctionCall(text(item, "call_id"), text(item, "name"),
                text(item, "arguments")));
    }

    private void complete(JsonNode response, AiModelStatus terminalStatus) {
        readResponseIdentity(response);
        JsonNode usage = response.path("usage");
        inputTokens = usage.path("input_tokens").asInt(0);
        outputTokens = usage.path("output_tokens").asInt(0);
        status = terminalStatus;
        if (terminalStatus == AiModelStatus.Failed) {
            JsonNode error = response.path("error");
            errorMessage = text(error, "message");
            if (errorMessage.isBlank()) {
                errorMessage = "DeepSeek 返回失败终止事件";
            }
        }
    }

    private void readResponseIdentity(JsonNode response) {
        if (response == null || response.isMissingNode()) {
            return;
        }
        String id = text(response, "id");
        String model = text(response, "model");
        if (!id.isBlank()) {
            responseId = id;
        }
        if (!model.isBlank()) {
            modelId = model;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
