<template>
  <div class="ai-page">
    <PageHeader
      title="智能只读助手"
      tag="AI / CONTROLLED READ-ONLY"
      description="用自然语言查询订单、库存、工单、告警与追溯事实。AI 只读分析，不会代替你提交、审核或确认业务操作。"
    >
      <template #actions>
        <el-button :disabled="isStreaming" @click="newConversation">新建对话</el-button>
        <el-button v-if="isStreaming" type="danger" plain @click="stop">停止生成</el-button>
      </template>
    </PageHeader>

    <section class="ai-shell">
      <aside class="context-panel">
        <div class="provider-card" :class="{ 'is-offline': capabilities && !capabilities.provider_enabled }">
          <span class="provider-dot"></span>
          <div>
            <strong>{{ capabilities?.model_id || "wms-assistant" }}</strong>
            <small>{{ providerStatus }}</small>
          </div>
        </div>

        <div class="side-block">
          <h3>我可以帮你</h3>
          <button v-for="item in suggestions" :key="item" type="button" @click="draft = item">{{ item }}</button>
        </div>

        <div v-if="pageContext.entity_type" class="side-block page-context">
          <h3>当前页面上下文</h3>
          <span>{{ pageContext.entity_type }}</span>
          <small>{{ pageContext.entity_id || "未指定业务对象" }}</small>
        </div>

        <div class="guardrail-card">
          <strong>安全边界</strong>
          <p>只调用你有权限且已加入白名单的查询工具；涉及多个角色时只给出文字建议，不执行跳转或业务操作。</p>
        </div>
      </aside>

      <main class="conversation-panel">
        <div ref="messageListRef" class="message-list">
          <div v-if="messages.length === 0" class="welcome-card">
            <div class="welcome-mark">AI</div>
            <h2>今天想先了解哪一段业务？</h2>
            <p>你可以问“为什么不能发货”“这张工单卡在哪里”“某个告警影响了什么”，回答会附带真实来源、责任角色和建议处理顺序。</p>
            <div class="welcome-actions">
              <button v-for="item in suggestions.slice(0, 3)" :key="item" type="button" @click="draft = item">{{ item }}</button>
            </div>
          </div>

          <AiMessage v-for="message in messages" :key="message.id" :message="message" />
        </div>

        <form class="composer" @submit.prevent="submit">
          <textarea
            v-model="draft"
            :disabled="isStreaming"
            maxlength="4000"
            rows="3"
            placeholder="描述你想查询的业务问题；Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact.prevent="submit"
          ></textarea>
          <div class="composer-footer">
            <span>{{ draft.length }}/4000 · 回答仅作业务辅助，请以系统事实为准</span>
            <button type="submit" :disabled="!draft.trim() || isStreaming">
              {{ isStreaming ? "生成中" : "发送问题" }}
            </button>
          </div>
        </form>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { getAiCapabilities } from "../../api/ai";
import PageHeader from "../../components/common/PageHeader.vue";
import { useAiStream } from "../../composables/useAiStream";
import type { AiCapabilities, AiPageContext } from "../../types/ai";
import AiMessage from "./components/AiMessage.vue";

const route = useRoute();
const draft = ref("");
const capabilities = ref<AiCapabilities | null>(null);
const messageListRef = ref<HTMLElement | null>(null);
const suggestions = [
  "这张销售订单为什么还不能发货？",
  "帮我查看关联订单、工单和设备的完整追溯链",
  "今天有哪些需要我优先关注的异常？",
  "当前库存能否满足这张订单？",
];

const pageContext = computed<AiPageContext>(() => ({
  entity_type: typeof route.query.entityType === "string" ? route.query.entityType : undefined,
  entity_id: typeof route.query.entityId === "string" ? route.query.entityId : undefined,
  entry_page_code: typeof route.query.entryPageCode === "string" ? route.query.entryPageCode : undefined,
}));

const providerStatus = computed(() => {
  if (!capabilities.value) return "正在读取服务能力";
  return capabilities.value.provider_enabled
    ? `流式服务可用 · ${capabilities.value.tools.length} 个授权工具`
    : "未启用或尚未配置 API Key";
});

const { messages, isStreaming, send, stop, newConversation } = useAiStream(scrollToBottom);

async function submit(): Promise<void> {
  const question = draft.value.trim();
  if (!question || isStreaming.value) return;
  draft.value = "";
  await send(question, pageContext.value);
}

function scrollToBottom(): void {
  nextTick(() => {
    if (messageListRef.value) messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  });
}

onMounted(async () => {
  try {
    capabilities.value = (await getAiCapabilities()).data;
  } catch {
    capabilities.value = null;
  }
});
</script>

<style scoped>
.ai-page { min-height: calc(100vh - 100px); }
.ai-shell { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 16px; min-height: 660px; margin-top: 16px; }
.context-panel, .conversation-panel { border: 1px solid #dfe6ef; border-radius: 16px; background: #fff; box-shadow: 0 10px 30px rgba(30, 53, 82, .055); }
.context-panel { padding: 16px; background: linear-gradient(180deg, #f8fbff, #fff 45%); }
.provider-card { display: flex; align-items: center; gap: 10px; padding: 12px; border: 1px solid #cfe7dc; border-radius: 12px; background: #f2fbf7; }
.provider-card.is-offline { border-color: #efd5d8; background: #fff7f7; }
.provider-dot { width: 10px; height: 10px; border-radius: 50%; background: #23a66f; box-shadow: 0 0 0 4px rgba(35, 166, 111, .12); }
.is-offline .provider-dot { background: #d85a67; box-shadow: 0 0 0 4px rgba(216, 90, 103, .1); }
.provider-card strong, .provider-card small { display: block; }
.provider-card strong { color: #203650; font-size: 13px; }
.provider-card small { margin-top: 3px; color: #6f8095; font-size: 11px; }
.side-block { margin-top: 20px; }
.side-block h3 { margin: 0 0 10px; color: #263d59; font-size: 12px; text-transform: uppercase; letter-spacing: .08em; }
.side-block button { width: 100%; margin-bottom: 7px; padding: 9px 10px; border: 1px solid #e0e8f2; border-radius: 9px; color: #4c6078; background: #fff; text-align: left; cursor: pointer; line-height: 1.45; }
.side-block button:hover { color: #1c64c7; border-color: #a8c8f2; background: #f7fbff; }
.page-context { padding: 12px; border-radius: 11px; background: #f2f6fb; }
.page-context span, .page-context small { display: block; overflow-wrap: anywhere; }
.page-context span { color: #245fae; font-size: 12px; font-weight: 800; }
.page-context small { margin-top: 4px; color: #75869a; font-size: 11px; }
.guardrail-card { margin-top: 20px; padding: 13px; border-radius: 11px; color: #65552a; background: #fff9e9; }
.guardrail-card strong { font-size: 12px; }
.guardrail-card p { margin: 6px 0 0; font-size: 11px; line-height: 1.6; }
.conversation-panel { display: grid; grid-template-rows: minmax(0, 1fr) auto; min-width: 0; overflow: hidden; }
.message-list { display: flex; flex-direction: column; gap: 18px; max-height: calc(100vh - 300px); min-height: 500px; padding: 22px; overflow-y: auto; background: radial-gradient(circle at 70% 0, rgba(45, 127, 249, .045), transparent 35%); }
.welcome-card { margin: auto; max-width: 660px; padding: 34px; text-align: center; }
.welcome-mark { width: 54px; height: 54px; display: grid; place-items: center; margin: 0 auto 16px; border-radius: 17px; color: #fff; background: linear-gradient(145deg, #173d72, #2d7ff9); font-weight: 900; box-shadow: 0 12px 30px rgba(45, 127, 249, .24); }
.welcome-card h2 { margin: 0; color: #203650; font-size: 23px; }
.welcome-card p { margin: 12px auto 20px; color: #718197; line-height: 1.75; }
.welcome-actions { display: flex; justify-content: center; flex-wrap: wrap; gap: 8px; }
.welcome-actions button { padding: 9px 12px; border: 1px solid #d8e5f4; border-radius: 999px; color: #315a8b; background: #f8fbff; cursor: pointer; }
.composer { margin: 14px; border: 1px solid #cdd9e8; border-radius: 14px; background: #fff; box-shadow: 0 8px 24px rgba(40, 68, 103, .09); overflow: hidden; }
.composer:focus-within { border-color: #77aef5; box-shadow: 0 0 0 3px rgba(45, 127, 249, .09), 0 8px 24px rgba(40, 68, 103, .09); }
.composer textarea { width: 100%; min-height: 76px; padding: 14px 15px 8px; border: 0; outline: 0; resize: none; color: #253b55; background: transparent; font: inherit; line-height: 1.55; box-sizing: border-box; }
.composer-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 10px 10px 15px; }
.composer-footer span { color: #8a98aa; font-size: 11px; }
.composer-footer button { padding: 9px 17px; border: 0; border-radius: 10px; color: #fff; background: linear-gradient(135deg, #2468c5, #2d7ff9); font-weight: 700; cursor: pointer; }
.composer-footer button:disabled { opacity: .45; cursor: not-allowed; }
@media (max-width: 900px) {
  .ai-shell { grid-template-columns: 1fr; }
  .context-panel { display: none; }
  .message-list { max-height: none; min-height: 480px; }
}
</style>
