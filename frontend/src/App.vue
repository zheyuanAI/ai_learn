<template>
  <div class="shell">
    <aside class="rail">
      <div class="brand">
        <p class="eyebrow">AI Learn</p>
        <h1>制造协同平台骨架</h1>
        <p>原型已完成，当前进入真实工程骨架阶段。</p>
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
  { to: "/", label: "总览", detail: "六条主线与冻结状态" },
  { to: "/erp-wms", label: "ERP/WMS", detail: "采购、销售、库存" },
  { to: "/mes", label: "MES", detail: "工单、报工、质检" },
  { to: "/iot", label: "IoT", detail: "设备、遥测、告警" },
  { to: "/gis", label: "GIS", detail: "地图、看板、三维" },
  { to: "/ai", label: "AI 助手", detail: "知识库、审计、只读工具" },
];
</script>
