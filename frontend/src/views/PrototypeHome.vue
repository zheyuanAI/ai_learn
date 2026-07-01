<template>
  <section class="hero">
    <p class="eyebrow">Prototype Frozen</p>
    <h2>当前进入真实工程骨架阶段</h2>
    <p class="lead">
      已完成原型页面和第一轮 spec 冻结。这里的前端壳用于后续逐域接真实接口，不再返工导航结构。
    </p>
  </section>

  <section class="grid">
    <article v-for="item in milestones" :key="item.title" class="card">
      <h3>{{ item.title }}</h3>
      <p>{{ item.body }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
const milestones = [
  { title: "已完成", body: "prototype 入口页与 12 个原型页面已生成。" },
  { title: "已冻结", body: "ERP/WMS、MES、IoT、GIS、AI 五域的字段、接口和验收已补强。" },
  { title: "下一步", body: "先实现登录、菜单、租户上下文，再按采购入库和销售出库两条链路落 API。" },
];
</script>
