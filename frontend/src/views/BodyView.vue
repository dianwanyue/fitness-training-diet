<script setup>
import { ref, computed, watch } from "vue";
import { useData } from "../lib/useData.js";
import { today, dateRange, isBusinessDate } from "../lib/domain.js";
import TrendChart from "../components/TrendChart.vue";
const { data, busy, mode, loadRange, getStats, saveBody, deleteBody } = useData();
const loading = ref(false);
const summary = ref(null);
let loadVersion = 0;
const date = ref(today()),
  weight = ref(""),
  error = ref(""),
  message = ref(""),
  deleting = ref(null);
const existing = computed(() =>
  data.value.body.find((x) => x.date === date.value),
);
watch(
  existing,
  (v) => {
    weight.value = v?.weight ?? "";
  },
  { immediate: true },
);

const history = computed(() =>
  [...data.value.body].filter((record) => record.date >= dateRange(today(), 366)[0] && record.date <= today()).sort((a, b) => b.date.localeCompare(a.date)),
);
async function save() {
  if (busy.value || loading.value) return;
  error.value = "";
  message.value = "";
  const n = Number(weight.value);
  if (!isBusinessDate(date.value) || !Number.isFinite(n) || n < 20 || n > 300) {
    error.value = "请选择日期，体重范围为 20–300 kg";
    return;
  }
  try {
    await saveBody({ date: date.value, weight: n });
    await refreshSummary();
    message.value = "体重已保存，同一天仅保留一条记录";
  } catch (e) {
    error.value = e.message;
  }
}
async function remove(id) {
  error.value = "";
  message.value = "";
  try {
    await deleteBody(id);
    await refreshSummary();
    deleting.value = null;
    message.value = "体重记录已删除";
  } catch (e) {
    error.value = e.message;
  }
}
async function refreshSummary() {
  summary.value = await getStats(dateRange(today(), 30)[0], today());
}
watch(date, async (value) => {
  const version = ++loadVersion;
  if (!isBusinessDate(value)) { loading.value = false; return; }
  loading.value = true;
  error.value = "";
  try {
    await loadRange(dateRange(today(), 366)[0], today());
    if (value < dateRange(today(), 366)[0] || value > today()) await loadRange(value, value);
    await refreshSummary();
  } catch (cause) { if (version === loadVersion) error.value = cause.message || "体重记录加载失败，请重试。"; }
  finally { if (version === loadVersion) loading.value = false; }
}, { immediate: true });
</script>
<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">MORE THAN A NUMBER</p>
      <h1>关注变化，不被数字定义。</h1>
      <p class="muted">同一时间、相近状态下测量，更便于回顾。</p>
    </div>
    <RouterLink to="/stats" class="button secondary">查看统计 ↗</RouterLink>
  </div>
  <p class="caption">{{ mode === "demo" ? "本地演示记录与统计。" : "真实服务记录与统计。" }}缺失日期不补零。</p>
  <p v-if="loading" role="status">正在加载体重记录…</p>
  <div class="grid-2">
    <section class="card">
      <h2>记录体重</h2>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="message" class="success" role="status">{{ message }}</p>
      <form class="stack" @submit.prevent="save">
        <label class="field"
          >日期<input v-model="date" type="date" required /></label
        ><label class="field"
          >体重（kg）<input
            v-model.number="weight"
            type="number"
            inputmode="decimal"
            min="20"
            max="300"
            step="0.1"
            required
            placeholder="如 68.5"
        /></label>
        <p class="caption">
          {{
            existing
              ? "该日期已有记录，保存将更新原记录。"
              : "每个日期保存一条记录，支持补录。"
          }}
        </p>
        <button class="button" :disabled="busy || loading">
          {{ busy ? "保存中…" : existing ? "更新同日记录" : "保存体重" }}
        </button>
      </form>
    </section>
    <section v-if="summary" class="card">
      <div class="section-heading">
        <h2>最近 30 天</h2>
        <span class="badge">有效 {{ summary.weightDays }} 天</span>
      </div>
      <TrendChart
        v-if="summary.weightDays"
        :dates="summary.dates"
        :values="summary.weights"
        label="体重"
        unit="kg"
        :average="summary.weightAverage"
      />
      <div v-else class="empty">还没有体重记录。记录后在这里看见趋势。</div>
      <p class="caption">
        缺失日期断开显示，不补零、不连线。区间均值
        {{ summary.weightAverage?.toFixed(1) ?? "—" }} kg。
      </p>
    </section>
  </div>
  <section class="card">
    <h2>近 366 天历史记录</h2>
    <div v-if="!history.length" class="empty">暂无记录</div>
    <div v-for="r in history" :key="r.id" class="list-row">
      <div>
        <b>{{ r.date }}</b>
        <p>{{ r.weight }} kg</p>
      </div>
      <div v-if="deleting === r.id" class="row">
        <button class="button danger" :disabled="busy || loading" @click="remove(r.id)">
          确认删除</button
        ><button class="button ghost" @click="deleting = null">取消</button>
      </div>
      <div v-else class="row">
        <button
          class="button ghost"
          @click="
            date = r.date;
            weight = r.weight;
          "
        >
          编辑</button
        ><button class="button ghost" :disabled="busy || loading" @click="deleting = r.id">
          删除
        </button>
      </div>
    </div>
  </section>
</template>
