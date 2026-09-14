import request, {
  TOKEN_KEY,
  TENANT_KEY,
  generateRequestId,
  type ApiResponse,
} from "../utils/request";
import type {
  AiCapabilities,
  AiChatRequest,
  AiStreamEventName,
} from "../types/ai";

const AI_STREAM_EVENTS = new Set<AiStreamEventName>([
  "meta", "progress", "tool_started", "tool_finished", "delta", "done", "error",
]);

export interface AiStreamEvent {
  name: AiStreamEventName;
  data: unknown;
}

/** 查询服务端实际可用模型和只读工具，不向前端暴露 Provider 密钥。 */
export function getAiCapabilities(): Promise<ApiResponse<AiCapabilities>> {
  return request<AiCapabilities>({ url: "/api/ai/capabilities", method: "get" });
}

/**
 * 用途：解析一个或多个完整 SSE 帧。
 * 入参：不包含尾部分隔空行的文本；出参：受支持的命名事件；流程：合并多行 data 后再解析 JSON。
 */
export function parseAiSseFrames(frameText: string): AiStreamEvent[] {
  return frameText
    .split(/\n\n+/)
    .map((frame) => {
      let name = "";
      const dataLines: string[] = [];
      for (const rawLine of frame.split("\n")) {
        const line = rawLine.endsWith("\r") ? rawLine.slice(0, -1) : rawLine;
        if (line.startsWith("event:")) name = line.slice(6).trim();
        if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
      }
      if (!AI_STREAM_EVENTS.has(name as AiStreamEventName) || dataLines.length === 0) return null;
      try {
        return { name: name as AiStreamEventName, data: JSON.parse(dataLines.join("\n")) };
      } catch {
        return null;
      }
    })
    .filter((item): item is AiStreamEvent => item !== null);
}

/**
 * 使用 fetch 发送带 Bearer Token 的 POST 请求并逐块解析命名 SSE；AbortSignal 用于停止生成。
 */
export async function streamAiChat(
  payload: AiChatRequest,
  onEvent: (event: AiStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const baseUrl = String(import.meta.env.VITE_API_BASE_URL || "http://localhost:20001").replace(/\/+$/, "");
  const headers: Record<string, string> = {
    "Content-Type": "application/json;charset=utf-8",
    Accept: "text/event-stream",
    "X-Request-Id": generateRequestId(),
  };
  const token = localStorage.getItem(TOKEN_KEY);
  const tenant = localStorage.getItem(TENANT_KEY);
  if (token) headers.Authorization = `Bearer ${token}`;
  if (tenant) headers["X-Tenant-Id"] = tenant;

  const response = await fetch(`${baseUrl}/api/ai/chat/stream`, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
    signal,
  });
  if (!response.ok || !response.body) {
    let message = `AI 流式请求失败（HTTP ${response.status}）`;
    try {
      const body = await response.json() as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // 非 JSON 错误响应保留 HTTP 状态提示。
    }
    throw new Error(message);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, "\n");
    const boundary = buffer.lastIndexOf("\n\n");
    if (boundary >= 0) {
      const completeFrames = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      parseAiSseFrames(completeFrames).forEach(onEvent);
    }
    if (done) break;
  }
  if (buffer.trim()) parseAiSseFrames(buffer).forEach(onEvent);
}
