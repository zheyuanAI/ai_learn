export type AiStreamEventName =
  | "meta"
  | "progress"
  | "tool_started"
  | "tool_finished"
  | "delta"
  | "done"
  | "error";

export interface AiPageContext {
  entity_type?: string;
  entity_id?: string;
  entry_page_code?: string;
}

export interface AiChatRequest {
  message: string;
  session_id?: string;
  tool_whitelist?: string[];
  page_context?: AiPageContext;
}

export type AiActionCode =
  | "SALES_ORDER_DETAIL"
  | "SALES_ORDER_FULFILLMENT"
  | "PURCHASE_ORDER_DETAIL"
  | "PURCHASE_QUALITY_PROCESSING"
  | "WORK_ORDER_DETAIL"
  | "DEVICE_DETAIL"
  | "DEVICE_ALARM_DETAIL"
  | "INVENTORY_BALANCE"
  | "INVENTORY_RESERVATION"
  | "ROLE_PERMISSION_CONFIGURATION"
  | "TRACEABILITY";

export interface AiNavigationAction {
  action_code: AiActionCode;
  label: string;
  entity_type?: string;
  entity_id?: string;
  reason?: string;
  confirm_text?: string;
}

export interface AiCapabilities {
  provider_enabled: boolean;
  model_id: string;
  streaming: boolean;
  tools: string[];
  navigation_actions: AiActionCode[];
}

export interface AiChatDone {
  request_id: string;
  session_id: string;
  answer: string;
  source_summary: string;
  time_range_summary: string;
  tool_call_summary: string;
  navigation_actions: AiNavigationAction[];
  model_id: string;
  input_tokens: number;
  output_tokens: number;
  status: string;
}

export interface AiToolProgressItem {
  callId: string;
  toolName: string;
  status: "running" | "success" | "failed";
  sourceSummary?: string;
}

export interface AiUiMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  status: "streaming" | "completed" | "interrupted" | "failed";
  requestId?: string;
  modelId?: string;
  progressText?: string;
  sourceSummary?: string;
  timeRangeSummary?: string;
  toolCallSummary?: string;
  tools: AiToolProgressItem[];
  errorMessage?: string;
}
