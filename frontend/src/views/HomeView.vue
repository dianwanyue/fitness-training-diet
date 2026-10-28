<script setup>
import { ref, computed } from "vue";
import { useData } from "../lib/useData.js";
import {
  today,
  totals,
  goalAt,
  summarize,
  dateRange,
  isBusinessDate,
} from "../lib/domain.js";
const { data } = useData();
const date = ref(today());
const safeDate = computed(() =>
  isBusinessDate(date.value) ? date.value : today(),
);
const diets = computed(() =>
  data.value.diet.filter((x) => x.date === safeDate.value),
);
const intake = computed(() => totals(diets.value));
const goal = computed(() => goalAt(data.value.goals, safeDate.value));
const plans = computed(() =>
  data.value.plans.filter(
    (x) => x.date === safeDate.value && x.status !== "cancelled",
  ),
);
const weight = computed(
  () => data.value.body.find((x) => x.date === safeDate.value)?.weight,
);
const week = computed(() =>
  summarize(data.value, dateRange(safeDate.value, 7)[0], safeDate.value),
);
const meals = ["早餐", "午餐", "晚餐", "加餐"];
const fmt = (n) => Number(n).toFixed(0);
</script>
<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">YOUR DAILY BALANCE</p>
      <h1>{{ data.profile.nickname || "你好" }}，今天也照顾好自己。</h1>
      <p class="muted">每一次记录，都是更了解自己的开始。</p>
    </div>
    <label class="field"
      >查看日期<input v-model="date" type="date" required
    /></label>
  </div>
  <section class="hero card">
    <div>
      <span class="hero-tag">日常，比完美更重要</span>
      <h2>动有所获，食有所衡。</h2>
      <p>给今天留下一点记录，<br />让每一份坚持都看得见。</p>
      <RouterLink class="button" to="/training">开始今日训练 ↗</RouterLink>
    </div>
    <div class="hero-art" aria-hidden="true">
      <span>循</span><small>ONE DAY AT A TIME</small>
    </div>
  </section>
  <div class="grid-3 metrics">
    <article class="card">
      <span class="muted">已摄入能量</span>
      <p class="number">
        {{ diets.length ? fmt(intake.kcal) : "—" }} <small>kcal</small>
      </p>
      <small>目标 {{ goal?.kcal ?? "未设置" }}</small>
    </article>
    <article class="card">
      <span class="muted">训练计划</span>
      <p class="number">
        {{ plans.filter((x) => x.status === "completed").length
        }}<small> / {{ plans.length }}</small>
      </p>
      <small>{{ plans.length ? "已完成 / 未取消计划" : "暂无计划" }}</small>
    </article>
    <article class="card">
      <span class="muted">当日体重</span>
      <p class="number">{{ weight ?? "—" }} <small>kg</small></p>
      <RouterLink to="/body" class="caption"
        >{{ weight ? "查看记录" : "去记录体重" }} →</RouterLink
      >
    </article>
  </div>
  <div class="grid-2">
    <section class="card">
      <div class="section-heading">
        <h2>饮食与营养</h2>
        <RouterLink to="/diet" class="caption">记录饮食 ↗</RouterLink>
      </div>
      <div
        v-for="[key, label] in [
          ['protein', '蛋白质'],
          ['carbs', '碳水化合物'],
          ['fat', '脂肪'],
        ]"
        :key="key"
        class="macro"
      >
        <div class="section-heading">
          <span>{{ label }}</span
          ><small
            >{{ diets.length ? intake[key].toFixed(1) : "—" }} /
            {{ goal?.[key] ?? "—" }} g</small
          >
        </div>
        <progress
          :value="intake[key]"
          :max="goal?.[key] || 100"
          :aria-label="label + '目标进度'"
        ></progress>
      </div>
      <div v-for="meal in meals" :key="meal" class="list-row">
        <span>{{ meal }}</span
        ><span>{{
          diets.some((x) => x.meal === meal)
            ? fmt(totals(diets.filter((x) => x.meal === meal)).kcal) + " kcal"
            : "未记录"
        }}</span>
      </div>
    </section>
    <div>
      <section class="card">
        <div class="section-heading">
          <h2>当日训练</h2>
          <RouterLink to="/training" class="caption">管理 ↗</RouterLink>
        </div>
        <div v-if="!plans.length" class="empty">
          今天暂无计划。<br />安排一次适合自己的训练吧。
        </div>
        <div v-for="plan in plans" :key="plan.id" class="list-row">
          <div>
            <b>{{ plan.title }}</b>
            <p class="caption">{{ plan.items.length }} 个动作</p>
          </div>
          <span class="badge" :class="{ warm: plan.status !== 'completed' }">{{
            plan.status === "completed" ? "已完成" : "待执行"
          }}</span>
        </div>
      </section>
      <section class="card week-card">
        <p class="eyebrow">A LITTLE REFLECTION</p>
        <h2>回看这七天</h2>
        <p class="muted">
          体重均值 <b>{{ week.weightAverage?.toFixed(1) ?? "—" }} kg</b
          ><br />有效体重 {{ week.weightDays }} 天 · 饮食 {{ week.dietDays }} 天
        </p>
        <RouterLink to="/stats" class="caption">看看自己的节奏 →</RouterLink>
      </section>
    </div>
  </div>
</template>
<style scoped>
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #e7eddb;
  border-color: #dce5cc;
  padding: 30px 36px;
  overflow: hidden;
}
.hero-tag {
  font-size: 11px;
  color: #5c7550;
  letter-spacing: 2px;
}
.hero h2 {
  font-size: 27px;
  margin: 13px 0;
}
.hero p {
  font-size: 13px;
  color: #6c7b61;
}
.hero-art {
  width: 180px;
  height: 180px;
  border: 1px solid #c6d4b4;
  outline: 18px solid #e0e8d2;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  transform: rotate(-13deg);
  margin: 10px 30px;
}
.hero-art span {
  font-size: 80px;
  font-weight: 700;
  color: #77916b;
  line-height: 1.3;
}
.hero-art small {
  font-size: 8px;
  letter-spacing: 2px;
  color: #829273;
}
.metrics .card {
  padding: 20px;
}
.metrics .number {
  margin: 12px 0 5px;
}
.metrics .muted {
  font-size: 12px;
}
.macro {
  margin-bottom: 18px;
  font-size: 12px;
}
.macro .section-heading {
  margin-bottom: 7px;
}
.week-card {
  background: #eff3eb;
}
@media (max-width: 700px) {
  .hero {
    padding: 23px;
  }
  .hero h2 {
    font-size: 22px;
  }
  .hero-art {
    display: none;
  }
  .metrics .card {
    padding: 14px 10px;
  }
  .metrics .muted {
    font-size: 10px;
  }
  .metrics small {
    font-size: 10px;
  }
  .metrics .number {
    font-size: 23px;
  }
}
</style>
