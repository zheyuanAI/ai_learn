<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
/**
 * 异常告警生命周期图表 (AlarmChart)
 * 职责：展示触发中、已确认、恢复未确认、已闭环四段状态分布，突出活动告警严重程度
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
  const triggered = parseMetricNumber(props.metrics?.triggered_count, 0);
  const acked = parseMetricNumber(props.metrics?.acked_count, 0);
  const recoveredUnacked = parseMetricNumber(props.metrics?.recovered_unacked_count, 0);
  const recovered = parseMetricNumber(props.metrics?.recovered_count, 0);

  const totalAlarms = triggered + acked + recoveredUnacked + recovered;

  return {
    tooltip: {
      ...DEFAULT_TOOLTIP,
      trigger: "item",
      formatter: "{b}: {c} 起 ({d}%)",
    },
    legend: {
      bottom: 0,
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 12,
      textStyle: { color: CHART_COLORS.textDim, fontSize: 10 },
    },
    series: [
      {
        type: "pie",
        radius: ["46%", "72%"],
        center: ["50%", "44%"],
        itemStyle: {
          borderColor: "#080d1a",
          borderWidth: 3,
          borderRadius: 6,
        },
        label: {
          show: true,
          position: "center",
          formatter: triggered > 0 ? `${triggered}起\n活动` : (totalAlarms > 0 ? "全部\n已闭环" : "暂无\n告警"),
          fontSize: 16,
          fontWeight: 800,
          color: triggered > 0 ? CHART_COLORS.red : CHART_COLORS.green,
          lineHeight: 20,
          fontFamily: "var(--font-mono, monospace)",
        },
        data: [
          {
            value: triggered,
            name: "触发中",
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 1,
                y2: 1,
                colorStops: [
                  { offset: 0, color: "#ef4444" },
                  { offset: 1, color: "#dc2626" },
                ],
              },
            },
          },
          {
            value: acked,
            name: "已确认",
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 1,
                y2: 1,
                colorStops: [
                  { offset: 0, color: CHART_COLORS.amber },
                  { offset: 1, color: CHART_COLORS.amberDeep },
                ],
              },
            },
          },
          {
            value: recoveredUnacked,
            name: "恢复未确认",
            itemStyle: { color: CHART_COLORS.accent },
          },
          {
            value: recovered,
            name: "已闭环",
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
        ],
        animationType: "scale",
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
