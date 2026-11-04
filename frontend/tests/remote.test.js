import test from "node:test";
import assert from "node:assert/strict";
import { apiError, createApi, unwrapResponse } from "../src/lib/http.js";
import { createRemoteRepository, validateRange } from "../src/lib/remoteRepository.js";
import { today, dateRange } from "../src/lib/domain.js";

class MemoryStorage {
  entries = new Map();
  getItem(key) { return this.entries.get(key) ?? null; }
  setItem(key, value) { this.entries.set(key, String(value)); }
  removeItem(key) { this.entries.delete(key); }
}
const SESSION = "ftdms.remote.v1.session";
const copy = (value) => structuredClone(value);
function seed(storage, token = "token-A", username = "alice") {
  storage.setItem(SESSION, JSON.stringify({ token, expiresAt: Date.now() + 3600000,
    user: { id: 1, username, role: "USER" } }));
}
function fixture(handler, { authenticated = true, onUnauthorized } = {}) {
  const storage = new MemoryStorage(), calls = [];
  if (authenticated) seed(storage);
  const client = { async request(config) { calls.push(copy(config)); return handler(config); } };
  return { repository: createRemoteRepository({ storage, client, onUnauthorized }), storage, calls };
}
const goal = { id: 1, effectiveDate: today(), kcal: "2100.00", proteinG: "120.00", carbG: null, fatG: "60.00" };
function statsRoute(config) {
  if (config.url === "/stats/workouts") return { plannedCount: 4, completedCount: 2,
    cancelledCount: 1, completionRate: "0.666666", extraWorkoutCount: 3 };
  if (config.url === "/stats/intake") return { validDays: 2, avg: { kcal: "100.00" }, days: [
    { date: "2026-11-01", recorded: true, kcal: "0.00" },
    { date: "2026-11-02", recorded: false, kcal: null },
    { date: "2026-11-03", recorded: true, kcal: "200.00" },
  ] };
  if (config.url === "/stats/weight") return { points: [
    { date: "2026-11-01", weightKg: "70.00" }, { date: "2026-11-03", weightKg: "68.00" },
  ], sevenDayAvg: "69.00", validDays: 2 };
  throw new Error(`Unexpected request ${config.url}`);
}

test("HTTP envelopes preserve null success and reject HTTP-200 business failures", () => {
  assert.equal(unwrapResponse({ status: 200, data: { code: 0, data: null } }), null);
  assert.deepEqual(unwrapResponse({ status: 200, data: { code: 0, data: [1] } }), [1]);
  assert.throws(() => unwrapResponse({ status: 200, data: { code: 4003, message: "越权", data: null, traceId: "t-1" } }),
    (error) => error.code === 4003 && error.status === 200 && error.traceId === "t-1" && error.message === "越权");
  for (const data of ["<html>wrong server</html>", {}, { code: "0", data: [] }, { code: 0 }])
    assert.throws(() => unwrapResponse({ status: 200, data }), /响应格式/);
});

test("Axios adapter propagates business errors and readable offline errors without fallback", async () => {
  const client = createApi("/custom/api");
  client.defaults.adapter = async (config) => ({ config, status: 200, headers: {}, data: { code: 1002, message: "令牌失效", data: null } });
  await assert.rejects(client.get("/test"), (error) => error.code === 1002 && error.status === 200);
  client.defaults.adapter = async () => { throw Object.assign(new Error("offline"), { code: "ERR_NETWORK" }); };
  await assert.rejects(client.get("/test"), /未切换为演示数据/);
});

test("remote login stores a session without password and supplies bearer token on subsequent requests", async () => {
  const { repository, storage, calls } = fixture((config) => config.url === "/auth/login"
    ? { token: "server-token", expiresIn: 600, user: { id: 7, username: "alice", role: "USER" } }
    : statsRoute(config), { authenticated: false });
  assert.equal(repository.session(), null);
  await repository.login("alice", "DoNotPersist@123");
  assert.deepEqual(calls[0].data, { username: "alice", password: "DoNotPersist@123" });
  assert.deepEqual(calls[0].headers, {});
  assert.equal(repository.session().token, "server-token");
  assert.ok(!storage.getItem(SESSION).includes("DoNotPersist"));
  await repository.getStats("2026-11-01", "2026-11-03");
  assert.ok(calls.slice(1).every((call) => call.headers.Authorization === "Bearer server-token"));
});

test("expired and corrupt sessions are cleared before any authenticated request", async () => {
  const { repository, storage, calls } = fixture(() => { throw new Error("must not call network"); });
  storage.setItem(SESSION, JSON.stringify({ token: "expired", user: { username: "alice" }, expiresAt: 1 }));
  await assert.rejects(repository.saveBody({ date: today(), weight: 70 }), (error) => error.status === 401);
  assert.equal(storage.getItem(SESSION), null);
  storage.setItem(SESSION, "invalid JSON");
  assert.throws(() => repository.session(), /损坏/);
  assert.equal(storage.getItem(SESSION), null);
  assert.equal(calls.length, 0);
});

test("catalog loading paginates, fetches food and plan details, and normalizes DTOs", async () => {
  const { repository, calls } = fixture((config) => {
    const { url, params } = config;
    if (url === "/users/me") return { id: 1, username: "alice", nickname: "训练者", heightCm: "175.50", birthDate: "2000-01-01", role: "USER" };
    if (url === "/goals/history") return [goal];
    if (url === "/goals/current") return goal;
    if (url === "/exercises") return { total: 2, list: [{ id: params.page, name: `动作${params.page}`, muscleGroup: "腿" }] };
    if (url === "/foods") return { total: 2, list: [{ id: params.page, name: `食物${params.page}` }] };
    if (/^\/foods\/\d+$/.test(url)) return { id: Number(url.split("/").at(-1)), name: "米饭", state: "COOKED", source: "资料库", kcalPer100g: "116", proteinPer100g: "2.6", carbPer100g: "25.9", fatPer100g: "0.3" };
    if (url === "/plans") return [{ id: 10 }];
    if (url === "/plans/10") return { id: 10, plannedDate: today(), title: "下肢", status: "PLANNED", items: [{ exerciseId: 1, exerciseName: "深蹲", targetSets: 3, targetReps: 8, targetWeightKg: "20" }] };
    if (url === "/workouts") return [{ id: 20, logDate: today(), planId: 10, performedAt: `${today()} 10:00:00`, sets: [{ exerciseId: 1, exerciseName: "深蹲", weightKg: "22.50", reps: 8 }, { exerciseId: 1, exerciseName: "深蹲", weightKg: null, reps: 10 }] }];
    if (url === "/body-records") return { list: [{ id: 30, recordDate: today(), weightKg: "70.25" }] };
    if (url === "/diets/summary") return { kcal: "174", proteinG: "3.9", carbG: "38.85", fatG: "0.45", target: goal,
      meals: { BREAKFAST: [{ id: 40, meal: "BREAKFAST", foodId: 1, foodName: "米饭", grams: "150", snapshotKcal: "174", snapshotProteinG: "3.9", snapshotCarbG: "38.85", snapshotFatG: "0.45" }] } };
    throw new Error(`Unexpected request ${url}`);
  });
  const result = await repository.load();
  assert.equal(result.user.username, "alice");
  assert.deepEqual(result.data.profile, { nickname: "训练者", height: 175.5, birthday: "2000-01-01", gender: undefined });
  assert.equal(result.data.goals.length, 1);
  assert.equal(result.data.goals[0].carbs, null);
  assert.equal(result.foods.length, 2);
  assert.equal(result.foods[0].kcal, 116);
  assert.equal(result.foods[0].state, "熟");
  assert.deepEqual(result.exercises.map((entry) => entry.id), ["1", "2"]);
  assert.equal(result.data.plans[0].status, "pending");
  assert.equal(result.data.plans[0].items[0].weight, 20);
  assert.equal(result.data.workouts[0].items.length, 1);
  assert.equal(result.data.workouts[0].items[0].sets.length, 2);
  assert.equal(result.data.body[0].weight, 70.25);
  assert.equal(result.data.diet[0].snapshot.kcal, 174);
  assert.equal(result.data.diet[0].meal, "早餐");
  assert.equal(result.data.diet[0].source, "营养快照由服务端保存");
  assert.ok(calls.some((call) => call.url === "/foods" && call.params.page === 2));
  assert.ok(calls.some((call) => call.url === "/exercises" && call.params.page === 2));
  assert.ok(calls.every((call) => call.headers.Authorization === "Bearer token-A"));
});

test("multi-day loads do not fan out daily diet requests", async () => {
  const { repository, calls } = fixture(({ url }) => url === "/body-records" ? { list: [] } : []);
  const result = await repository.loadRange("2026-11-01", "2026-11-03");
  assert.equal(Object.hasOwn(result, "diet"), false);
  assert.deepEqual(calls.map((call) => call.url).sort(), ["/body-records", "/plans", "/workouts"]);
});

test("statistics preserve valid zero and missing null and exclude cancelled plans from denominator", async () => {
  const { repository } = fixture(statsRoute);
  const result = await repository.getStats("2026-11-01", "2026-11-03");
  assert.deepEqual(result.energy, [0, null, 200]);
  assert.deepEqual(result.weights, [70, null, 68]);
  assert.equal(result.planned, 3);
  assert.equal(result.completed, 2);
  assert.ok(Math.abs(result.completion - 66.6666) < 1e-6);
  assert.equal(result.temporaryWorkouts, 3);
  assert.equal(result.weightDays, 2);
  assert.equal(result.dietDays, 2);
  assert.equal(result.weightAverage, 69);
  assert.equal(result.sevenDayAverage, 69);
  assert.equal(result.sevenDayDays, 2);
});

test("empty server statistics remain null rather than fabricated zero averages", async () => {
  const { repository } = fixture(({ url }) => url.endsWith("workouts")
    ? { plannedCount: 0, completedCount: 0, cancelledCount: 0, completionRate: null, extraWorkoutCount: 0 }
    : url.endsWith("intake") ? { days: [], validDays: 0, avg: null }
      : { points: [], sevenDayAvg: null, validDays: 0 });
  const result = await repository.getStats(today(), today());
  assert.equal(result.completion, null);
  assert.equal(result.weightAverage, null);
  assert.equal(result.sevenDayAverage, null);
  assert.deepEqual(result.energy, [null]);
  assert.deepEqual(result.weights, [null]);
});

test("workout converts grouped UI sets and reports persisted-record partial success", async () => {
  const { repository, calls } = fixture(({ url }) => {
    if (url === "/workouts") return { id: 50 };
    throw apiError("打卡失败", { status: 503 });
  });
  await assert.rejects(repository.saveWorkout({ date: today(), planId: "10", completePlan: true,
    items: [{ exerciseId: "2", sets: [{ weight: 25, reps: 8 }, { weight: 20, reps: 10 }] }] }),
  (error) => error.partial === true && error.recordSaved === true && /勿重复/.test(error.message));
  assert.equal(calls.length, 2);
  assert.equal(calls[0].data.planId, 10);
  assert.equal(calls[0].data.performedAt, `${today()} 00:00:00`);
  assert.deepEqual(calls[0].data.sets, [{ exerciseId: 2, setNo: 1, weightKg: 25, reps: 8 }, { exerciseId: 2, setNo: 2, weightKg: 20, reps: 10 }]);
  assert.deepEqual(calls[1].data, { status: "COMPLETED" });
});

test("diet creation and update never send client nutrition snapshots", async () => {
  const { repository, calls } = fixture(() => ({ id: 1 }));
  await repository.saveDiet({ date: today(), meal: "午餐", foodId: "5", grams: 150, snapshot: { kcal: 999 } });
  await repository.saveDiet({ id: "6", date: today(), meal: "晚餐", foodId: "999", grams: 200 });
  assert.deepEqual(calls[0].data, { recordDate: today(), meal: "LUNCH", foodId: 5, grams: 150 });
  assert.deepEqual(calls[1].data, { meal: "DINNER", grams: 200 });
  assert.equal(calls[1].url, "/diets/6");
  assert.equal(calls[1].method, "PUT");
});

test("plan status keeps its explicit two-argument API", async () => {
  const { repository, calls } = fixture(() => ({ id: 10 }));
  await repository.setPlanStatus("10", "cancelled");
  assert.equal(calls[0].url, "/plans/10/status");
  assert.deepEqual(calls[0].data, { status: "CANCELLED" });
});

test("logout removes the local token even if server logout fails", async () => {
  const { repository, storage } = fixture(() => { throw new Error("offline"); });
  await assert.rejects(repository.logout(), /offline/);
  assert.equal(storage.getItem(SESSION), null);
  assert.equal(repository.session(), null);
});

test("current-token 401 clears session while stale-token 401 cannot erase a newer login", async () => {
  let rejectRequest, unauthorized = 0;
  const { repository, storage } = fixture(() => new Promise((_, reject) => { rejectRequest = reject; }),
    { onUnauthorized() { unauthorized++; } });
  let pending = repository.saveBody({ date: today(), weight: 70 });
  const oldRejected = assert.rejects(pending, (error) => error.status === 401);
  seed(storage, "token-B", "bob");
  rejectRequest(apiError("expired A", { status: 401 }));
  await oldRejected;
  assert.equal(repository.session().user.username, "bob");
  assert.equal(unauthorized, 0);
  pending = repository.saveBody({ date: today(), weight: 70 });
  const currentRejected = assert.rejects(pending, (error) => error.status === 401);
  rejectRequest(apiError("expired B", { status: 401 }));
  await currentRejected;
  assert.equal(storage.getItem(SESSION), null);
  assert.equal(unauthorized, 1);
});

test("query dates enforce real calendar dates and a 366-day maximum", () => {
  assert.throws(() => validateRange("2026-02-30", "2026-03-01"), /有效/);
  assert.throws(() => validateRange("2026-11-03", "2026-11-01"), /有效/);
  assert.throws(() => validateRange("2025-01-01", "2026-11-01"), /366/);
  assert.equal(validateRange(dateRange(today(), 366)[0], today()).length, 366);
});

for (const outcome of ["success", "unauthorized"]) {
  test(`late account-A logout ${outcome} cannot clear account-B login or invalidate its session`, async () => {
    let finishLogout, rejectLogout, unauthorized = 0;
    const { repository, storage, calls } = fixture((config) => {
      if (config.url === "/auth/logout") return new Promise((resolve, reject) => {
        finishLogout = resolve;
        rejectLogout = reject;
      });
      if (config.url === "/auth/login") return {
        token: "token-B", expiresIn: 3600, user: { id: 2, username: "bob", role: "USER" },
      };
      return statsRoute(config);
    }, { onUnauthorized() { unauthorized++; } });

    const logout = repository.logout();
    const settledLogout = outcome === "unauthorized"
      ? assert.rejects(logout, (error) => error.status === 401 && error.code === 1002)
      : logout;
    // Local logout must happen before the old network request settles.
    assert.equal(storage.getItem(SESSION), null);
    assert.equal(repository.session(), null);
    assert.equal(calls[0].url, "/auth/logout");
    assert.equal(calls[0].headers.Authorization, "Bearer token-A");

    await repository.login("bob", "BobPass@123");
    const newSession = storage.getItem(SESSION);
    assert.equal(repository.session().token, "token-B");
    assert.equal(repository.session().user.username, "bob");
    assert.deepEqual(calls[1].headers, {});

    if (outcome === "success") finishLogout(null);
    else rejectLogout(apiError("account-A token expired", { status: 401, code: 1002 }));
    await settledLogout;

    assert.equal(storage.getItem(SESSION), newSession);
    assert.equal(repository.session().token, "token-B");
    assert.equal(repository.session().user.username, "bob");
    assert.equal(unauthorized, 0);
    // The new session must remain usable, not just present in storage.
    await repository.getStats("2026-11-01", "2026-11-03");
    assert.ok(calls.slice(2).every((call) => call.headers.Authorization === "Bearer token-B"));
  });
}
