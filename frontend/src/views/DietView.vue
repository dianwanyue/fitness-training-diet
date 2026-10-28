<script setup>
import { ref, computed, watch } from "vue";
import { useData } from "../lib/useData.js";
import { foods } from "../lib/repository.js";
import { today, nutrition, totals, isBusinessDate } from "../lib/domain.js";
const { data, busy, commit } = useData();
const date = ref(today()),
  meal = ref("早餐"),
  query = ref(""),
  foodId = ref(foods[0]?.id || ""),
  grams = ref(100);
const message = ref(""),
  error = ref(""),
  editing = ref(null),
  deleting = ref(null);
const meals = ["早餐", "午餐", "晚餐", "加餐"];
const matches = computed(() =>
  foods.filter((x) => x.name.includes(query.value.trim())),
);
watch(matches, (list) => {
  if (!editing.value && !list.some((x) => x.id === foodId.value))
    foodId.value = list[0]?.id || "";
});
const selected = computed(() => foods.find((x) => x.id === foodId.value));
const records = computed(() =>
  data.value.diet.filter((x) => x.date === date.value && x.meal === meal.value),
);
const dayTotal = computed(() =>
  totals(data.value.diet.filter((x) => x.date === date.value)),
);
const preview = computed(() => {
  if (editing.value)
    return Object.fromEntries(
      Object.entries(editing.value.snapshot).map(([k, v]) => [
        k,
        (v * Number(grams.value)) / editing.value.grams,
      ]),
    );
  const amount = Number(grams.value);
  return selected.value
    ? nutrition(
        selected.value,
        Number.isFinite(amount) && amount >= 0 ? amount : 0,
      )
    : { kcal: 0, protein: 0, carbs: 0, fat: 0 };
});
function reset() {
  editing.value = null;
  grams.value = 100;
  error.value = "";
}
function edit(record) {
  editing.value = JSON.parse(JSON.stringify(record));
  grams.value = record.grams;
  foodId.value = record.foodId;
  message.value = "";
  error.value = "";
}
async function save() {
  error.value = "";
  message.value = "";
  if (
    !isBusinessDate(date.value) ||
    !Number.isFinite(Number(grams.value)) ||
    Number(grams.value) <= 0 ||
    Number(grams.value) > 10000 ||
    (!editing.value && !selected.value)
  ) {
    error.value = "请选择食物，克重须大于 0 且不超过 10000 g";
    return;
  }
  try {
    const old = editing.value;
    const record = old
      ? { ...old, grams: Number(grams.value), snapshot: { ...preview.value } }
      : {
          id: crypto.randomUUID(),
          date: date.value,
          meal: meal.value,
          foodId: selected.value.id,
          foodName: selected.value.name,
          grams: Number(grams.value),
          snapshot: { ...preview.value },
          source: selected.value.source,
          state: selected.value.state,
        };
    await commit((d) => {
      if (old) {
        const index = d.diet.findIndex((x) => x.id === old.id);
        if (index < 0) throw new Error("记录已不存在");
        d.diet[index] = record;
      } else d.diet.push(record);
    });
    reset();
    message.value = "饮食记录已保存，汇总已更新";
  } catch (e) {
    error.value = e.message;
  }
}
async function remove(id) {
  error.value = "";
  message.value = "";
  try {
    await commit((d) => {
      d.diet = d.diet.filter((x) => x.id !== id);
    });
    if (editing.value?.id === id) reset();
    deleting.value = null;
    message.value = "记录已删除";
  } catch (e) {
    error.value = e.message;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">NOURISH YOUR DAY</p>
      <h1>好好吃饭，也好好记录。</h1>
      <p class="muted">按实际克重记录，营养只是估算，不必追求完美。</p>
    </div>
    <label class="field"
      >记录日期<input v-model="date" type="date" required @change="reset()"
    /></label>
  </div>
  <div class="grid-3">
    <div class="card">
      <small>当日能量</small>
      <div class="number">
        {{ dayTotal.kcal.toFixed(0) }} <small>kcal</small>
      </div>
    </div>
    <div class="card">
      <small>蛋白质 / 碳水</small>
      <div class="number nutrients">
        {{ dayTotal.protein.toFixed(1) }} / {{ dayTotal.carbs.toFixed(1) }}
        <small>g</small>
      </div>
    </div>
    <div class="card">
      <small>脂肪</small>
      <div class="number">{{ dayTotal.fat.toFixed(1) }} <small>g</small></div>
    </div>
  </div>
  <div class="segmented meals" aria-label="餐次">
    <button
      v-for="m in meals"
      :key="m"
      :aria-pressed="meal === m"
      @click="
        meal = m;
        reset();
      "
    >
      {{ m }}
    </button>
  </div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="message" class="success" role="status">{{ message }}</p>
  <div class="grid-2">
    <section class="card">
      <h2>{{ editing ? "修改摄入克重" : "添加一份食物" }}</h2>
      <form class="stack" @submit.prevent="save">
        <template v-if="!editing"
          ><label class="field"
            >搜索演示食物<input
              v-model="query"
              type="search"
              placeholder="如：米饭、鸡胸肉" /></label
          ><label class="field"
            >选择食物<select v-model="foodId" required>
              <option v-for="f in matches" :key="f.id" :value="f.id">
                {{ f.name }} · {{ f.state }}
              </option>
            </select></label
          >
          <p v-if="!matches.length" class="empty">
            没有找到食物，请尝试其他关键词。
          </p></template
        >
        <div v-if="selected || editing" class="food-info">
          <b
            >{{ editing?.foodName || selected?.name }} ·
            {{ editing?.state || selected?.state }}</b
          >
          <p class="caption">来源：{{ editing?.source || selected?.source }}</p>
          <p v-if="!editing" class="caption">
            每 100 g：{{ selected.kcal }} kcal · 蛋白质 {{ selected.protein }} g
            · 碳水 {{ selected.carbs }} g · 脂肪 {{ selected.fat }} g
          </p>
          <p v-else class="caption">
            按原记录营养快照等比例更新，不使用变更后的食物库数据。
          </p>
        </div>
        <label class="field"
          >实际摄入（g）<input
            v-model.number="grams"
            type="number"
            min="0.1"
            max="10000"
            step="0.1"
            required
            inputmode="decimal"
        /></label>
        <div class="preview">
          <b>本项计入 {{ preview.kcal.toFixed(0) }} kcal</b>
          <p class="caption">
            蛋白质 {{ preview.protein.toFixed(1) }} g · 碳水
            {{ preview.carbs.toFixed(1) }} g · 脂肪
            {{ preview.fat.toFixed(1) }} g
          </p>
        </div>
        <div class="actions">
          <button class="button" :disabled="busy">
            {{
              busy ? "保存中…" : editing ? "保存修改" : "添加到" + meal
            }}</button
          ><button
            v-if="editing"
            type="button"
            class="button ghost"
            @click="reset"
          >
            取消编辑
          </button>
        </div>
      </form>
    </section>
    <section class="card">
      <div class="section-heading">
        <h2>{{ meal }}记录</h2>
        <span class="badge">{{ records.length }} 项</span>
      </div>
      <div v-if="!records.length" class="empty">
        这一餐还没有记录。<br />从一份食物开始吧。
      </div>
      <article v-for="r in records" :key="r.id" class="record">
        <div class="list-row">
          <div>
            <b>{{ r.foodName }}</b>
            <p class="caption">
              {{ r.state }} · {{ r.grams }} g ·
              {{ r.snapshot.kcal.toFixed(0) }} kcal
            </p>
            <small>{{ r.source }}</small>
          </div>
          <span class="badge">{{ r.meal }}</span>
        </div>
        <div v-if="deleting === r.id" class="actions">
          <span class="caption">确定删除这一项？</span
          ><button class="button danger" :disabled="busy" @click="remove(r.id)">
            确认删除</button
          ><button class="button ghost" @click="deleting = null">保留</button>
        </div>
        <div v-else class="actions">
          <button class="button ghost" :disabled="busy" @click="edit(r)">
            修改克重</button
          ><button
            class="button ghost"
            :disabled="busy"
            @click="deleting = r.id"
          >
            删除
          </button>
        </div>
      </article>
      <p class="caption rule">
        保存的是录入时的营养快照。食物数据均为模拟示例，请勿用于真实营养决策。
      </p>
    </section>
  </div>
</template>
<style scoped>
.meals {
  margin-bottom: 22px;
}
.food-info,
.preview {
  padding: 16px;
  background: #f3f6ee;
  border-radius: 10px;
  font-size: 13px;
}
.food-info p,
.preview p {
  margin: 8px 0 0;
}
.record {
  border-bottom: 1px solid var(--line);
  padding-bottom: 12px;
  margin-bottom: 14px;
}
.record .actions {
  margin-top: 2px;
}
.rule {
  margin-top: 20px;
}
.nutrients {
  font-size: 23px;
}
@media (max-width: 700px) {
  .nutrients {
    font-size: 16px;
  }
  .grid-3 .card {
    padding: 12px 9px;
  }
  .number {
    font-size: 21px;
  }
}
</style>
