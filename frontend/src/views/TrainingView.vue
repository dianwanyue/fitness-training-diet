<script setup>
import { computed, nextTick, ref, watch } from "vue";
import { useData } from "../lib/useData.js";
import { today } from "../lib/domain.js";
import { exercises } from "../lib/repository.js";

const { data, user, busy, error, commit } = useData();
const selectedDate = ref(today());
const localError = ref("");
const success = ref("");
const planEditorOpen = ref(false);
const recordEditorOpen = ref(false);
const editingPlanId = ref(null);
const planTitle = ref("");
const planItems = ref([]);
const recordPlanId = ref("");
const recordItems = ref([]);
const completeWithRecord = ref(false);
const cancelId = ref(null);
const completeId = ref(null);
const historyScope = ref("day");
const saving = ref(false);
const planForm = ref(null);
const recordForm = ref(null);
const locked = computed(() => busy.value || saving.value);
const plans = computed(() => data.value?.plans ?? []);
const workouts = computed(() => data.value?.workouts ?? []);
const dayPlans = computed(() =>
  plans.value.filter((plan) => plan.date === selectedDate.value),
);
const linkablePlans = computed(() =>
  dayPlans.value.filter((plan) => plan.status !== "cancelled"),
);
const linkedPlan = computed(() =>
  linkablePlans.value.find((plan) => plan.id === recordPlanId.value),
);
const canCompleteWithRecord = computed(
  () => linkedPlan.value?.status === "pending",
);
const history = computed(() =>
  workouts.value
    .filter(
      (workout) =>
        historyScope.value === "all" || workout.date === selectedDate.value,
    )
    .slice()
    .sort((a, b) => b.date.localeCompare(a.date)),
);
const dayWorkoutCount = computed(
  () =>
    workouts.value.filter((workout) => workout.date === selectedDate.value)
      .length,
);
const visibleError = computed(
  () =>
    localError.value ||
    (error.value ? String(error.value.message || error.value) : ""),
);
const statusLabels = {
  pending: "待完成",
  completed: "已完成",
  cancelled: "已取消",
};

const key = () => crypto.randomUUID();
const newPlanItem = () => ({
  key: key(),
  exerciseId: exercises[0]?.id ?? "",
  sets: 3,
  reps: 10,
  weight: 0,
});
const newSet = () => ({ key: key(), weight: "", reps: "" });
const newRecordItem = (exerciseId = exercises[0]?.id ?? "") => ({
  key: key(),
  exerciseId,
  sets: [newSet()],
});
const exerciseName = (id) =>
  exercises.find((exercise) => exercise.id === id)?.name || "未知动作";
const exerciseLabel = (exercise) => `${exercise.name} · ${exercise.muscle}`;
const planForWorkout = (workout) =>
  plans.value.find((plan) => plan.id === workout.planId);
const resetMessages = () => {
  localError.value = "";
  success.value = "";
};

function validDate(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value))
    return false;
  const date = new Date(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
  );
}

function requireDate() {
  if (!validDate(selectedDate.value)) throw new Error("请选择有效的训练日期。");
  return selectedDate.value;
}

function numberValue(value, label, integer = false) {
  if (
    value === null ||
    value === undefined ||
    typeof value === "boolean" ||
    (typeof value === "string" && !value.trim())
  ) {
    throw new Error(`请填写${label}。`);
  }
  const number = Number(value);
  if (
    !Number.isFinite(number) ||
    (integer ? !Number.isInteger(number) || number <= 0 : number < 0)
  ) {
    throw new Error(
      integer
        ? `${label}必须是有限的正整数。`
        : `${label}必须是大于或等于 0 的有限数。`,
    );
  }
  return number;
}

function requireExercise(exerciseId) {
  if (!exercises.some((exercise) => exercise.id === exerciseId))
    throw new Error("请选择有效的训练动作。");
  return exerciseId;
}

async function saveChange(mutator, message, onSuccess = () => {}) {
  if (locked.value || !user.value) return;
  resetMessages();
  saving.value = true;
  try {
    await commit(mutator);
    onSuccess();
    success.value = message;
  } catch (cause) {
    localError.value = cause?.message
      ? String(cause.message)
      : "保存失败，请重试；填写内容已保留。";
  } finally {
    saving.value = false;
  }
}

function openPlan(plan = null) {
  if (locked.value) return;
  resetMessages();
  if (!validDate(selectedDate.value)) {
    localError.value = "请先选择有效的训练日期。";
    return;
  }
  if (plan && plan.status !== "pending") {
    localError.value = "仅待完成计划可以编辑。";
    return;
  }
  editingPlanId.value = plan?.id ?? null;
  planTitle.value = plan?.title ?? "";
  planItems.value = plan
    ? plan.items.map((item) => ({ ...item, key: key() }))
    : [newPlanItem()];
  planEditorOpen.value = true;
  nextTick(() =>
    planForm.value?.scrollIntoView({ behavior: "smooth", block: "start" }),
  );
  cancelId.value = null;
  completeId.value = null;
}

async function savePlan() {
  if (locked.value) return;
  let date, title, items;
  try {
    date = requireDate();
    title = planTitle.value.trim();
    if (!title || title.length > 80)
      throw new Error("计划名称须为 1–80 个字符。");
    if (!planItems.value.length) throw new Error("计划至少需要一个动作。");
    items = planItems.value.map((item, index) => ({
      exerciseId: requireExercise(item.exerciseId),
      sets: numberValue(item.sets, `动作 ${index + 1} 的目标组数`, true),
      reps: numberValue(item.reps, `动作 ${index + 1} 的每组次数`, true),
      weight: numberValue(item.weight, `动作 ${index + 1} 的目标重量`),
    }));
  } catch (cause) {
    localError.value = cause.message;
    return;
  }
  const id = editingPlanId.value;
  await saveChange(
    (draft) => {
      if (id) {
        const plan = draft.plans.find((entry) => entry.id === id);
        if (!plan || plan.status !== "pending" || plan.date !== date)
          throw new Error("计划已变更，请关闭编辑后重试。");
        plan.title = title;
        plan.items = items;
        // Editing a target never rewrites the actual workout history.
      } else {
        draft.plans.push({ id: key(), date, title, status: "pending", items });
      }
    },
    id ? "计划已更新，历史训练记录保持不变。" : "训练计划已创建。",
    () => {
      planEditorOpen.value = false;
    },
  );
}

async function cancelPlan(id) {
  await saveChange(
    (draft) => {
      const plan = draft.plans.find((entry) => entry.id === id);
      if (!plan || plan.status !== "pending")
        throw new Error("计划已不是待完成状态，不能重复取消。");
      plan.status = "cancelled";
    },
    "计划已取消，已有训练记录不会删除。",
    () => {
      cancelId.value = null;
      if (editingPlanId.value === id) planEditorOpen.value = false;
      if (recordPlanId.value === id) {
        recordPlanId.value = "";
        completeWithRecord.value = false;
      }
    },
  );
}

async function markComplete(id) {
  await saveChange(
    (draft) => {
      const plan = draft.plans.find((entry) => entry.id === id);
      if (!plan || plan.status !== "pending")
        throw new Error("仅待完成计划可以打卡，不能重复完成。");
      plan.status = "completed";
    },
    "计划已标记完成；没有生成实际训练记录。",
    () => {
      completeId.value = null;
    },
  );
}

function fillRecordFromPlan() {
  const plan = linkedPlan.value;
  // Only copy exercise identities. Targets are not evidence of actual performance.
  recordItems.value = plan
    ? plan.items.map((item) => newRecordItem(item.exerciseId))
    : [newRecordItem()];
  if (!recordItems.value.length) recordItems.value = [newRecordItem()];
  completeWithRecord.value = false;
}

function openRecord(plan = null, finish = false) {
  if (locked.value) return;
  resetMessages();
  if (!validDate(selectedDate.value)) {
    localError.value = "请先选择有效的训练日期。";
    return;
  }
  recordPlanId.value = plan?.id ?? "";
  fillRecordFromPlan();
  completeWithRecord.value = finish && plan?.status === "pending";
  recordEditorOpen.value = true;
  nextTick(() =>
    recordForm.value?.scrollIntoView({ behavior: "smooth", block: "start" }),
  );
  completeId.value = null;
  cancelId.value = null;
}

async function saveWorkout() {
  if (locked.value) return;
  let date, items;
  try {
    date = requireDate();
    if (!recordItems.value.length)
      throw new Error("实际训练至少需要一个动作。");
    items = recordItems.value.map((item, itemIndex) => {
      if (!item.sets.length)
        throw new Error(`动作 ${itemIndex + 1} 至少需要一组实际记录。`);
      return {
        exerciseId: requireExercise(item.exerciseId),
        sets: item.sets.map((set, setIndex) => ({
          weight: numberValue(
            set.weight,
            `动作 ${itemIndex + 1} 第 ${setIndex + 1} 组重量`,
          ),
          reps: numberValue(
            set.reps,
            `动作 ${itemIndex + 1} 第 ${setIndex + 1} 组次数`,
            true,
          ),
        })),
      };
    });
  } catch (cause) {
    localError.value = cause.message;
    return;
  }
  const planId = recordPlanId.value || null;
  const finish = completeWithRecord.value;
  await saveChange(
    (draft) => {
      const plan = planId
        ? draft.plans.find((entry) => entry.id === planId)
        : null;
      if (
        planId &&
        (!plan || plan.date !== date || plan.status === "cancelled")
      ) {
        throw new Error("关联计划已取消或不属于当前日期，请重新选择。");
      }
      if (finish && (!plan || plan.status !== "pending"))
        throw new Error(
          "关联计划已完成或不可用，请取消“同时完成计划”后再保存。",
        );
      draft.workouts.push({ id: key(), date, planId, items });
      if (finish) plan.status = "completed";
    },
    finish
      ? "实际记录已保存，关联计划已完成。"
      : "实际记录已保存；没有更改计划状态。",
    () => {
      recordEditorOpen.value = false;
      recordItems.value = [];
      completeWithRecord.value = false;
    },
  );
}

watch(selectedDate, () => {
  planEditorOpen.value = false;
  recordEditorOpen.value = false;
  cancelId.value = null;
  completeId.value = null;
  resetMessages();
});
watch(
  () => user.value?.username,
  () => {
    planEditorOpen.value = false;
    recordEditorOpen.value = false;
    cancelId.value = null;
    completeId.value = null;
    resetMessages();
  },
);
watch(canCompleteWithRecord, (possible) => {
  if (!possible) completeWithRecord.value = false;
});
</script>

<template>
  <section class="training-page stack" aria-labelledby="training-heading">
    <header class="page-heading training-heading">
      <div>
        <p class="eyebrow">TRAINING JOURNAL · 训练日记</p>
        <h1 id="training-heading">一点进步，也值得记录。</h1>
        <p class="muted">先安排目标，再记下真实完成的每一组。</p>
      </div>
      <span class="training-mark" aria-hidden="true">↗</span>
    </header>

    <p v-if="visibleError" class="error" role="alert">{{ visibleError }}</p>
    <p v-if="success" class="success" role="status">{{ success }}</p>
    <p v-if="!user" class="empty">请先登录演示账户，再记录你的训练日记。</p>

    <template v-else>
      <section class="card date-card" aria-label="训练日期">
        <label class="field" for="training-date">
          <span>选择训练日期</span>
          <input
            id="training-date"
            v-model="selectedDate"
            type="date"
            required
            :disabled="locked"
          />
        </label>
        <div class="day-summary">
          <span
            ><strong class="number">{{ dayPlans.length }}</strong> 个计划</span
          >
          <span
            ><strong class="number">{{ dayWorkoutCount }}</strong>
            次实际记录</span
          >
          <button
            class="button ghost"
            type="button"
            :disabled="locked"
            @click="selectedDate = today()"
          >
            回到今天
          </button>
        </div>
      </section>

      <section class="stack" aria-labelledby="plans-heading">
        <div class="section-heading row section-toolbar">
          <div>
            <p class="eyebrow">01 / YOUR PLAN</p>
            <h2 id="plans-heading">当天训练计划</h2>
          </div>
          <button
            class="button"
            type="button"
            :disabled="locked || !selectedDate"
            @click="openPlan()"
          >
            ＋ 新建计划
          </button>
        </div>
        <p v-if="!dayPlans.length" class="card empty">
          今天还没有计划。给自己安排一场刚刚好的训练吧。
        </p>

        <article
          v-for="plan in dayPlans"
          :key="plan.id"
          class="card stack plan-card"
        >
          <div class="row section-toolbar">
            <h3>{{ plan.title }}</h3>
            <span class="badge" :class="`status-${plan.status}`">{{
              statusLabels[plan.status] || plan.status
            }}</span>
          </div>
          <p class="muted plan-caption">训练目标 · 以下不是实际训练记录</p>
          <ul class="exercise-list">
            <li
              v-for="(item, index) in plan.items"
              :key="`${plan.id}-${index}`"
              class="list-row target-row"
            >
              <span>{{ exerciseName(item.exerciseId) }}</span>
              <span class="number"
                >{{ item.sets }} 组 × {{ item.reps }} 次 ·
                {{ item.weight }} kg</span
              >
            </li>
          </ul>
          <div v-if="plan.status === 'pending'" class="actions">
            <button
              class="button"
              type="button"
              :disabled="locked"
              @click="openRecord(plan, true)"
            >
              记录并完成
            </button>
            <button
              class="button secondary"
              type="button"
              :disabled="locked"
              @click="
                completeId = plan.id;
                cancelId = null;
              "
            >
              仅标记完成
            </button>
            <button
              class="button ghost"
              type="button"
              :disabled="locked"
              @click="openPlan(plan)"
            >
              编辑
            </button>
            <button
              class="button danger"
              type="button"
              :disabled="locked"
              @click="
                cancelId = plan.id;
                completeId = null;
              "
            >
              取消计划
            </button>
          </div>
          <p v-else class="muted">
            {{
              plan.status === "completed"
                ? "已完成，不再重复打卡。实际表现请查看训练记录；需要时仍可追加记录。"
                : "已取消，不可打卡。之前的实际训练记录仍然保留。"
            }}
          </p>

          <div
            v-if="cancelId === plan.id"
            class="confirmation stack"
            role="group"
            :aria-label="`确认取消${plan.title}`"
          >
            <p>
              确定取消“{{
                plan.title
              }}”吗？取消后不能打卡，但不会删除已有训练记录。
            </p>
            <div class="actions">
              <button
                class="button danger"
                type="button"
                :disabled="locked"
                @click="cancelPlan(plan.id)"
              >
                确认取消计划
              </button>
              <button
                class="button ghost"
                type="button"
                :disabled="locked"
                @click="cancelId = null"
              >
                保留计划
              </button>
            </div>
          </div>
          <div
            v-if="completeId === plan.id"
            class="confirmation stack"
            role="group"
            :aria-label="`确认完成${plan.title}`"
          >
            <p>
              仅将此计划标记为完成，不会生成实际组数、次数或重量，也不会计入实际训练量。
            </p>
            <div class="actions">
              <button
                class="button secondary"
                type="button"
                :disabled="locked"
                @click="markComplete(plan.id)"
              >
                确认仅完成计划
              </button>
              <button
                class="button"
                type="button"
                :disabled="locked"
                @click="openRecord(plan, true)"
              >
                改为记录并完成
              </button>
              <button
                class="button ghost"
                type="button"
                :disabled="locked"
                @click="completeId = null"
              >
                返回
              </button>
            </div>
          </div>
        </article>

        <form
          v-if="planEditorOpen"
          ref="planForm"
          class="card stack editor-card"
          @submit.prevent="savePlan"
        >
          <div class="section-heading">
            <h3>{{ editingPlanId ? "编辑训练目标" : "安排新计划" }}</h3>
            <p class="muted">
              日期：{{ selectedDate }} · 编辑不会改动历史记录。
            </p>
          </div>
          <fieldset class="editor-fields stack" :disabled="locked">
            <label class="field" for="plan-title"
              ><span>计划名称</span
              ><input
                id="plan-title"
                v-model="planTitle"
                required
                maxlength="80"
                placeholder="例如：下肢力量与核心"
            /></label>
            <div
              v-for="(item, index) in planItems"
              :key="item.key"
              class="exercise-editor stack"
            >
              <div class="row section-toolbar">
                <h4>动作 {{ index + 1 }}</h4>
                <button
                  class="button ghost"
                  type="button"
                  :disabled="planItems.length === 1"
                  :aria-label="`删除计划动作 ${index + 1}`"
                  @click="planItems.splice(index, 1)"
                >
                  删除动作
                </button>
              </div>
              <label class="field" :for="`plan-exercise-${item.key}`"
                ><span>训练动作</span
                ><select
                  :id="`plan-exercise-${item.key}`"
                  v-model="item.exerciseId"
                  required
                >
                  <option disabled value="">请选择动作</option>
                  <option
                    v-for="exercise in exercises"
                    :key="exercise.id"
                    :value="exercise.id"
                  >
                    {{ exerciseLabel(exercise) }}
                  </option>
                </select></label
              >
              <div class="grid-3 target-fields">
                <label class="field" :for="`plan-sets-${item.key}`"
                  ><span>目标组数</span
                  ><input
                    :id="`plan-sets-${item.key}`"
                    v-model="item.sets"
                    type="number"
                    inputmode="numeric"
                    min="1"
                    step="1"
                    required
                /></label>
                <label class="field" :for="`plan-reps-${item.key}`"
                  ><span>每组次数</span
                  ><input
                    :id="`plan-reps-${item.key}`"
                    v-model="item.reps"
                    type="number"
                    inputmode="numeric"
                    min="1"
                    step="1"
                    required
                /></label>
                <label class="field" :for="`plan-weight-${item.key}`"
                  ><span>目标重量 / kg</span
                  ><input
                    :id="`plan-weight-${item.key}`"
                    v-model="item.weight"
                    type="number"
                    inputmode="decimal"
                    min="0"
                    step="any"
                    required
                /></label>
              </div>
            </div>
            <button
              class="button secondary"
              type="button"
              @click="planItems.push(newPlanItem())"
            >
              ＋ 添加目标动作
            </button>
            <p v-if="localError" class="error">{{ localError }}</p>
            <div class="actions">
              <button class="button" type="submit">
                {{ locked ? "保存中…" : "保存计划" }}</button
              ><button
                class="button ghost"
                type="button"
                @click="planEditorOpen = false"
              >
                放弃编辑
              </button>
            </div>
          </fieldset>
        </form>
      </section>

      <section class="stack" aria-labelledby="record-heading">
        <div class="section-heading row section-toolbar">
          <div>
            <p class="eyebrow">02 / WHAT YOU DID</p>
            <h2 id="record-heading">记下实际训练</h2>
          </div>
          <button
            class="button secondary"
            type="button"
            :disabled="locked || !selectedDate"
            @click="openRecord()"
          >
            ＋ 记录训练
          </button>
        </div>
        <p v-if="!recordEditorOpen" class="muted">
          临时训练也值得记录。只有你亲自填写的组数、重量和次数，才会成为实际记录。
        </p>
        <form
          v-if="recordEditorOpen"
          ref="recordForm"
          class="card stack editor-card"
          @submit.prevent="saveWorkout"
        >
          <div>
            <h3>实际训练记录</h3>
            <p class="muted">
              记录日期：{{ selectedDate }} · 目标不会自动填入实际数据。
            </p>
          </div>
          <fieldset class="editor-fields stack" :disabled="locked">
            <label class="field" for="record-plan"
              ><span>关联计划（可选）</span
              ><select
                id="record-plan"
                v-model="recordPlanId"
                @change="fillRecordFromPlan"
              >
                <option value="">临时训练 · 不关联计划</option>
                <option
                  v-for="plan in linkablePlans"
                  :key="plan.id"
                  :value="plan.id"
                >
                  {{ plan.title
                  }}{{
                    plan.status === "completed"
                      ? "（已完成，可追加记录）"
                      : "（待完成）"
                  }}
                </option>
              </select></label
            >
            <p class="muted">
              切换关联计划会重置下方未保存的动作和组数据；每个动作先提供一组空白实际数据。
            </p>
            <p v-if="linkedPlan" class="target-note">
              计划参考：{{
                linkedPlan.items
                  .map(
                    (item) =>
                      `${exerciseName(item.exerciseId)} ${item.sets}组 × ${item.reps}次 / ${item.weight}kg`,
                  )
                  .join("；")
              }}。请按实际表现填写，不必与目标一致。
            </p>
            <div
              v-for="(item, itemIndex) in recordItems"
              :key="item.key"
              class="exercise-editor stack"
            >
              <div class="row section-toolbar">
                <h4>实际动作 {{ itemIndex + 1 }}</h4>
                <button
                  class="button ghost"
                  type="button"
                  :disabled="recordItems.length === 1"
                  :aria-label="`删除实际动作 ${itemIndex + 1}`"
                  @click="recordItems.splice(itemIndex, 1)"
                >
                  删除动作
                </button>
              </div>
              <label class="field" :for="`record-exercise-${item.key}`"
                ><span>训练动作</span
                ><select
                  :id="`record-exercise-${item.key}`"
                  v-model="item.exerciseId"
                  required
                >
                  <option disabled value="">请选择动作</option>
                  <option
                    v-for="exercise in exercises"
                    :key="exercise.id"
                    :value="exercise.id"
                  >
                    {{ exerciseLabel(exercise) }}
                  </option>
                </select></label
              >
              <div
                v-for="(set, setIndex) in item.sets"
                :key="set.key"
                class="actual-set"
              >
                <span class="set-index number">第 {{ setIndex + 1 }} 组</span>
                <label class="field" :for="`actual-weight-${set.key}`"
                  ><span>实际重量 / kg</span
                  ><input
                    :id="`actual-weight-${set.key}`"
                    v-model="set.weight"
                    type="number"
                    inputmode="decimal"
                    min="0"
                    step="any"
                    required
                    placeholder="0"
                /></label>
                <label class="field" :for="`actual-reps-${set.key}`"
                  ><span>实际次数</span
                  ><input
                    :id="`actual-reps-${set.key}`"
                    v-model="set.reps"
                    type="number"
                    inputmode="numeric"
                    min="1"
                    step="1"
                    required
                    placeholder="填写次数"
                /></label>
                <button
                  class="button ghost remove-set"
                  type="button"
                  :disabled="item.sets.length === 1"
                  :aria-label="`删除实际动作 ${itemIndex + 1} 的第 ${setIndex + 1} 组`"
                  @click="item.sets.splice(setIndex, 1)"
                >
                  删除组
                </button>
              </div>
              <button
                class="button secondary add-set"
                type="button"
                @click="item.sets.push(newSet())"
              >
                ＋ 添加一组
              </button>
            </div>
            <button
              class="button secondary"
              type="button"
              @click="recordItems.push(newRecordItem())"
            >
              ＋ 添加实际动作
            </button>
            <label class="completion-choice" for="record-complete"
              ><input
                id="record-complete"
                v-model="completeWithRecord"
                type="checkbox"
                :disabled="!canCompleteWithRecord"
              /><span
                >保存时同时完成关联计划<span class="muted completion-hint">{{
                  canCompleteWithRecord
                    ? "不勾选则只保存记录，计划仍为待完成。"
                    : linkedPlan
                      ? "此计划已完成，无需再次打卡；仍可保存实际记录。"
                      : "临时训练没有可完成的关联计划。"
                }}</span></span
              ></label
            >
            <p v-if="localError" class="error">{{ localError }}</p>
            <div class="actions">
              <button class="button" type="submit">
                {{
                  locked
                    ? "保存中…"
                    : completeWithRecord
                      ? "保存记录并完成计划"
                      : "仅保存实际记录"
                }}</button
              ><button
                class="button ghost"
                type="button"
                @click="recordEditorOpen = false"
              >
                放弃记录
              </button>
            </div>
          </fieldset>
        </form>
      </section>

      <section class="stack" aria-labelledby="history-heading">
        <div class="section-heading row section-toolbar">
          <div>
            <p class="eyebrow">03 / YOUR PROGRESS</p>
            <h2 id="history-heading">训练历史</h2>
          </div>
          <div class="segmented" role="group" aria-label="历史记录范围">
            <button
              type="button"
              :aria-pressed="historyScope === 'day'"
              :class="{ active: historyScope === 'day' }"
              @click="historyScope = 'day'"
            >
              所选日期</button
            ><button
              type="button"
              :aria-pressed="historyScope === 'all'"
              :class="{ active: historyScope === 'all' }"
              @click="historyScope = 'all'"
            >
              全部记录
            </button>
          </div>
        </div>
        <p v-if="!history.length" class="card empty">
          {{
            historyScope === "all"
              ? "还没有实际训练记录。从第一组开始，积累自己的进步。"
              : "这一天还没有实际训练记录。完成计划标记不会自动生成记录。"
          }}
        </p>
        <article
          v-for="workout in history"
          :key="workout.id"
          class="card stack history-card"
        >
          <div class="row section-toolbar">
            <h3>
              {{
                workout.planId
                  ? planForWorkout(workout)?.title || "原关联计划"
                  : "临时训练"
              }}
            </h3>
            <time class="badge number" :datetime="workout.date">{{
              workout.date
            }}</time>
          </div>
          <p class="muted">
            实际记录<span
              v-if="planForWorkout(workout)?.status === 'cancelled'"
            >
              · 关联计划已取消，记录保留</span
            ><span v-else-if="workout.planId && !planForWorkout(workout)">
              · 原计划已不可用，记录保留</span
            >
          </p>
          <div
            v-for="(item, itemIndex) in workout.items"
            :key="`${workout.id}-${itemIndex}`"
            class="history-exercise"
          >
            <h4>{{ exerciseName(item.exerciseId) }}</h4>
            <ul class="actual-history">
              <li
                v-for="(set, setIndex) in item.sets"
                :key="`${workout.id}-${itemIndex}-${setIndex}`"
              >
                <span class="muted">第 {{ setIndex + 1 }} 组</span
                ><strong class="number"
                  >{{ set.weight }} kg × {{ set.reps }} 次</strong
                >
              </li>
            </ul>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped>
.training-page {
  --training-accent: #187f73;
  gap: 1.75rem;
}
.training-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.training-heading h1 {
  max-width: 16em;
}
.training-mark {
  display: grid;
  place-items: center;
  flex: 0 0 3.25rem;
  height: 3.25rem;
  border-radius: 50%;
  color: var(--training-accent);
  background: #e2f0e6;
  font-size: 2rem;
}
.training-page h2,
.training-page h3,
.training-page h4,
.training-page p {
  margin-block: 0;
}
.training-page h2 {
  font-size: 1.25rem;
}
.training-page h3 {
  font-size: 1.08rem;
  overflow-wrap: anywhere;
}
.training-page h4 {
  font-size: 0.95rem;
}
.training-page .eyebrow {
  margin-bottom: 0.45rem;
}
.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.date-card {
  display: grid;
  gap: 1rem;
  background: #f8fbf5;
}
.day-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
}
.day-summary strong {
  color: var(--training-accent);
  font-size: 1.35rem;
}
.exercise-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.target-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.5rem;
}
.plan-caption {
  font-size: 0.85rem;
}
.status-completed {
  background: #dff3e8;
  color: #175d46;
}
.status-cancelled {
  background: #eeeae3;
  color: #6b6357;
}
.status-pending {
  background: #fff0cf;
  color: #705514;
}
.confirmation {
  padding: 1rem;
  background: #fff8e9;
  border: 1px solid #e9d8b4;
  border-radius: 0.85rem;
}
.editor-card {
  border-top: 3px solid var(--training-accent);
  scroll-margin-top: 1rem;
}
.editor-fields {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}
.exercise-editor {
  padding: 1rem;
  background: #f6f8f1;
  border: 1px solid #e1e9dc;
  border-radius: 1rem;
  min-width: 0;
}
.target-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.6rem;
}
.training-page .field {
  min-width: 0;
}
.training-page input:not([type="checkbox"]),
.training-page select {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
}
.actual-set {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.6rem;
  align-items: end;
  padding-block: 0.4rem;
}
.set-index {
  grid-column: 1 / -1;
  font-size: 0.85rem;
  color: var(--training-accent);
}
.remove-set {
  grid-column: 1 / -1;
  justify-self: start;
}
.add-set {
  align-self: flex-start;
}
.target-note {
  padding: 0.8rem;
  background: #edf5ee;
  border-radius: 0.75rem;
  font-size: 0.875rem;
  line-height: 1.7;
}
.completion-choice {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
  padding-block: 0.5rem;
  cursor: pointer;
}
.completion-choice input {
  flex-shrink: 0;
  width: 1.25rem;
  height: 1.25rem;
  margin-top: 0.15rem;
  accent-color: var(--training-accent);
}
.completion-hint {
  display: block;
  margin-top: 0.35rem;
  font-size: 0.85rem;
}
.history-exercise {
  border-top: 1px solid #e3e8df;
  padding-top: 0.85rem;
}
.actual-history {
  list-style: none;
  display: grid;
  gap: 0.5rem;
  padding: 0;
  margin: 0.65rem 0 0;
}
.actual-history li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.75rem;
}
@media (min-width: 640px) {
  .date-card {
    grid-template-columns: minmax(12rem, 18rem) 1fr;
    align-items: end;
  }
  .day-summary {
    justify-content: flex-end;
  }
  .actual-set {
    grid-template-columns: 4rem minmax(0, 1fr) minmax(0, 1fr) auto;
  }
  .set-index,
  .remove-set {
    grid-column: auto;
  }
  .set-index {
    align-self: center;
  }
}
</style>
