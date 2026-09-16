<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
/**
 * 库存资产环形占比图 (InventoryChart)
 * 职责：展示可用库存与业务预留的比例，中心突出可用率百分比
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
  const onHand = parseMetricNumber(props.metrics?.on_hand_qty, 0);
  const available = parseMetricNumber(props.metrics?.available_qty, 0);
  const reserved = parseMetricNumber(props.metrics?.reserved_qty, 0);

  const total = onHand > 0 ? onHand : available + reserved;
  const rate = total > 0 ? Math.min(100, Math.round((available / total) * 100)) : (available > 0 ? 100 : 0);

  return {
    tooltip: {
      ...DEFAULT_TOOLTIP,
      trigger: "item",
      formatter: "{b}: {c} 件 ({d}%)",
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
          formatter: `${rate}%\n可用率`,
          fontSize: 20,
          fontWeight: 800,
          fontFamily: "var(--font-mono, monospace)",
          color: CHART_COLORS.accent,
          lineHeight: 26,
        },
        emphasis: {
          scaleSize: 6,
        },
        data: [
          {
            value: available,
            name: "可用库存",
            itemStyle: {
              color: {
                type: "linear",
                x: 0,
                y: 0,
                x2: 1,
                y2: 1,
                colorStops: [
                  { offset: 0, color: CHART_COLORS.accent },
                  { offset: 1, color: CHART_COLORS.accentDeep },
                ],
              },
            },
          },
          {
            value: reserved,
            name: "业务预留",
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
