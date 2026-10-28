import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { createRepository, foods, exercises } from "../src/lib/repository.js";
import { summarize, today, dateRange } from "../src/lib/domain.js";

if (!globalThis.crypto?.subtle)
  Object.defineProperty(globalThis, "crypto", {
    value: webcrypto,
    configurable: true,
  });

class MemoryStorage {
  entries = new Map();
  failWrites = false;
  getItem(key) {
    return this.entries.get(key) ?? null;
  }
  setItem(key, value) {
    if (this.failWrites) throw new Error("QuotaExceededError");
    this.entries.set(key, String(value));
  }
  removeItem(key) {
    this.entries.delete(key);
  }
  dump() {
    return [...this.entries.values()].join("\n");
  }
}

test("food and exercise libraries expose documented fields", () => {
  assert.ok(foods.length > 0);
  assert.ok(exercises.length > 0);
  assert.equal(new Set(foods.map((food) => food.id)).size, foods.length);
  for (const food of foods) {
    for (const key of ["id", "name", "state", "source"])
      assert.equal(typeof food[key], "string");
    for (const key of ["kcal", "protein", "carbs", "fat"])
      assert.ok(Number.isFinite(food[key]));
  }
  for (const exercise of exercises) {
    for (const key of ["id", "name", "muscle"])
      assert.equal(typeof exercise[key], "string");
  }
});

test("demo login uses relative dates, includes missing days, and persists edits", async () => {
  const storage = new MemoryStorage();
  const repository = createRepository(storage);
  assert.equal(repository.session(), null);
  assert.throws(() => repository.load(), /登录/);
  await assert.rejects(
    repository.login("demo", "wrongpass"),
    /用户名或密码错误/,
  );
  assert.deepEqual(await repository.login("demo", "demo1234"), {
    username: "demo",
  });
  const data = repository.load();
  const dates = dateRange(today(), 14);
  const summary = summarize(data, dates[0], today());
  assert.ok(summary.dietDays > 0 && summary.dietDays < 14);
  assert.ok(summary.weightDays > 0 && summary.weightDays < 14);
  assert.ok(data.plans.some((plan) => plan.date === today()));
  data.profile.nickname = "已保存";
  repository.save(data);
  repository.logout();
  await repository.login("demo", "demo1234");
  assert.equal(repository.load().profile.nickname, "已保存");
  assert.ok(!storage.dump().includes("demo1234"));
});

test("registration starts empty with a default goal; accounts remain isolated across reloads", async () => {
  const storage = new MemoryStorage();
  let repository = createRepository(storage);
  await repository.register("alice", "local-only-A");
  const alice = repository.load();
  assert.equal(alice.profile.nickname, "alice");
  for (const key of ["plans", "workouts", "diet", "body"])
    assert.deepEqual(alice[key], []);
  assert.equal(alice.goals.length, 1);
  assert.equal(alice.goals[0].effectiveDate, today());
  alice.body.push({ id: "alice-body", date: today(), weight: 60 });
  repository.save(alice);
  repository.logout();
  await repository.register("bob", "local-only-B");
  assert.deepEqual(repository.load().body, []);
  assert.deepEqual(repository.session(), { username: "bob" });
  repository = createRepository(storage);
  assert.deepEqual(repository.session(), { username: "bob" });
  await repository.login("alice", "local-only-A");
  assert.equal(repository.load().body[0].weight, 60);
  assert.equal(repository.load().profile.nickname, "alice");
  assert.ok(!storage.dump().includes("local-only-A"));
  assert.ok(!storage.dump().includes("local-only-B"));
  await assert.rejects(
    repository.login("alice", "local-only-B"),
    /用户名或密码错误/,
  );
});

test("PBKDF2 stores independent random salts and 256-bit hashes, never passwords", async () => {
  const storage = new MemoryStorage();
  const repository = createRepository(storage);
  await repository.register("first", "same-demo-pass");
  await repository.register("second", "same-demo-pass");
  const accounts = JSON.parse(storage.entries.get("ftdms.demo.v1.accounts"));
  assert.match(accounts.first.salt, /^[a-f0-9]{32}$/);
  assert.match(accounts.first.hash, /^[a-f0-9]{64}$/);
  assert.equal(accounts.first.iterations, 210000);
  assert.notEqual(accounts.first.salt, accounts.second.salt);
  assert.notEqual(accounts.first.hash, accounts.second.hash);
  assert.ok(!storage.dump().includes("same-demo-pass"));
  assert.equal(accounts.first.password, undefined);
});

test("load returns detached data and save snapshots its input", async () => {
  const repository = createRepository(new MemoryStorage());
  await repository.register("clones", "clones-demo-pass");
  const first = repository.load();
  first.profile.nickname = "not-saved";
  assert.equal(repository.load().profile.nickname, "clones");
  repository.save(first);
  first.profile.nickname = "changed-after-save";
  assert.equal(repository.load().profile.nickname, "not-saved");
});

test("failed storage writes throw and retain the previous persisted data", async () => {
  const storage = new MemoryStorage();
  const repository = createRepository(storage);
  await repository.register("quota", "quota-demo-pass");
  const original = repository.load();
  const edited = repository.load();
  edited.profile.nickname = "must-not-save";
  storage.failWrites = true;
  assert.throws(() => repository.save(edited), /保存失败/);
  assert.deepEqual(repository.load(), original);
  storage.failWrites = false;
  repository.save(edited);
  assert.equal(repository.load().profile.nickname, "must-not-save");
});

test("invalid input, duplicate accounts and duplicate daily weights are rejected", async () => {
  const storage = new MemoryStorage();
  const repository = createRepository(storage);
  await assert.rejects(repository.register("demo", "other-password"), /保留/);
  await assert.rejects(
    repository.register("invalid user", "other-password"),
    /用户名/,
  );
  await assert.rejects(repository.register("valid", "short"), /密码/);
  await repository.register("valid", "other-password");
  await assert.rejects(
    repository.register("valid", "other-password"),
    /已存在/,
  );
  const data = repository.load();
  data.body.push(
    { id: "1", date: today(), weight: 65 },
    { id: "2", date: today(), weight: 64 },
  );
  assert.throws(() => repository.save(data), /一天只能/);
  assert.deepEqual(repository.load().body, []);
  assert.throws(() => repository.save({}), /数据格式/);
});

test("useData commits only after save and always resets busy on rejected mutations/storage", async () => {
  const previous = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  const storage = new MemoryStorage();
  Object.defineProperty(globalThis, "localStorage", {
    value: storage,
    configurable: true,
  });
  const { useData } = await import("../src/lib/useData.js");
  const state = useData();
  try {
    assert.equal(state.ready.value, false);
    await state.refresh();
    assert.equal(state.ready.value, true);
    assert.equal(state.user.value, null);
    await state.register("state-user", "state-demo-pass");
    assert.equal(useData().data, state.data);
    await state.commit((draft) => {
      draft.profile.nickname = "saved";
    });
    const before = state.data.value;
    await assert.rejects(
      state.commit((draft) => {
        draft.profile.nickname = "mutated-only";
        throw new Error("mutator failed");
      }),
      /mutator failed/,
    );
    assert.equal(state.data.value, before);
    assert.equal(state.busy.value, false);
    storage.failWrites = true;
    await assert.rejects(
      state.commit((draft) => {
        draft.profile.nickname = "quota-failed";
      }),
      /保存失败/,
    );
    assert.equal(state.data.value, before);
    assert.equal(state.data.value.profile.nickname, "saved");
    assert.equal(state.busy.value, false);
    assert.match(state.error.value, /保存失败/);
    storage.failWrites = false;
    await state.refresh();
    assert.equal(state.data.value.profile.nickname, "saved");
    let release;
    const pending = state.commit(async (draft) => {
      await new Promise((resolve) => {
        release = resolve;
      });
      draft.profile.nickname = "stale";
    });
    await assert.rejects(
      state.commit(() => {}),
      /正在保存/,
    );
    state.logout();
    release();
    await assert.rejects(pending, /账户已切换/);
    assert.equal(state.user.value, null);
    assert.equal(state.data.value, null);
    assert.equal(state.busy.value, false);
    await state.login("state-user", "state-demo-pass");
    assert.equal(state.data.value.profile.nickname, "saved");
  } finally {
    state.logout();
    if (previous) Object.defineProperty(globalThis, "localStorage", previous);
    else delete globalThis.localStorage;
  }
});

test("corrupt data is reported, never silently replaced by demo data", async () => {
  const storage = new MemoryStorage();
  const repository = createRepository(storage);
  await repository.register("corrupt", "corrupt-demo-pass");
  storage.setItem("ftdms.demo.v1.data.corrupt", "not json");
  assert.throws(() => repository.load(), /损坏/);
  assert.equal(storage.getItem("ftdms.demo.v1.data.corrupt"), "not json");
});
