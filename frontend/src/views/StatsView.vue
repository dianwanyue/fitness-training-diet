<script setup>
import { ref, computed } from "vue";
import { useData } from "../lib/useData.js";
import { today, dateRange, summarize, isBusinessDate } from "../lib/domain.js";
import TrendChart from "../components/TrendChart.vue";
const { data } = useData();
const days = ref(7),
  start = ref(dateRange(today(), 7)[0]),
  end = ref(today());
function range(n) {
  days.value = n;
  if (n) {
    end.value = today();
    start.value = dateRange(end.value, n)[0];
  }
}
const invalid = computed(
  () =>
    !isBusinessDate(start.value) ||
    !isBusinessDate(end.value) ||
    start.value > end.value ||
    (Date.parse(end.value) - Date.parse(start.value)) / 86400000 > 365,
);
const result = computed(() =>
  invalid.value ? null : summarize(data.value, start.value, end.value),
);
const seven = computed(() => {
  const date = isBusinessDate(end.value) ? end.value : today();
  return summarize(data.value, dateRange(date, 7)[0], date);
});
</script>
<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">LOOK BACK, MOVE FORWARD</p>
      <h1>看见自己的节奏。</h1>
      <p class="muted">只统计有效记录，留白也是记录的一部分。</p>
    </div>
    <div class="segmented">
      <button :aria-pressed="days === 7" @click="range(7)">7 天</button
      ><button :aria-pressed="days === 30" @click="range(30)">30 天</button
      ><button :aria-pressed="days === 0" @click="range(0)">自定义</button>
    </div>
  </div>
  <section class="card">
    <div class="form-grid">
      <label class="field"
        >开始日期<input v-model="start" type="date" @change="days = 0" /></label
      ><label class="field"
        >结束日期<input v-model="end" type="date" @change="days = 0"
      /></label>
    </div>
    <p class="caption">
      统计时区：Asia/Shanghai。支持最多 366 个自然日；缺失记录不补零。
    </p>
  </section>
  <p v-if="invalid" class="error" role="alert">
    请选择有效日期，开始日期不得晚于结束日期，区间最多 366 天。
  </p>
  <template v-if="result"
    ><div class="grid-3">
      <article class="card">
        <small>计划完成率</small>
        <div class="number">
          {{
            result.completion == null ? "—" : result.completion.toFixed(0) + "%"
          }}
        </div>
        <p class="caption">
          {{
            result.planned
              ? result.completed + " / " + result.planned + " 个未取消计划"
              : "暂无计划"
          }}
        </p>
      </article>
      <article class="card">
        <small>临时训练</small>
        <div class="number">
          {{ result.temporaryWorkouts }} <small>次</small>
        </div>
        <p class="caption">不计入计划完成率</p>
      </article>
      <article class="card">
        <small>区间体重均值</small>
        <div class="number">
          {{ result.weightAverage?.toFixed(1) ?? "—" }} <small>kg</small>
        </div>
        <p class="caption">有效 {{ result.weightDays }} 天</p>
      </article>
    </div>
    <div class="grid-2">
      <section class="card">
        <div class="section-heading">
          <h2>每日能量摄入</h2>
          <span class="badge">有效 {{ result.dietDays }} 天</span>
        </div>
        <TrendChart
          v-if="result.dietDays"
          :dates="result.dates"
          :values="result.energy"
          type="bar"
          label="能量摄入"
          unit="kcal"
        />
        <div v-else class="empty">区间内暂无饮食记录</div>
        <p class="caption">未记录日留空，未记录不代表摄入为零。</p>
      </section>
      <section class="card">
        <div class="section-heading">
          <h2>体重趋势</h2>
          <RouterLink to="/body" class="caption">记录体重 ↗</RouterLink>
        </div>
        <TrendChart
          v-if="result.weightDays"
          :dates="result.dates"
          :values="result.weights"
          label="体重趋势"
          unit="kg"
          :average="result.weightAverage"
        />
        <div v-else class="empty">区间内暂无体重记录</div>
        <p class="caption">
          截至 {{ end }} 的七日均值：{{
            seven.weightAverage?.toFixed(1) ?? "—"
          }}
          kg（有效 {{ seven.weightDays }} 天）；虚线为所选区间均值。
        </p>
      </section>
    </div>
    <section class="card">
      <h2>关于这些数字</h2>
      <p class="muted">
        完成率 = 已完成计划 ÷
        未取消计划。无计划不计算完成率；临时训练单独计数。营养为录入时的快照估算，均值只使用有效记录日。
      </p>
      <span class="badge"
        >本页是本地演示统计，后端联调时以服务端统一口径为准。</span
      >
    </section></template
  >
</template>
