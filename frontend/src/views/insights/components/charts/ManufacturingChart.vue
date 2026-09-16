<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
/**
 * 生产执行综合图表 (ManufacturingChart)
 * 职责：左侧展示工单状态柱状分布，右侧展示报工良品率环形
 */

import { ref, watch } from "vue";
import type { EChartsOption } from "echarts";
import { useChart } from "./useChart";
import { CHART_COLORS, DEFAULT_TOOLTIP } from "./theme";
import { parseMetricNumber } from "../../utils/metric-dictionary";

const props = defineProps<{
  metrics?: Record<string, unknown>;
}>();

const chartRef = ref<HTMLElement | null>(null);

function getOption(): EChartsOption {
  const completed = parseMetricNumber(props.metrics?.completed_count, 0);
  const inProgress = parseMetricNumber(props.metrics?.in_progress_count, 0);
  const qualityBlocked = parseMetricNumber(props.metrics?.quality_blocked_count, 0);

  const qualified = parseMetricNumber(props.metrics?.qualified_qty, 0);
  const defect = parseMetricNumber(props.metrics?.defect_qty, 0);
  const reported = parseMetricNumber(props.metrics?.reported_qty, qualified + defect);

  const yieldRate = reported > 0 ? Math.min(100, Math.round((qualified / reported) * 100)) : (defect === 0 ? 100 : 0);

  return {
    tooltip: {
      ...DEFAULT_TOOLTIP,
      trigger: "item",
    },
    grid: {
      left: 8,
      right: "48%",
      top: 18,
      bottom: 24,
      containLabel: true,
    },
    xAxis: {
      type: "category",
      data: ["已完工", "执行中", "阻滞"],
      axisLine: { lineStyle: { color: CHART_COLORS.border } },
      axisTick: { show: false },
      axisLabel: { color: CHART_COLORS.textDim, fontSize: 10 },
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { color: CHART_COLORS.border } },
      axisLabel: { color: CHART_COLORS.textDim, fontSize: 10 },
    },
    series: [
      {
        type: "bar",
        barWidth: 22,
        data: [
          {
            value: completed,
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: CHART_COLORS.green },
                  { offset: 1, color: CHART_COLORS.greenDeep },
                ],
              },
              borderRadius: [4, 4, 0, 0],
            },
          },
          {
            value: inProgress,
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: CHART_COLORS.accent },
                  { offset: 1, color: CHART_COLORS.accentDeep },
                ],
              },
              borderRadius: [4, 4, 0, 0],
            },
          },
          {
            value: qualityBlocked,
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: CHART_COLORS.amber },
                  { offset: 1, color: CHART_COLORS.amberDeep },
                ],
              },
              borderRadius: [4, 4, 0, 0],
            },
          },
        ],
        label: {
          show: true,
          position: "top",
          color: CHART_COLORS.textSecondary,
          fontFamily: "var(--font-mono, monospace)",
          fontSize: 11,
          fontWeight: 700,
        },
      },
      {
        type: "pie",
        radius: ["36%", "58%"],
        center: ["78%", "48%"],
        itemStyle: {
          borderColor: "#080d1a",
          borderWidth: 2,
          borderRadius: 4,
        },
        label: {
          show: true,
          position: "center",
          formatter: `${yieldRate}%\n良品率`,
          fontSize: 14,
          fontWeight: 800,
          color: CHART_COLORS.green,
          lineHeight: 18,
          fontFamily: "var(--font-mono, monospace)",
        },
        data: [
          {
            value: qualified > 0 ? qualified : (defect === 0 ? 1 : 0),
            name: "合格量",
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 1,
                y2: 1,
                colorStops: [
                  { offset: 0, color: CHART_COLORS.green },
                  { offset: 1, color: CHART_COLORS.greenDeep },
                ],
              },
            },
          },
          {
            value: defect,
            name: "缺陷量",
            itemStyle: { color: "rgba(100, 116, 139, 0.4)" },
          },
        ],
      },
    ],
  };
}

const { updateChart } = useChart(chartRef, getOption);

watch(() => props.metrics, () => {
  updateChart();
}, { deep: true });
</script>

<style scoped>
.chart-container {
  width: 100%;
  height: 180px;
}
</style>
