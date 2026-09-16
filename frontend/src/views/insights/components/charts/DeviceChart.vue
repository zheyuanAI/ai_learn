<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
/**
 * 设备健康状态环形图 (DeviceChart)
 * 职责：展示在线设备与离线设备的占比，中心显示在线/总数及健康状态
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
  const online = parseMetricNumber(props.metrics?.online_device_count, 0);
  const offline = parseMetricNumber(props.metrics?.offline_device_count, 0);
  const total = parseMetricNumber(props.metrics?.device_count, online + offline);

  return {
    tooltip: {
      ...DEFAULT_TOOLTIP,
      trigger: "item",
      formatter: "{b}: {c} 台 ({d}%)",
    },
    series: [
      {
        type: "pie",
        radius: ["52%", "78%"],
        center: ["50%", "50%"],
        itemStyle: {
          borderColor: "#080d1a",
          borderWidth: 3,
          borderRadius: 6,
        },
        label: {
          show: true,
          position: "center",
          formatter: `${online}/${total}\n在线`,
          fontSize: 20,
          fontWeight: 800,
          color: online === total && total > 0 ? CHART_COLORS.green : (online > 0 ? CHART_COLORS.accent : CHART_COLORS.red),
          lineHeight: 26,
          fontFamily: "var(--font-mono, monospace)",
        },
        data: [
          {
            value: online,
            name: "在线设备",
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
            value: offline,
            name: "离线设备",
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 1,
                y2: 1,
                colorStops: [
                  { offset: 0, color: CHART_COLORS.red },
                  { offset: 1, color: CHART_COLORS.redDeep },
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
