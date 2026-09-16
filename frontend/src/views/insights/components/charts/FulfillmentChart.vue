<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
/**
 * 履约时效水平管道柱状图 (FulfillmentChart)
 * 职责：展示从需求总量、已收货、已预留、已拣货到已发货的漏斗流转与递减情况
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
  const ordered = parseMetricNumber(props.metrics?.ordered_qty, 0);
  const received = parseMetricNumber(props.metrics?.received_qty, 0);
  const reserved = parseMetricNumber(props.metrics?.reserved_qty, 0);
  const picked = parseMetricNumber(props.metrics?.picked_qty, 0);
  const shipped = parseMetricNumber(props.metrics?.shipped_qty, 0);

  const rawData = [
    { name: "需求总量", value: ordered },
    { name: "已收货", value: received },
    { name: "已预留", value: reserved },
    { name: "已拣货", value: picked },
    { name: "已发货", value: shipped },
  ];

  const maxVal = Math.max(ordered, received, reserved, picked, shipped, 10);

  return {
    tooltip: {
      ...DEFAULT_TOOLTIP,
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: any) => {
        const item = params?.[0];
        if (!item) return "";
        return `${item.name}: ${item.value} 件`;
      },
    },
    grid: {
      left: 64,
      right: 48,
      top: 10,
      bottom: 6,
      containLabel: false,
    },
    xAxis: {
      type: "value",
      show: false,
      max: Math.ceil(maxVal * 1.15),
    },
    yAxis: {
      type: "category",
      inverse: true,
      data: rawData.map((d) => d.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: CHART_COLORS.textSecondary,
        fontSize: 11,
      },
    },
    series: [
      {
        type: "bar",
        barWidth: 16,
        data: rawData.map((d) => d.value),
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: CHART_COLORS.accentDeep },
              { offset: 1, color: CHART_COLORS.accent },
            ],
          },
        },
        label: {
          show: true,
          position: "right",
          color: CHART_COLORS.textSecondary,
          fontSize: 11,
          fontFamily: "var(--font-mono, monospace)",
          fontWeight: 600,
        },
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
