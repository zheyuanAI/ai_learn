<template>
  <section class="hero">
    <p class="eyebrow">Domain Ready</p>
    <h2>{{ title }}</h2>
    <p class="lead">{{ summary }}</p>
  </section>

  <section class="grid two">
    <article class="card">
      <h3>Spec 目录</h3>
      <p>{{ specPath }}</p>
    </article>
    <article class="card">
      <h3>原型基线</h3>
      <p>{{ prototypePath }}</p>
    </article>
  </section>

  <section class="grid">
    <article class="card">
      <h3>开发提示</h3>
      <p>先接列表查询和详情查询，再补写接口；所有写接口默认要幂等、租户过滤和权限校验。</p>
    </article>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  summary: string;
  specPath: string;
  prototypePath: string;
}>();
</script>
