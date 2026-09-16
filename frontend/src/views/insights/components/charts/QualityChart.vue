<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
/**
 * 质量合格仪表盘图表 (QualityChart)
 * 职责：半圆仪表盘直观反映综合检验合格率与质量健康区间
 */

import { ref, watch } from "vue";
import type { EChartsOption } from "echarts";
import { useChart } from "./useChart";
import { CHART_COLORS } from "./theme";
import { parseMetricNumber } from "../../utils/metric-dictionary";

const props = defineProps<{
  metrics?: Record<string, unknown>;
}>();

const chartRef = ref<HTMLElement | null>(null);

function getOption(): EChartsOption {
  const qualified = parseMetricNumber(props.metrics?.qualified_qty, 0);
  const unqualified = parseMetricNumber(props.metrics?.unqualified_qty, 0);
  const inspected = parseMetricNumber(props.metrics?.inspected_qty, qualified + unqualified);

  const passedCount = parseMetricNumber(props.metrics?.passed_count, 0);
  const failedCount = parseMetricNumber(props.metrics?.failed_count, 0);
  const inspectCount = parseMetricNumber(props.metrics?.inspection_count, passedCount + failedCount);

  let passRate = 100;
  if (inspected > 0) {
    passRate = parseFloat(((qualified / inspected) * 100).toFixed(1));
  } else if (inspectCount > 0) {
    passRate = parseFloat(((passedCount / inspectCount) * 100).toFixed(1));
  } else if (props.metrics?.pass_rate !== undefined) {
    passRate = parseMetricNumber(props.metrics.pass_rate, 100);
  }

  return {
    series: [
      {
        type: "gauge",
        startAngle: 200,
        endAngle: -20,
        radius: "96%",
        center: ["50%", "65%"],
        min: 0,
        max: 100,
        axisLine: {
          lineStyle: {
            width: 14,
            color: [
              [0.6, CHART_COLORS.red],
              [0.85, CHART_COLORS.amber],
              [1, CHART_COLORS.green],
            ],
          },
        },
        axisTick: { length: 4, lineStyle: { color: "rgba(148,163,184,0.3)", width: 1 } },
        splitLine: { length: 8, lineStyle: { color: "rgba(148,163,184,0.4)", width: 2 } },
        axisLabel: { distance: 14, color: CHART_COLORS.textDim, fontSize: 9 },
        pointer: {
          length: "60%",
          width: 4,
          itemStyle: {
            color: CHART_COLORS.textPrimary,
            shadowBlur: 8,
            shadowColor: "rgba(56,189,248,0.4)",
          },
        },
        anchor: {
          show: true,
          size: 8,
          itemStyle: { color: CHART_COLORS.accent, borderWidth: 2, borderColor: CHART_COLORS.textPrimary },
        },
        detail: {
          valueAnimation: true,
          fontSize: 22,
          fontWeight: 800,
          fontFamily: "var(--font-mono, monospace)",
          color: passRate >= 85 ? CHART_COLORS.green : (passRate >= 60 ? CHART_COLORS.amber : CHART_COLORS.red),
          formatter: "{value}%",
          offsetCenter: [0, "30%"],
        },
        data: [{ value: passRate }],
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
