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
.avatar { flex: 0 0 34px; width: 34px; height: 34px; display: grid; place-items: center; border-radius: 11px; color: #fff; background: linear-gradient(145deg, #173d72, #2d7ff9); font-size: 12px; font-weight: 800; box-shadow: 0 6px 18px rgba(31, 91, 169, .2); }
.is-user .avatar { background: #64748b; }
.message-body { max-width: min(780px, calc(100% - 52px)); padding: 14px 16px; border: 1px solid #dfe7f1; border-radius: 5px 16px 16px 16px; background: #fff; box-shadow: 0 6px 20px rgba(35, 57, 86, .05); }
.is-user .message-body { border: 0; border-radius: 16px 5px 16px 16px; background: linear-gradient(145deg, #245fae, #2d7ff9); color: #fff; box-shadow: 0 8px 24px rgba(45, 127, 249, .2); }
.message-head { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 8px; font-size: 13px; }
.message-head span { color: #8190a3; font-size: 11px; font-weight: 600; }
.is-user .message-head span { color: rgba(255, 255, 255, .72); }
.message-content { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 14px; line-height: 1.72; }
.thinking-line { display: flex; align-items: center; gap: 5px; color: #62738a; font-size: 13px; }
.thinking-line span { width: 5px; height: 5px; border-radius: 50%; background: #2d7ff9; animation: blink 1.1s infinite; }
.thinking-line span:nth-child(2) { animation-delay: .15s; }
.thinking-line span:nth-child(3) { animation-delay: .3s; margin-right: 4px; }
.message-error, .interrupted { margin-top: 10px; padding: 9px 11px; border-radius: 9px; color: #a53442; background: #fff1f2; font-size: 12px; }
.evidence-grid { display: grid; gap: 8px; margin: 14px 0 0; padding-top: 12px; border-top: 1px solid #edf1f6; }
.evidence-grid div { display: grid; grid-template-columns: 70px 1fr; gap: 8px; font-size: 12px; }
.evidence-grid dt { color: #8291a4; }
.evidence-grid dd { margin: 0; color: #465970; overflow-wrap: anywhere; }
@keyframes blink { 50% { opacity: .25; transform: translateY(-2px); } }
</style>
