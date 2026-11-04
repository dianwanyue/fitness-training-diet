import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { createDataStore } from "../src/lib/useData.js";
import { createRepository } from "../src/lib/repository.js";
import { apiError } from "../src/lib/http.js";
import { today, dateRange } from "../src/lib/domain.js";

if (!globalThis.crypto?.subtle) Object.defineProperty(globalThis, "crypto", { value: webcrypto, configurable: true });
class MemoryStorage {
  entries = new Map();
  getItem(key) { return this.entries.get(key) ?? null; }
  setItem(key, value) { this.entries.set(key, String(value)); }
  removeItem(key) { this.entries.delete(key); }
}
const clone = (value) => structuredClone(value);
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function dataFor(username) {
  return { profile: { nickname: username, height: 175, birthday: "" },
    goals: [{ id: `goal-${username}`, effectiveDate: today(), kcal: 2100, protein: 120, carbs: 250, fat: 60 }],
    plans: [{ id: `plan-${username}`, date: today(), title: `${username} plan`, status: "pending", items: [] }],
    workouts: [], diet: [], body: [] };
}
function makeRemote() {
  const server = { current: "alice", accounts: new Map([["alice", dataFor("alice")], ["bob", dataFor("bob")]]) };
  const calls = [];
  const currentData = () => {
    if (!server.current) throw apiError("请先登录", { status: 401 });
    if (!server.accounts.has(server.current)) server.accounts.set(server.current, dataFor(server.current));
    return server.accounts.get(server.current);
  };
  const remote = {
    session: () => server.current ? { token: `${server.current}-token`, user: { username: server.current } } : null,
    clear() { calls.push(["clear"]); server.current = null; },
    async login(username) { calls.push(["login", username]); server.current = username; },
    async register(username) { calls.push(["register", username]); server.current = username; },
    async logout() { calls.push(["logout"]); server.current = null; },
    async load() {
      calls.push(["load", server.current]);
      return { user: { id: server.current, username: server.current, role: "USER" }, data: clone(currentData()),
        foods: [{ id: "server-food", name: "server food" }], exercises: [{ id: "server-exercise", name: "server exercise" }] };
    },
    async loadRange(from, to) {
      calls.push(["loadRange", from, to]);
      const data = currentData();
      return Object.fromEntries(["plans", "workouts", "diet", "body"].map((field) => [field,
        clone(data[field].filter((entry) => entry.date >= from && entry.date <= to))]));
    },
    async saveBody(value) {
      calls.push(["saveBody", clone(value)]);
      const data = currentData(), weight = Math.round(value.weight * 100) / 100;
      const old = data.body.find((entry) => entry.date === value.date);
      if (old) old.weight = weight;
      else data.body.push({ id: `server-body-${server.current}`, date: value.date, weight });
      return { id: `server-body-${server.current}` };
    },
    async saveProfile(value) { calls.push(["saveProfile", clone(value)]); currentData().profile = { ...value, nickname: value.nickname.trim() }; },
    async saveGoal(value) { calls.push(["saveGoal", clone(value)]); currentData().goals.push({ ...value, id: "server-new-goal" }); },
    async setPlanStatus(recordId, status) {
      calls.push(["setPlanStatus", recordId, status]);
      const plan = currentData().plans.find((entry) => entry.id === recordId);
      assert.ok(plan, "status adapter must preserve the explicit plan id");
      plan.status = status;
    },
    async saveWorkout(value) {
      calls.push(["saveWorkout", clone(value)]);
      currentData().workouts.push({ id: "server-workout", date: value.date, planId: value.planId, items: clone(value.items) });
    },
    async getStats(from, to) {
      calls.push(["getStats", from, to]);
      return { dates: [from, to], energy: [null, 42], weights: [null, 70], planned: 2, completed: 1,
        completion: 50, temporaryWorkouts: 3, dietDays: 1, weightDays: 1, weightAverage: 70,
        sevenDayAverage: 70, sevenDayDays: 1 };
    },
  };
  return { server, remote, calls };
}
async function fixture() {
  const context = makeRemote(), modeStorage = new MemoryStorage();
  const localStorage = new MemoryStorage();
  const state = createDataStore({ remoteRepository: context.remote, modeStorage,
    localRepository: createRepository(localStorage) });
  await state.refresh();
  return { ...context, state, modeStorage, localStorage };
}

test("store defaults to remote and publishes authenticated server catalogs and profile", async () => {
  const { state } = await fixture();
  assert.equal(state.mode.value, "remote");
  assert.equal(state.ready.value, true);
  assert.equal(state.user.value.username, "alice");
  assert.equal(state.data.value.profile.nickname, "alice");
  assert.equal(state.foods.value[0].id, "server-food");
  assert.equal(state.exercises.value[0].id, "server-exercise");
  assert.equal(state.busy.value, false);
});

test("saveBody waits for authoritative server reread before publishing records", async () => {
  const { state, calls } = await fixture();
  await state.saveBody({ date: today(), weight: 70.12345 });
  assert.deepEqual(calls.slice(-2).map((entry) => entry[0]), ["saveBody", "loadRange"]);
  assert.equal(state.data.value.body[0].id, "server-body-alice");
  assert.equal(state.data.value.body[0].weight, 70.12);
  assert.equal(state.busy.value, false);
});

test("rejected remote writes keep published state and never save to demo storage", async () => {
  const { state, remote, calls, localStorage } = await fixture();
  const before = state.data.value;
  remote.saveBody = async () => { throw apiError("拒绝写入", { status: 403 }); };
  const count = calls.length;
  await assert.rejects(state.saveBody({ date: today(), weight: 80 }), /拒绝写入/);
  assert.equal(state.data.value, before);
  assert.equal(state.data.value.body.length, 0);
  assert.equal(state.mode.value, "remote");
  assert.equal(calls.length, count);
  assert.equal(localStorage.entries.size, 0);
  assert.equal(state.busy.value, false);
  assert.match(state.error.value, /拒绝写入/);
});

test("a confirmed write followed by reread failure raises partial instead of claiming the write failed", async () => {
  const { state, remote, server, localStorage } = await fixture();
  remote.loadRange = async () => { throw new Error("network unavailable"); };
  await assert.rejects(state.saveBody({ date: today(), weight: 72 }),
    (error) => error.partial === true && error.recordSaved === false && /不要重复提交/.test(error.message));
  assert.equal(server.accounts.get("alice").body[0].weight, 72);
  assert.deepEqual(state.data.value.body, []);
  assert.equal(localStorage.entries.size, 0);
  assert.equal(state.busy.value, false);
});

test("profile and goal mutations reload the complete authoritative profile and history", async () => {
  const { state, calls } = await fixture();
  await state.saveProfile({ nickname: "  服务端名称  ", height: 180, birthday: "2000-01-01" });
  assert.equal(state.data.value.profile.nickname, "服务端名称");
  assert.deepEqual(calls.slice(-2).map((entry) => entry[0]), ["saveProfile", "load"]);
  await state.saveGoal({ effectiveDate: today(), kcal: 2300, protein: 130, carbs: 270, fat: 70 });
  assert.ok(state.data.value.goals.some((entry) => entry.id === "server-new-goal"));
  assert.deepEqual(calls.slice(-2).map((entry) => entry[0]), ["saveGoal", "load"]);
});

test("setPlanStatus forwards two arguments without double-adapting the repository", async () => {
  const { state, calls } = await fixture();
  await state.setPlanStatus("plan-alice", "completed");
  assert.ok(calls.some((entry) => entry[0] === "setPlanStatus" && entry[1] === "plan-alice" && entry[2] === "completed"));
  assert.equal(state.data.value.plans[0].status, "completed");
});

test("getStats returns server results without rebuilding metrics from locally loaded records", async () => {
  const { state, calls } = await fixture();
  assert.deepEqual(state.data.value.body, []);
  const result = await state.getStats(dateRange(today(), 7)[0], today());
  assert.equal(result.completion, 50);
  assert.equal(result.weightDays, 1);
  assert.deepEqual(result.energy, [null, 42]);
  assert.equal(calls.at(-1)[0], "getStats");
});

test("401 clears private records and catalogs and does not switch to demo", async () => {
  const { state, remote, server } = await fixture();
  remote.loadRange = async () => { throw apiError("登录过期", { status: 401, code: 1002 }); };
  await assert.rejects(state.loadRange(today(), today()), /登录过期/);
  assert.equal(state.user.value, null);
  assert.equal(state.data.value, null);
  assert.deepEqual(state.foods.value, []);
  assert.deepEqual(state.exercises.value, []);
  assert.equal(state.mode.value, "remote");
  assert.equal(server.current, null);
  assert.equal(state.loading.value, false);
});

test("HTTP-200 unauthorized business codes also clear the store", async () => {
  const { state, remote } = await fixture();
  remote.getStats = async () => { throw apiError("账户已禁用", { status: 200, code: 2002 }); };
  await assert.rejects(state.getStats(today(), today()), /账户已禁用/);
  assert.equal(state.data.value, null);
  assert.equal(state.user.value, null);
});

test("stats transport failures remain errors rather than falling back to local summaries", async () => {
  const { state, remote, localStorage } = await fixture();
  remote.getStats = async () => { throw new Error("offline stats"); };
  await assert.rejects(state.getStats(today(), today()), /offline stats/);
  assert.equal(state.mode.value, "remote");
  assert.equal(state.user.value.username, "alice");
  assert.equal(localStorage.entries.size, 0);
});

test("legacy commit is blocked in remote mode before the mutator can run", async () => {
  const { state } = await fixture();
  let invoked = false;
  await assert.rejects(state.commit(() => { invoked = true; }), /真实模式/);
  assert.equal(invoked, false);
  assert.equal(state.busy.value, false);
});

test("explicit demo mode is isolated from server records and remote requests", async () => {
  const { state, calls, server } = await fixture();
  await state.setMode("demo");
  const count = calls.length;
  await state.login("demo", "demo1234");
  await state.saveBody({ date: today(), weight: 81 });
  assert.equal(state.mode.value, "demo");
  assert.equal(calls.length, count);
  assert.equal(state.data.value.body.find((entry) => entry.date === today()).weight, 81);
  assert.deepEqual(server.accounts.get("alice").body, []);
  await state.setMode("remote");
  await state.login("alice", "password-not-used-by-fake");
  assert.deepEqual(state.data.value.body, []);
  assert.equal(state.foods.value[0].id, "server-food");
});

test("an old loadRange cannot merge private records after logout and a new login", async () => {
  const { state, remote } = await fixture();
  const delayed = deferred();
  remote.loadRange = () => delayed.promise;
  const pending = state.loadRange(today(), today());
  const rejected = assert.rejects(pending, /账户已切换/);
  await state.logout();
  await state.login("bob", "unused");
  delayed.resolve({ plans: [], workouts: [], diet: [], body: [{ id: "old-alice", date: today(), weight: 99 }] });
  await rejected;
  assert.equal(state.user.value.username, "bob");
  assert.equal(state.data.value.profile.nickname, "bob");
  assert.deepEqual(state.data.value.body, []);
  assert.equal(state.loading.value, false);
});

test("a read begun before a mutation cannot overwrite the mutation's reread", async () => {
  const { state, remote } = await fixture();
  const delayed = deferred(), originalLoad = remote.loadRange;
  let first = true;
  remote.loadRange = (...args) => {
    if (first) { first = false; return delayed.promise; }
    return originalLoad(...args);
  };
  const pending = state.loadRange(today(), today());
  await state.saveBody({ date: today(), weight: 75 });
  delayed.resolve({ plans: [], workouts: [], diet: [], body: [{ id: "stale", date: today(), weight: 1 }] });
  await pending;
  assert.equal(state.data.value.body[0].weight, 75);
  assert.equal(state.data.value.body[0].id, "server-body-alice");
});

test("logout clears private state immediately even when remote logout later fails", async () => {
  const { state, remote } = await fixture();
  const delayed = deferred();
  remote.logout = () => delayed.promise;
  const pending = state.logout();
  const rejected = assert.rejects(pending, /offline/);
  assert.equal(state.user.value, null);
  assert.equal(state.data.value, null);
  delayed.reject(new Error("offline"));
  await rejected;
  assert.match(state.error.value, /服务端登出未确认/);
  assert.equal(state.busy.value, false);
  assert.equal(state.mode.value, "remote");
});

test("workout partial success reloads persisted records without submitting a second workout", async () => {
  const { state, remote, server, calls } = await fixture();
  remote.saveWorkout = async (value) => {
    calls.push(["saveWorkout", clone(value)]);
    server.accounts.get("alice").workouts.push({ id: "persisted-workout", date: value.date, planId: value.planId, items: [] });
    throw apiError("训练记录已保存，但计划打卡失败", { partial: true, recordSaved: true, status: 503 });
  };
  await assert.rejects(state.saveWorkout({ date: today(), planId: "plan-alice", completePlan: true, items: [] }),
    (error) => error.partial === true && error.recordSaved === true);
  assert.equal(state.data.value.workouts[0].id, "persisted-workout");
  assert.equal(state.data.value.plans[0].status, "pending");
  assert.equal(calls.filter((entry) => entry[0] === "saveWorkout").length, 1);
  assert.equal(state.busy.value, false);
});

test("partial-success recovery cannot restore private records after logout", async () => {
  const { state, remote } = await fixture();
  const delayed = deferred(), recoveryStarted = deferred();
  remote.saveWorkout = async () => { throw apiError("persisted but status failed", { partial: true, recordSaved: true }); };
  remote.loadRange = () => { recoveryStarted.resolve(); return delayed.promise; };
  const pending = state.saveWorkout({ date: today(), planId: "plan-alice", completePlan: true, items: [] });
  const rejected = assert.rejects(pending, (error) => error.partial === true);
  await recoveryStarted.promise;
  // Logout can cancel a write, while a new login waits until that operation settles.
  await state.logout();
  assert.equal(state.data.value, null);
  await assert.rejects(state.login("bob", "unused"), /正在保存/);
  delayed.resolve({ plans: [], workouts: [{ id: "alice-secret", date: today(), items: [] }], diet: [], body: [] });
  await rejected;
  assert.equal(state.user.value, null);
  assert.equal(state.data.value, null);
  assert.equal(state.busy.value, false);
  await state.login("bob", "unused");
  assert.equal(state.user.value.username, "bob");
  assert.deepEqual(state.data.value.workouts, []);
});
