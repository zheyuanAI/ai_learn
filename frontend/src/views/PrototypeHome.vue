<template>
  <section class="hero">
    <p class="eyebrow">Phase 1 Target Design</p>
    <h2>围绕一条黄金业务闭环推进</h2>
    <p class="lead">
      销售需求通过人工来源关系连接生产与采购，再贯通收货、上架、领退料、工序执行、成品入库、库存预留、拣货和发货。这里仅展示一期目标与导航，不代表业务功能已经实现。
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
  { title: "业务主线", body: "销售行 -> 生产工单 -> 采购来源 -> 制造执行 -> 销售交付。" },
  { title: "事实边界", body: "预留不扣实物，上架与拣货只移位，发货才扣减总库存；遥测、状态与告警分离。" },
  { title: "展示边界", body: "二维地图、看板、追溯与 AI 均只读，不建立第二套事实来源。" },
];
</script>
