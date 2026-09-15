<template>
  <article class="message-row" :class="`is-${message.role}`">
    <div class="avatar">{{ message.role === "assistant" ? "AI" : "我" }}</div>
    <div class="message-body">
      <div class="message-head">
        <strong>{{ message.role === "assistant" ? "智能只读助手" : "我的问题" }}</strong>
        <span v-if="message.modelId">{{ message.modelId }}</span>
      </div>
      <AiToolProgress v-if="message.role === 'assistant'" :items="message.tools" />
      <div v-if="message.content" class="message-content">{{ message.content }}</div>
      <div v-else-if="message.status === 'streaming'" class="thinking-line">
        <span></span><span></span><span></span>{{ message.progressText || "正在处理" }}
      </div>
      <div v-if="message.errorMessage" class="message-error">{{ message.errorMessage }}</div>
      <dl v-if="message.role === 'assistant' && message.status === 'completed'" class="evidence-grid">
        <div v-if="message.sourceSummary"><dt>数据来源</dt><dd>{{ message.sourceSummary }}</dd></div>
        <div v-if="message.timeRangeSummary"><dt>数据时间</dt><dd>{{ message.timeRangeSummary }}</dd></div>
        <div v-if="message.toolCallSummary"><dt>调用工具</dt><dd>{{ message.toolCallSummary }}</dd></div>
        <div v-if="message.requestId"><dt>请求编号</dt><dd>{{ message.requestId }}</dd></div>
      </dl>
      <div v-if="message.status === 'interrupted'" class="interrupted">本次生成已中断，以上内容可能不完整。</div>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { AiUiMessage } from "../../../types/ai";
import AiToolProgress from "./AiToolProgress.vue";

defineProps<{ message: AiUiMessage }>();
</script>

<style scoped>
.message-row { display: flex; gap: 12px; align-items: flex-start; }
.message-row.is-user { flex-direction: row-reverse; }
.avatar { flex: 0 0 38px; width: 38px; height: 38px; display: grid; place-items: center; border-radius: 12px; color: #fff; background: linear-gradient(145deg, #173d72, #2d7ff9); font-size: 13px; font-weight: 800; box-shadow: 0 6px 18px rgba(31, 91, 169, .22); }
/* --- 用户头像：与气泡统一蓝色系渐变 --- */
.is-user .avatar { background: linear-gradient(145deg, #3670c2, #5a9cf5); box-shadow: 0 6px 18px rgba(54, 112, 194, .22); }
/* --- AI 回复气泡：加强边框、阴影和内间距，提升整体可读性 --- */
.message-body { max-width: min(780px, calc(100% - 56px)); padding: 18px 20px; border: 1px solid #c4d3e6; border-radius: 5px 16px 16px 16px; background: #fff; color: #1a2b3f; box-shadow: 0 4px 16px rgba(30, 55, 85, .10), 0 1px 3px rgba(30, 55, 85, .06); }
/* --- 用户气泡：提亮渐变底色，让白色文字更通透清晰 --- */
.is-user .message-body { border: 0; border-radius: 16px 5px 16px 16px; background: linear-gradient(145deg, #3b7ddf, #5a9cf5); color: #fff; box-shadow: 0 6px 20px rgba(58, 125, 220, .28), 0 2px 6px rgba(58, 125, 220, .10); }
/* --- 头部：加深标题色、模型标签色 --- */
.message-head { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 10px; font-size: 13.5px; }
.message-head strong { color: #14253a; }
.message-head span { color: #5c7290; font-size: 12px; font-weight: 600; }
/* --- 用户气泡头部：纯白标题 + 半透明辅助文字，层次更清晰 --- */
.is-user .message-head strong { color: #fff; text-shadow: 0 1px 3px rgba(30, 70, 140, .18); }
.is-user .message-head span { color: rgba(255, 255, 255, .82); }
/* --- 用户气泡正文：纯白 + 微量字间距增强可读性 --- */
.is-user .message-content { color: #fff; letter-spacing: .02em; }
/* --- 正文内容：加深颜色、增大字号和行高 --- */
.message-content { white-space: pre-wrap; overflow-wrap: anywhere; color: #1a2b3f; font-size: 15px; line-height: 1.82; letter-spacing: .01em; }
/* --- 流式生成中提示 --- */
.thinking-line { display: flex; align-items: center; gap: 5px; color: #4a5e74; font-size: 13.5px; }
.thinking-line span { width: 6px; height: 6px; border-radius: 50%; background: #2d7ff9; animation: blink 1.1s infinite; }
.thinking-line span:nth-child(2) { animation-delay: .15s; }
.thinking-line span:nth-child(3) { animation-delay: .3s; margin-right: 5px; }
/* --- 错误和中断提示 --- */
.message-error, .interrupted { margin-top: 10px; padding: 10px 13px; border-radius: 9px; color: #922030; background: #fff0f1; font-size: 13px; line-height: 1.6; }
/* --- 证据栏（来源、时间、工具、请求编号）：加深颜色、增大字号 --- */
.evidence-grid { display: grid; gap: 9px; margin: 16px 0 0; padding: 14px 0 0; border-top: 1px solid #dce4ef; }
.evidence-grid div { display: grid; grid-template-columns: 76px 1fr; gap: 8px; font-size: 13px; }
.evidence-grid dt { color: #5a6d82; font-weight: 600; }
.evidence-grid dd { margin: 0; color: #2a3f57; overflow-wrap: anywhere; }
@keyframes blink { 50% { opacity: .25; transform: translateY(-2px); } }
</style>
