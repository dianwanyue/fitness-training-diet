import { api, apiError } from "./http.js";
import { dateRange, isBusinessDate, today } from "./domain.js";

const SESSION = "ftdms.remote.v1.session";
const meals = { 早餐: "BREAKFAST", 午餐: "LUNCH", 晚餐: "DINNER", 加餐: "SNACK" };
const mealNames = Object.fromEntries(Object.entries(meals).map(([name, code]) => [code, name]));
const statuses = { PLANNED: "pending", COMPLETED: "completed", CANCELLED: "cancelled" };
const foodStates = { RAW: "生", COOKED: "熟", BRAND: "品牌食品" };
const id = (value) => value == null ? null : String(value);
const number = (value) => value == null ? null : Number(value);
const goalView = (value) => value && ({
  id: id(value.id), effectiveDate: value.effectiveDate, kcal: number(value.kcal),
  protein: number(value.proteinG), carbs: number(value.carbG), fat: number(value.fatG),
});
const profileView = (value) => ({
  nickname: value.nickname || value.username, height: number(value.heightCm),
  birthday: value.birthDate || "", gender: value.gender,
});
function planView(value) {
  return { id: id(value.id), date: value.plannedDate, title: value.title || "训练计划",
    status: statuses[value.status], note: value.note || "", items: (value.items || []).map((item) => ({
      exerciseId: id(item.exerciseId), exerciseName: item.exerciseName, sets: item.targetSets,
      reps: item.targetReps, weight: number(item.targetWeightKg) ?? 0,
    })) };
}
function workoutView(value) {
  const groups = new Map();
  for (const item of value.sets || []) {
    const key = id(item.exerciseId);
    if (!groups.has(key)) groups.set(key, { exerciseId: key, exerciseName: item.exerciseName, sets: [] });
    groups.get(key).sets.push({ weight: number(item.weightKg) ?? 0, reps: item.reps });
  }
  return { id: id(value.id), date: value.logDate, planId: id(value.planId),
    performedAt: value.performedAt, items: [...groups.values()] };
}
const bodyView = (value) => ({ id: id(value.id), date: value.recordDate, weight: number(value.weightKg) });
function dietView(value, date) {
  return { id: id(value.id), date, meal: mealNames[value.meal] || value.meal,
    foodId: id(value.foodId), foodName: value.foodName, grams: number(value.grams),
    snapshot: { kcal: number(value.snapshotKcal), protein: number(value.snapshotProteinG),
      carbs: number(value.snapshotCarbG), fat: number(value.snapshotFatG) },
    // Historical source/state are not exposed by the backend. Do not mislabel current catalog values as snapshots.
    source: "营养快照由服务端保存", state: "" };
}
const foodView = (value) => ({ id: id(value.id), name: value.name,
  state: foodStates[value.state] || value.state, source: value.source || "来源未标注",
  kcal: number(value.kcalPer100g), protein: number(value.proteinPer100g),
  carbs: number(value.carbPer100g), fat: number(value.fatPer100g) });

export function validateRange(from, to) {
  if (!isBusinessDate(from) || !isBusinessDate(to) || from > to) throw new Error("请选择有效的起止日期");
  const days = (Date.parse(to) - Date.parse(from)) / 86400000 + 1;
  if (days > 366) throw new Error("查询区间最多 366 天");
  return dateRange(to, days);
}

// Bound detail requests rather than opening one connection per record at once.
async function mapLimited(items, work, limit = 6) {
  const results = new Array(items.length);
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await work(items[index]);
    }
  }));
  return results;
}

export function createRemoteRepository({ client = api, storage = globalThis.sessionStorage, onUnauthorized = () => {} } = {}) {
  function clear() { storage.removeItem(SESSION); }
  function session() {
    const raw = storage.getItem(SESSION);
    if (!raw) return null;
    let value;
    try { value = JSON.parse(raw); } catch { clear(); throw new Error("登录会话已损坏，请重新登录"); }
    if (!value?.token || !value.user?.username || !Number.isFinite(value.expiresAt)) {
      clear(); throw new Error("登录会话格式无效，请重新登录");
    }
    if (value.expiresAt <= Date.now()) {
      clear(); throw apiError("登录已过期，请重新登录", { code: 1002, status: 401 });
    }
    return value;
  }
  async function request(method, url, data, params, authenticated = true) {
    const current = authenticated ? session() : null;
    if (authenticated && !current) throw apiError("请先登录", { code: 1002, status: 401 });
    try {
      return await client.request({ method, url, data, params,
        headers: current ? { Authorization: `Bearer ${current.token}` } : {} });
    } catch (cause) {
      if (authenticated && (cause.status === 401 || cause.code === 1002 || cause.code === 2002)) {
        // An old failing request must never erase a newer account's session.
        const raw = storage.getItem(SESSION);
        if (raw && JSON.parse(raw).token === current.token) {
          clear(); onUnauthorized(cause);
        }
      }
      throw cause;
    }
  }
  const get = (url, params) => request("GET", url, undefined, params);
  async function pages(url) {
    const result = [];
    for (let page = 1; ; page++) {
      const response = await get(url, { page, size: 100 });
      if (!Array.isArray(response?.list)) throw new Error("目录分页响应无效");
      result.push(...response.list);
      if (result.length >= response.total) return result;
      if (!response.list.length) throw new Error("目录分页不完整，请重试");
    }
  }
  async function goals() {
    const end = dateRange(today(), 1)[0];
    const future = new Date(`${end}T00:00:00Z`);
    future.setUTCDate(future.getUTCDate() + 7);
    const to = future.toISOString().slice(0, 10);
    const [history, current] = await Promise.all([
      get("/goals/history", { from: dateRange(to, 366)[0], to }), get("/goals/current"),
    ]);
    const byId = new Map(history.map((value) => [id(value.id), goalView(value)]));
    if (current) byId.set(id(current.id), goalView(current));
    return [...byId.values()];
  }
  async function loadRange(from, to) {
    validateRange(from, to);
    const params = { from, to };
    const [plans, workouts, bodies, summary] = await Promise.all([
      get("/plans", params), get("/workouts", params), get("/body-records", params),
      from === to ? get("/diets/summary", { date: to }) : null,
    ]);
    const details = await mapLimited(plans, (plan) => get(`/plans/${plan.id}`));
    const result = { plans: details.map(planView), workouts: workouts.map(workoutView), body: bodies.list.map(bodyView) };
    if (summary) {
      result.diet = Object.values(summary.meals).flat().map((record) => dietView(record, to));
      result.goals = summary.target ? [goalView(summary.target)] : [];
      result.dailySummary = { date: to, kcal: number(summary.kcal), protein: number(summary.proteinG),
        carbs: number(summary.carbG), fat: number(summary.fatG) };
    }
    return result;
  }
  async function load() {
    const [profile, history, exercises, foodList, range] = await Promise.all([
      get("/users/me"), goals(), pages("/exercises"), pages("/foods"), loadRange(today(), today()),
    ]);
    const foods = await mapLimited(foodList, (food) => get(`/foods/${food.id}`).then(foodView));
    return { user: { id: id(profile.id), username: profile.username, role: profile.role },
      foods, exercises: exercises.map((value) => ({ id: id(value.id), name: value.name,
        muscle: value.muscleGroup || "未分类", equipment: value.equipment })),
      data: { profile: profileView(profile), goals: history, plans: range.plans,
        workouts: range.workouts, body: range.body, diet: range.diet || [] } };
  }
  async function login(username, password) {
    const result = await request("POST", "/auth/login", { username, password }, undefined, false);
    const value = { token: result.token, expiresAt: Date.now() + result.expiresIn * 1000, user: result.user };
    try { storage.setItem(SESSION, JSON.stringify(value)); }
    catch (cause) { throw apiError("无法保存登录会话，请允许当前标签页的会话存储", { cause }); }
    return result.user;
  }
  return {
    session, clear, login, load, loadRange, goals,
    async register(username, password) {
      await request("POST", "/auth/register", { username, password }, undefined, false);
      try { return await login(username, password); }
      catch (cause) { cause.message = `账户已注册，但自动登录失败，请直接登录。${cause.message}`; throw cause; }
    },
    async logout() {
      const current = session();
      // Clear synchronously before awaiting the network. A late logout response must
      // never remove a new account's token or trigger its unauthorized callback.
      clear();
      if (current) await client.request({ method: "POST", url: "/auth/logout",
        headers: { Authorization: `Bearer ${current.token}` } });
    },
    async getStats(from, to) {
      const dates = validateRange(from, to);
      const [workouts, intake, weight] = await Promise.all([
        get("/stats/workouts", { from, to }), get("/stats/intake", { from, to }), get("/stats/weight", { from, to }),
      ]);
      const energies = new Map(intake.days.map((point) => [point.date, point.recorded ? number(point.kcal) : null]));
      const weights = new Map(weight.points.map((point) => [point.date, number(point.weightKg)]));
      const values = [...weights.values()].filter((value) => value != null);
      return { dates, energy: dates.map((date) => energies.get(date) ?? null),
        weights: dates.map((date) => weights.get(date) ?? null),
        planned: workouts.plannedCount - workouts.cancelledCount, completed: workouts.completedCount,
        completion: workouts.completionRate == null ? null : Number(workouts.completionRate) * 100,
        temporaryWorkouts: workouts.extraWorkoutCount, dietDays: intake.validDays,
        intakeAverage: number(intake.avg?.kcal), weightDays: values.length,
        // Presentation-only mean of authoritative server points, not a new server metric.
        weightAverage: values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null,
        sevenDayAverage: number(weight.sevenDayAvg), sevenDayDays: weight.validDays,
        sevenDayValidDays: weight.validDays };
    },
    async saveProfile(value) {
      await request("PUT", "/users/me", { nickname: value.nickname, heightCm: value.height,
        birthDate: value.birthday || "", gender: value.gender });
    },
    async saveGoal(value) {
      await request("POST", "/goals", { effectiveDate: value.effectiveDate, kcal: value.kcal,
        proteinG: value.protein, carbG: value.carbs, fatG: value.fat });
    },
    async savePlan(value) {
      return request(value.id ? "PUT" : "POST", value.id ? `/plans/${value.id}` : "/plans", {
        plannedDate: value.date, title: value.title, note: value.note,
        items: value.items.map((item, index) => ({ exerciseId: Number(item.exerciseId), targetSets: item.sets,
          targetReps: item.reps, targetWeightKg: item.weight, orderNo: index })),
      });
    },
    async setPlanStatus(planId, status) {
      return request("POST", `/plans/${planId}/status`, { status: status.toUpperCase() });
    },
    async saveWorkout(value) {
      const result = await request("POST", "/workouts", { planId: value.planId ? Number(value.planId) : null,
        performedAt: `${value.date} 00:00:00`, sets: value.items.flatMap((item) => item.sets.map((set, index) => ({
          exerciseId: Number(item.exerciseId), setNo: index + 1, weightKg: set.weight, reps: set.reps,
        }))) });
      if (value.completePlan && value.planId) {
        try { await request("POST", `/plans/${value.planId}/status`, { status: "COMPLETED" }); }
        catch (cause) { throw apiError(`训练记录已保存，但计划打卡失败；请勿重复录入，只需重试标记完成。${cause.message}`,
          { partial: true, recordSaved: true, status: cause.status, code: cause.code, cause, result }); }
      }
      return result;
    },
    async saveDiet(value) {
      return request(value.id ? "PUT" : "POST", value.id ? `/diets/${value.id}` : "/diets",
        value.id ? { meal: meals[value.meal], grams: value.grams } :
          { recordDate: value.date, meal: meals[value.meal], foodId: Number(value.foodId), grams: value.grams });
    },
    deleteDiet: (recordId) => request("DELETE", `/diets/${recordId}`),
    saveBody: (value) => request("PUT", "/body-records", { recordDate: value.date, weightKg: value.weight }),
    deleteBody: (recordId) => request("DELETE", `/body-records/${recordId}`),
  };
}
