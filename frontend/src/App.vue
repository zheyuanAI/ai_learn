<template>
  <div class="shell">
    <aside class="rail">
      <div class="brand">
        <p class="eyebrow">AI Learn</p>
        <h1>制造与仓储协同执行平台</h1>
        <p>一期壳层按黄金业务闭环组织；原型展示目标设计，不代表功能已经实现。</p>
      </div>
      <nav class="nav-list">
        <RouterLink v-for="item in navItems" :key="item.to" class="nav-link" :to="item.to">
          <strong>{{ item.label }}</strong>
          <span>{{ item.detail }}</span>
        </RouterLink>
      </nav>
    </aside>
    <main class="workspace">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
const navItems = [
  { to: "/", label: "一期总览", detail: "一条黄金业务闭环" },
  { to: "/erp-wms", label: "供需与仓储", detail: "人工关联、预留、收发存" },
  { to: "/mes", label: "制造执行", detail: "领退料、工序执行、质检" },
  { to: "/iot", label: "设备事实", detail: "遥测、状态、告警分离" },
  { to: "/gis", label: "地图与看板", detail: "业务事实只读展示" },
  { to: "/ai", label: "AI 只读助手", detail: "受控查询、来源与审计" },
];
</script>
