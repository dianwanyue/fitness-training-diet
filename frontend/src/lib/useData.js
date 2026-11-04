import { ref } from "vue";
import { createRepository, foods as demoFoods, exercises as demoExercises } from "./repository.js";
import { createRemoteRepository, validateRange } from "./remoteRepository.js";
import { summarize, nutrition, today, dateRange } from "./domain.js";

const MODE = "ftdms.connection.v1.mode";
const clone = (value) => JSON.parse(JSON.stringify(value));
const key = () => globalThis.crypto.randomUUID();

export function createDataStore(options = {}) {
  const data = ref(null), user = ref(null), ready = ref(false), error = ref(null), busy = ref(false);
  const mode = ref("remote"), foods = ref([]), exercises = ref([]), loading = ref(false);
  let generation = 0, revision = 0, reads = 0;
  let local, remote;
  const storage = () => options.modeStorage ?? globalThis.sessionStorage;
  function reset() {
    generation++; revision++;
    data.value = null; user.value = null; foods.value = []; exercises.value = [];
  }
  const repo = () => mode.value === "demo"
    ? (local ??= options.localRepository ?? createRepository())
    : (remote ??= options.remoteRepository ?? createRemoteRepository({ onUnauthorized(cause) {
      if (mode.value === "remote") { reset(); ready.value = true; error.value = cause.message; }
    } }));
  function check(version) {
    if (version !== generation) throw new Error("账户已切换，已忽略旧请求，请重新加载");
  }
  function fail(cause, version) {
    if (version !== generation) return;
    error.value = cause instanceof Error ? cause.message : String(cause);
    if (mode.value === "remote" && (cause.status === 401 || cause.code === 1002 || cause.code === 2002)) {
      remote?.clear?.(); reset(); ready.value = true;
    }
  }
  async function operation(work) {
    if (busy.value) throw new Error("正在保存或加载，请稍后再试");
    busy.value = true; error.value = null;
    const version = generation;
    try { return await work(version); }
    catch (cause) { fail(cause, version); throw cause; }
    finally { busy.value = false; }
  }
  async function readCurrent(version) {
    const source = repo();
    const current = source.session();
    if (!current) { check(version); user.value = null; data.value = null; ready.value = true; return; }
    if (mode.value === "demo") {
      const records = await source.load(); check(version);
      user.value = current; data.value = records; foods.value = demoFoods; exercises.value = demoExercises;
    } else {
      const result = await source.load(); check(version);
      data.value = result.data; foods.value = result.foods; exercises.value = result.exercises; user.value = result.user;
    }
    ready.value = true;
  }
  async function refresh() {
    return operation(async (version) => {
      revision++;
      try {
        if (!ready.value) {
          const saved = storage().getItem(MODE);
          mode.value = saved === "demo" ? "demo" : "remote";
        }
        await readCurrent(version);
      }
      catch (cause) { if (version === generation) { user.value = null; data.value = null; } throw cause; }
      finally { ready.value = true; }
    });
  }
  async function setMode(next) {
    if (!["remote", "demo"].includes(next)) throw new Error("连接模式无效");
    if (mode.value === next) return;
    return operation(async () => {
      if (user.value) {
        try { await repo().logout(); } finally { reset(); }
      } else reset();
      storage().setItem(MODE, next);
      mode.value = next; error.value = null; ready.value = true;
    });
  }
  async function authenticate(method, username, password) {
    return operation(async (version) => {
      const source = repo();
      await source[method](username, password);
      if (version !== generation) {
        await source.logout();
        check(version);
      }
      try { await readCurrent(version); return user.value; }
      catch (cause) { if (version === generation) { user.value = null; data.value = null; } throw cause; }
    });
  }
  async function logout() {
    // Logout is cancellation, so it must work even while a write/login is busy.
    const source = repo(); reset(); ready.value = true; error.value = null;
    try { await source.logout(); }
    catch (cause) { error.value = `已清除本机会话；服务端登出未确认。${cause.message}`; throw cause; }
  }
  function requireData() {
    if (!user.value || !data.value) throw new Error("请先登录");
  }
  function mergeRange(result, from, to) {
    const next = { ...data.value };
    for (const field of ["plans", "workouts", "body", "diet"]) {
      if (result[field]) next[field] = [...next[field].filter((record) => record.date < from || record.date > to), ...result[field]];
    }
    if (result.goals) {
      const goals = new Map(next.goals.map((goal) => [goal.id, goal]));
      for (const goal of result.goals) goals.set(goal.id, goal);
      next.goals = [...goals.values()];
    }
    data.value = next;
  }
  async function loadRange(from, to) {
    validateRange(from, to); requireData();
    if (mode.value === "demo") return;
    const version = generation, currentRevision = revision;
    reads++; loading.value = true;
    try {
      const result = await repo().loadRange(from, to);
      check(version);
      if (currentRevision === revision) mergeRange(result, from, to);
    } catch (cause) { fail(cause, version); throw cause; }
    finally { reads--; loading.value = reads > 0; }
  }
  async function getStats(from, to) {
    validateRange(from, to); requireData();
    if (mode.value === "demo") {
      const result = summarize(data.value, from, to);
      const first = dateRange(to, 7)[0];
      const seven = summarize(data.value, first < from ? from : first, to);
      return { ...result, sevenDayAverage: seven.weightAverage, sevenDayDays: seven.weightDays,
        sevenDayValidDays: seven.weightDays };
    }
    const version = generation;
    try { const result = await repo().getStats(from, to); check(version); return result; }
    catch (cause) { fail(cause, version); throw cause; }
  }
  async function localCommit(mutator) {
    const version = generation, username = user.value.username;
    const source = repo();
    const next = clone(data.value);
    await mutator(next);
    check(version);
    if (source.session()?.username !== username) throw new Error("账户已切换，未保存此次修改");
    await source.save(next);
    check(version);
    data.value = next;
  }
  async function mutate(method, value, mutator, date) {
    return operation(async (version) => {
      requireData(); revision++;
      if (mode.value === "demo") return localCommit(mutator);
      let result;
      try { result = method === "setPlanStatus"
        ? await repo().setPlanStatus(value.recordId, value.status)
        : await repo()[method](value); }
      catch (cause) {
        if (cause.partial && version === generation && user.value) {
          try {
            const records = await repo().loadRange(date, date);
            check(version);
            mergeRange(records, date, date);
          } catch { /* retain the explicit partial-success message */ }
        }
        throw cause;
      }
      check(version);
      // A confirmed write must not be presented as a failed write if the subsequent read fails.
      try {
        if (method === "saveProfile" || method === "saveGoal") await readCurrent(version);
        else { const records = await repo().loadRange(date, date); check(version); mergeRange(records, date, date); }
      } catch (cause) {
        throw Object.assign(new Error(`已保存到服务器，但重新读取失败；请刷新确认，不要重复提交。${cause.message}`),
          { partial: true, recordSaved: method === "saveWorkout", status: cause.status, code: cause.code, cause });
      }
      revision++;
      return result;
    });
  }
  const recordDate = (collection, recordId) => {
    requireData();
    const record = data.value[collection].find((item) => item.id === recordId);
    if (!record) throw new Error("记录未加载，请刷新后重试");
    return record.date;
  };
  const store = {
    data, user, ready, error, busy, loading, mode, foods, exercises, refresh, setMode, loadRange, getStats, logout,
    login: (username, password) => authenticate("login", username, password),
    register: (username, password) => authenticate("register", username, password),
    // Legacy mutators are strictly demo-only. Real pages use explicit endpoint operations.
    commit: (mutator) => operation(async () => {
      requireData(); if (mode.value !== "demo") throw new Error("真实模式不能直接修改本地数据");
      return localCommit(mutator);
    }),
    saveProfile: (value) => mutate("saveProfile", value, (draft) => { draft.profile = { ...value }; }),
    saveGoal: (value) => mutate("saveGoal", value, (draft) => {
      const old = draft.goals.find((goal) => goal.effectiveDate === value.effectiveDate);
      if (old) Object.assign(old, value); else draft.goals.push({ ...value, id: key() });
    }),
    savePlan: (value) => mutate("savePlan", value, (draft) => {
      if (value.id) {
        const old = draft.plans.find((plan) => plan.id === value.id);
        if (!old || old.status !== "pending") throw new Error("仅待完成计划可以修改");
        Object.assign(old, value);
      } else draft.plans.push({ ...value, id: key(), status: "pending" });
    }, value.date),
    setPlanStatus: (recordId, status) => {
      const date = recordDate("plans", recordId);
      return mutate("setPlanStatus", { recordId, status }, (draft) => {
        const plan = draft.plans.find((record) => record.id === recordId);
        if (plan.status !== "pending") throw new Error("计划已变更，不能重复操作");
        plan.status = status;
      }, date);
    },
    saveWorkout: (value) => mutate("saveWorkout", value, (draft) => {
      draft.workouts.push({ id: key(), date: value.date, planId: value.planId || null, items: clone(value.items) });
      if (value.completePlan && value.planId) {
        const plan = draft.plans.find((record) => record.id === value.planId);
        if (!plan || plan.status !== "pending") throw new Error("计划已变更，请刷新");
        plan.status = "completed";
      }
    }, value.date),
    saveDiet: (value) => mutate("saveDiet", value, (draft) => {
      if (value.id) {
        const old = draft.diet.find((record) => record.id === value.id);
        if (!old) throw new Error("记录已不存在");
        const ratio = Number(value.grams) / old.grams;
        old.snapshot = Object.fromEntries(Object.entries(old.snapshot).map(([name, amount]) => [name, amount * ratio]));
        old.grams = Number(value.grams); old.meal = value.meal;
      } else {
        const food = demoFoods.find((record) => record.id === value.foodId);
        if (!food) throw new Error("请选择食物");
        draft.diet.push({ ...value, id: key(), foodName: food.name, source: food.source, state: food.state,
          grams: Number(value.grams), snapshot: nutrition(food, value.grams) });
      }
    }, value.date),
    deleteDiet: (recordId) => {
      const date = recordDate("diet", recordId);
      return mutate("deleteDiet", recordId, (draft) => { draft.diet = draft.diet.filter((record) => record.id !== recordId); }, date);
    },
    saveBody: (value) => mutate("saveBody", value, (draft) => {
      const old = draft.body.find((record) => record.date === value.date);
      if (old) old.weight = value.weight; else draft.body.push({ ...value, id: key() });
    }, value.date),
    deleteBody: (recordId) => {
      const date = recordDate("body", recordId);
      return mutate("deleteBody", recordId, (draft) => { draft.body = draft.body.filter((record) => record.id !== recordId); }, date);
    },
  };
  return store;
}

const store = createDataStore();
export function useData() { return store; }
