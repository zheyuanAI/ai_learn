import { computed, nextTick, ref } from "vue";
import { streamAiChat, type AiStreamEvent } from "../api/ai";
import type {
  AiChatDone,
  AiChatRequest,
  AiPageContext,
  AiToolProgressItem,
  AiUiMessage,
} from "../types/ai";

function uid(prefix: string): string {
  return typeof crypto !== "undefined" && crypto.randomUUID
    ? `${prefix}-${crypto.randomUUID()}`
    : `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** 管理单会话 SSE 生命周期、短缓冲正文、工具进度、停止生成和错误保留。 */
export function useAiStream(onUpdated?: () => void) {
  const notifyUpdated = onUpdated ?? (() => {});
  const messages = ref<AiUiMessage[]>([]);
  const sessionId = ref("");
  const isStreaming = ref(false);
  let controller: AbortController | null = null;
  let deltaBuffer = "";
  let flushHandle = 0;

  const currentAssistant = computed(() =>
    [...messages.value].reverse().find((message) => message.role === "assistant" && message.status === "streaming"),
  );

  function flushDelta(): void {
    flushHandle = 0;
    const assistant = currentAssistant.value;
    if (assistant && deltaBuffer) {
      assistant.content += deltaBuffer;
      deltaBuffer = "";
      nextTick(notifyUpdated);
    }
  }

  function scheduleFlush(): void {
    if (!flushHandle) flushHandle = window.setTimeout(flushDelta, 32);
  }

  function handleEvent(event: AiStreamEvent): void {
    const assistant = currentAssistant.value;
    if (!assistant) return;
    const data = (event.data || {}) as Record<string, any>;
    if (event.name === "meta") {
      assistant.requestId = String(data.request_id || "");
      assistant.modelId = String(data.model_id || "");
      sessionId.value = String(data.session_id || sessionId.value);
    } else if (event.name === "progress") {
      assistant.progressText = String(data.message || "正在处理");
    } else if (event.name === "tool_started") {
      assistant.tools.push({
        callId: String(data.call_id || uid("call")),
        toolName: String(data.tool_name || "unknown"),
        status: "running",
      });
    } else if (event.name === "tool_finished") {
      const tool = assistant.tools.find((item) => item.callId === String(data.call_id || ""));
      if (tool) {
        tool.status = String(data.status).toLowerCase() === "success" ? "success" : "failed";
        tool.sourceSummary = String(data.source_summary || "");
      }
    } else if (event.name === "delta") {
      deltaBuffer += String(data.text || "");
      scheduleFlush();
    } else if (event.name === "done") {
      flushDelta();
      const done = data as unknown as AiChatDone;
      assistant.content = done.answer || assistant.content;
      assistant.requestId = done.request_id || assistant.requestId;
      assistant.modelId = done.model_id || assistant.modelId;
      assistant.sourceSummary = done.source_summary;
      assistant.timeRangeSummary = done.time_range_summary;
      assistant.toolCallSummary = done.tool_call_summary;
      assistant.progressText = "回答完成";
      assistant.status = "completed";
    } else if (event.name === "error") {
      flushDelta();
      assistant.errorMessage = String(data.message || "AI 服务暂不可用");
      assistant.requestId = String(data.request_id || assistant.requestId || "");
      assistant.status = "failed";
    }
    nextTick(notifyUpdated);
  }

  async function send(message: string, pageContext?: AiPageContext): Promise<void> {
    const question = message.trim();
    if (!question || isStreaming.value) return;
    messages.value.push({ id: uid("user"), role: "user", content: question, status: "completed", tools: [] });
    const assistant: AiUiMessage = {
      id: uid("assistant"), role: "assistant", content: "", status: "streaming",
      progressText: "正在连接 AI 服务", tools: [],
    };
    messages.value.push(assistant);
    isStreaming.value = true;
    controller = new AbortController();
    const payload: AiChatRequest = { message: question };
    if (sessionId.value) payload.session_id = sessionId.value;
    if (pageContext && pageContext.entity_type) payload.page_context = pageContext;
    nextTick(notifyUpdated);

    try {
      await streamAiChat(payload, handleEvent, controller.signal);
      flushDelta();
      if (assistant.status === "streaming") {
        assistant.status = "interrupted";
        assistant.errorMessage = "连接已结束，但未收到完成事件";
      }
    } catch (error) {
      flushDelta();
      if (controller?.signal.aborted) {
        assistant.status = "interrupted";
        assistant.progressText = "已停止生成";
      } else {
        assistant.status = "failed";
        assistant.errorMessage = error instanceof Error ? error.message : "AI 流式请求失败";
      }
    } finally {
      isStreaming.value = false;
      controller = null;
      nextTick(notifyUpdated);
    }
  }

  function stop(): void {
    controller?.abort();
  }

  function newConversation(): void {
    if (isStreaming.value) return;
    sessionId.value = "";
    messages.value = [];
  }

  return { messages, sessionId, isStreaming, send, stop, newConversation };
}
