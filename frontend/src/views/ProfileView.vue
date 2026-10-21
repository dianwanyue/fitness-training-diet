<script setup>
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { useData } from "../lib/useData.js";
import { today, isBusinessDate } from "../lib/domain.js";
const { data, user, busy, commit, logout } = useData(),
  router = useRouter();
const profile = reactive({ ...data.value.profile }),
  goal = reactive({
    effectiveDate: today(),
    kcal: 2100,
    protein: 120,
    carbs: 250,
    fat: 60,
  });
const error = ref(""),
  message = ref("");
async function saveProfile() {
  error.value = "";
  message.value = "";
  if (
    !profile.nickname?.trim() ||
    !Number.isFinite(Number(profile.height)) ||
    profile.height < 80 ||
    profile.height > 250 ||
    (profile.birthday &&
      (!isBusinessDate(profile.birthday) || profile.birthday > today()))
  ) {
    error.value = "请填写昵称、80–250 cm 身高及有效出生日期";
    return;
  }
  try {
    await commit((d) => {
      d.profile = { ...profile, nickname: profile.nickname.trim() };
    });
    message.value = "个人资料已保存";
  } catch (e) {
    error.value = e.message;
  }
}
async function saveGoal() {
  error.value = "";
  message.value = "";
  if (
    !isBusinessDate(goal.effectiveDate) ||
    ["kcal", "protein", "carbs", "fat"].some(
      (k) => !Number.isFinite(Number(goal[k])) || Number(goal[k]) <= 0,
    ) ||
    goal.kcal > 10000 ||
    goal.protein > 1000 ||
    goal.carbs > 2000 ||
    goal.fat > 1000
  ) {
    error.value = "请填写有效生效日期和正数目标，数值超出可用范围";
    return;
  }
  try {
    await commit((d) => {
      const old = d.goals.find((x) => x.effectiveDate === goal.effectiveDate);
      if (old) Object.assign(old, goal);
      else d.goals.push({ ...goal, id: crypto.randomUUID() });
    });
    message.value = "目标已保存；其他生效日期的历史保持不变";
  } catch (e) {
    error.value = e.message;
  }
}
async function exit() {
  try {
    logout();
    await router.replace("/");
  } catch (e) {
    error.value = e.message;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">YOUR OWN PACE</p>
      <h1>为自己，设定一个方向。</h1>
      <p class="muted">
        {{ user.username }} · 当前账户的数据仅保存在本机浏览器。
      </p>
    </div>
    <button class="button secondary" :disabled="busy" @click="exit">
      退出登录
    </button>
  </div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="message" class="success" role="status">{{ message }}</p>
  <div class="grid-2">
    <section class="card">
      <h2>个人资料</h2>
      <form class="stack" @submit.prevent="saveProfile">
        <label class="field"
          >昵称<input
            v-model="profile.nickname"
            required
            maxlength="32"
            autocomplete="nickname" /></label
        ><label class="field"
          >身高（cm）<input
            v-model.number="profile.height"
            type="number"
            min="80"
            max="250"
            step="0.1"
            required /></label
        ><label class="field"
          >出生日期（可选）<input
            v-model="profile.birthday"
            type="date"
            :max="today()" /></label
        ><button class="button" :disabled="busy">保存资料</button>
      </form>
    </section>
    <section class="card">
      <h2>每日营养目标</h2>
      <form class="stack" @submit.prevent="saveGoal">
        <label class="field"
          >生效日期<input v-model="goal.effectiveDate" type="date" required
        /></label>
        <div class="form-grid">
          <label class="field"
            >能量（kcal）<input
              v-model.number="goal.kcal"
              type="number"
              min="1"
              max="10000"
              step="1"
              required /></label
          ><label class="field"
            >蛋白质（g）<input
              v-model.number="goal.protein"
              type="number"
              min="0.1"
              max="1000"
              step="0.1"
              required /></label
          ><label class="field"
            >碳水化合物（g）<input
              v-model.number="goal.carbs"
              type="number"
              min="0.1"
              max="2000"
              step="0.1"
              required /></label
          ><label class="field"
            >脂肪（g）<input
              v-model.number="goal.fat"
              type="number"
              min="0.1"
              max="1000"
              step="0.1"
              required
          /></label>
        </div>
        <p class="caption">
          同一生效日期更新该目标，其他日期保留历史。示例数值并非个体化建议。
        </p>
        <button class="button" :disabled="busy">保存生效日期目标</button>
      </form>
    </section>
  </div>
  <section class="card">
    <h2>目标历史</h2>
    <div v-if="!data.goals.length" class="empty">暂无目标</div>
    <div
      v-for="g in [...data.goals].sort((a, b) =>
        b.effectiveDate.localeCompare(a.effectiveDate),
      )"
      :key="g.id"
      class="list-row"
    >
      <div>
        <b>{{ g.effectiveDate }} 起生效</b>
        <p class="caption">
          {{ g.kcal }} kcal · 蛋白质 {{ g.protein }} g · 碳水 {{ g.carbs }} g ·
          脂肪 {{ g.fat }} g
        </p>
      </div>
      <button class="button ghost" @click="Object.assign(goal, g)">编辑</button>
    </div>
  </section>
  <section class="card">
    <h2>隐私与版本说明</h2>
    <p class="muted">
      这是前端模拟版本，不是真实身份认证或安全存储。请勿输入真实口令或敏感健康数据；清除浏览器站点数据会删除本机演示账户及记录。退出登录不删除数据。
    </p>
    <p class="caption">
      当前未实现 AI 分析、后台管理与 Android
      打包；没有向模型发送数据，也没有收集 AI 授权。待后端接口确认后再联调。
    </p>
  </section>
</template>
