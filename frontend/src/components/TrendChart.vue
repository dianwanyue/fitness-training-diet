<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from "vue";
import { use, init } from "echarts/core";
import { LineChart, BarChart } from "echarts/charts";
import {
  GridComponent,
  TooltipComponent,
  MarkLineComponent,
  AriaComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
use([
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  MarkLineComponent,
  AriaComponent,
  CanvasRenderer,
]);
const props = defineProps({
  dates: { type: Array, required: true },
  values: { type: Array, required: true },
  type: { type: String, default: "line" },
  label: { type: String, required: true },
  unit: { type: String, default: "" },
  average: { type: Number, default: null },
});
const host = ref(null);
let chart, observer;
function draw() {
  if (!chart) return;
  chart.setOption(
    {
      aria: { enabled: true },
      animation: false,
      color: ["#26755e"],
      tooltip: {
        trigger: "axis",
        valueFormatter: (v) =>
          v == null ? "未记录" : `${Number(v).toFixed(1)} ${props.unit}`,
      },
      grid: { left: 48, right: 15, top: 24, bottom: 30 },
      xAxis: {
        type: "category",
        data: props.dates.map((x) => x.slice(5)),
        axisLine: { lineStyle: { color: "#d6dfd3" } },
        axisTick: { show: false },
        axisLabel: { color: "#7b8979", fontSize: 10 },
      },
      yAxis: {
        type: "value",
        scale: props.type === "line",
        axisLabel: { color: "#7b8979", fontSize: 10 },
        splitLine: { lineStyle: { color: "#eef2e9" } },
      },
      series: [
        {
          name: props.label,
          type: props.type,
          data: props.values,
          connectNulls: false,
          barMaxWidth: 26,
          itemStyle: { borderRadius: props.type === "bar" ? [4, 4, 0, 0] : 0 },
          symbolSize: 7,
          lineStyle: { width: 3 },
          areaStyle:
            props.type === "line"
              ? { color: "#dcead8", opacity: 0.3 }
              : undefined,
          markLine:
            props.average == null
              ? undefined
              : {
                  symbol: "none",
                  lineStyle: { color: "#a18d56", type: "dashed" },
                  label: { formatter: "均值", position: "insideEndTop" },
                  data: [{ yAxis: props.average }],
                },
        },
      ],
    },
    true,
  );
}
onMounted(() => {
  chart = init(host.value);
  observer = new ResizeObserver(() => chart.resize());
  observer.observe(host.value);
  draw();
});
watch(() => [props.dates, props.values, props.average], draw, { deep: true });
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div
    ref="host"
    class="chart"
    role="img"
    :aria-label="label + '，缺失日期不补零'"
  ></div>
  <details class="chart-table">
    <summary>查看{{ label }}数据表</summary>
    <table>
      <thead>
        <tr>
          <th>日期</th>
          <th>{{ label }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(date, i) in dates" :key="date">
          <td>{{ date }}</td>
          <td>
            {{
              values[i] == null
                ? "未记录"
                : Number(values[i]).toFixed(1) + " " + unit
            }}
          </td>
        </tr>
      </tbody>
    </table>
  </details>
</template>
<style scoped>
.chart {
  width: 100%;
  height: 220px;
  min-width: 0;
}
.chart-table {
  font-size: 11px;
  color: var(--muted);
  margin-top: 10px;
}
.chart-table summary {
  cursor: pointer;
  min-height: 44px;
  padding: 12px 0;
}
table {
  border-collapse: collapse;
  width: 100%;
}
th,
td {
  text-align: left;
  padding: 8px;
  border-bottom: 1px solid var(--line);
}
</style>
