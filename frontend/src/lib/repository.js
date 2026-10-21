import { dateRange, nutrition, today } from "./domain.js";

// Illustrative per-100g estimates, not a medical or authoritative food database.
export const foods = [
  {
    id: "rice",
    name: "米饭",
    state: "熟",
    source: "演示估算（每100g）",
    kcal: 116,
    protein: 2.6,
    carbs: 25.9,
    fat: 0.3,
  },
  {
    id: "chicken",
    name: "鸡胸肉",
    state: "熟",
    source: "演示估算（每100g）",
    kcal: 165,
    protein: 31,
    carbs: 0,
    fat: 3.6,
  },
  {
    id: "egg",
    name: "鸡蛋",
    state: "熟",
    source: "演示估算（每100g）",
    kcal: 144,
    protein: 13.3,
    carbs: 2.8,
    fat: 8.8,
  },
  {
    id: "oats",
    name: "燕麦片",
    state: "干",
    source: "演示估算（每100g）",
    kcal: 377,
    protein: 15,
    carbs: 66.9,
    fat: 6.7,
  },
  {
    id: "milk",
    name: "纯牛奶",
    state: "即饮",
    source: "演示估算（每100g）",
    kcal: 61,
    protein: 3.2,
    carbs: 4.8,
    fat: 3.3,
  },
  {
    id: "banana",
    name: "香蕉",
    state: "生（可食部）",
    source: "演示估算（每100g）",
    kcal: 93,
    protein: 1.4,
    carbs: 22,
    fat: 0.2,
  },
  {
    id: "broccoli",
    name: "西兰花",
    state: "熟",
    source: "演示估算（每100g）",
    kcal: 35,
    protein: 2.4,
    carbs: 7.2,
    fat: 0.4,
  },
  {
    id: "beef",
    name: "瘦牛肉",
    state: "熟",
    source: "演示估算（每100g）",
    kcal: 200,
    protein: 29,
    carbs: 0,
    fat: 9,
  },
  {
    id: "tofu",
    name: "北豆腐",
    state: "生",
    source: "演示估算（每100g）",
    kcal: 98,
    protein: 12.2,
    carbs: 2.1,
    fat: 4.8,
  },
  {
    id: "apple",
    name: "苹果",
    state: "生（可食部）",
    source: "演示估算（每100g）",
    kcal: 53,
    protein: 0.4,
    carbs: 13.7,
    fat: 0.2,
  },
];

export const exercises = [
  { id: "squat", name: "杠铃深蹲", muscle: "腿部" },
  { id: "bench", name: "杠铃卧推", muscle: "胸部" },
  { id: "deadlift", name: "硬拉", muscle: "背部 / 臀腿" },
  { id: "row", name: "哑铃划船", muscle: "背部" },
  { id: "press", name: "哑铃肩推", muscle: "肩部" },
  { id: "pulldown", name: "高位下拉", muscle: "背部" },
  { id: "curl", name: "哑铃弯举", muscle: "手臂" },
  { id: "pushup", name: "俯卧撑", muscle: "胸部 / 手臂" },
];

const PREFIX = "ftdms.demo.v1.";
const ACCOUNTS = `${PREFIX}accounts`;
const SESSION = `${PREFIX}session`;
const ITERATIONS = 210000;
const clone = (value) => JSON.parse(JSON.stringify(value));
const dataKey = (username) => `${PREFIX}data.${encodeURIComponent(username)}`;

function emptyData(username) {
  return {
    profile: { nickname: username, height: null, birthday: "" },
    goals: [
      {
        id: "goal-default",
        effectiveDate: today(),
        kcal: 2000,
        protein: 120,
        carbs: 250,
        fat: 60,
      },
    ],
    plans: [],
    workouts: [],
    diet: [],
    body: [],
  };
}

function demoData() {
  const dates = dateRange(today(), 14);
  const data = emptyData("训练伙伴");
  data.profile = { nickname: "训练伙伴", height: 175, birthday: "2002-05-18" };
  data.goals[0].effectiveDate = dates[0];
  for (const [index, title, status, exerciseId] of [
    [2, "上肢力量", "completed", "bench"],
    [5, "下肢力量", "completed", "squat"],
    [8, "背部训练", "pending", "row"],
    [10, "调整休息", "cancelled", "press"],
    [12, "全身力量", "completed", "deadlift"],
    [13, "今日上肢计划", "pending", "bench"],
  ]) {
    const id = `demo-plan-${index}`;
    data.plans.push({
      id,
      date: dates[index],
      title,
      status,
      items: [{ exerciseId, sets: 3, reps: 10, weight: 30 }],
    });
    if (status === "completed")
      data.workouts.push({
        id: `demo-workout-${index}`,
        date: dates[index],
        planId: id,
        items: [
          {
            exerciseId,
            sets: [
              { weight: 30, reps: 10 },
              { weight: 30, reps: 10 },
              { weight: 30, reps: 8 },
            ],
          },
        ],
      });
  }
  data.workouts.push({
    id: "demo-temporary",
    date: dates[11],
    planId: null,
    items: [{ exerciseId: "pushup", sets: [{ weight: 0, reps: 15 }] }],
  });
  // Deliberate gaps on days 1, 4, 7, 10: absence must not become zero on charts.
  for (const index of [0, 2, 3, 5, 6, 8, 9, 11, 12, 13]) {
    for (const [meal, foodId, grams] of [
      ["早餐", "oats", 60],
      ["早餐", "milk", 250],
      ["午餐", "rice", 220],
      ["午餐", "chicken", 160],
      ["晚餐", "rice", 180],
      ["晚餐", "beef", 140],
      ["晚餐", "broccoli", 200],
      ["加餐", "banana", 120 + index * 3],
    ]) {
      const food = foods.find((item) => item.id === foodId);
      data.diet.push({
        id: `demo-diet-${index}-${data.diet.length}`,
        date: dates[index],
        meal,
        foodId,
        foodName: food.name,
        grams,
        snapshot: nutrition(food, grams),
        source: food.source,
        state: food.state,
      });
    }
  }
  for (const [index, weight] of [
    [0, 72.6],
    [3, 72.3],
    [5, 72.4],
    [8, 72.1],
    [11, 72],
    [13, 71.9],
  ]) {
    data.body.push({ id: `demo-body-${index}`, date: dates[index], weight });
  }
  return data;
}

function cryptoAPI() {
  if (!globalThis.crypto?.subtle || !globalThis.crypto?.getRandomValues) {
    throw new Error(
      "当前环境不支持 WebCrypto，请使用 localhost 或 HTTPS；不会降级保存明文口令",
    );
  }
  return globalThis.crypto;
}
const hex = (bytes) =>
  Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
const unhex = (text) =>
  Uint8Array.from(text.match(/.{2}/g) ?? [], (item) => parseInt(item, 16));

async function passwordHash(password, salt, iterations) {
  const crypto = cryptoAPI();
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: unhex(salt), iterations },
    key,
    256,
  );
  return hex(new Uint8Array(bits));
}

function credentials(username, password) {
  if (
    typeof username !== "string" ||
    !/^[\p{L}\p{N}_-]{2,32}$/u.test(username.trim())
  ) {
    throw new Error("用户名须为 2–32 位文字、数字、下划线或短横线");
  }
  if (
    typeof password !== "string" ||
    password.length < 8 ||
    password.length > 128
  ) {
    throw new Error("演示密码须为 8–128 位，请勿使用真实密码");
  }
  return username.trim();
}

function validateData(data) {
  if (
    !data ||
    typeof data !== "object" ||
    !data.profile ||
    ["goals", "plans", "workouts", "diet", "body"].some(
      (key) => !Array.isArray(data[key]),
    )
  ) {
    throw new Error("本地数据格式无效，请勿覆盖原有记录");
  }
  const dates = new Set();
  for (const record of data.body) {
    if (!record || !record.date || dates.has(record.date))
      throw new Error("同一天只能保存一条体重记录");
    dates.add(record.date);
  }
}

/**
 * Local-only demonstration, NOT an authentication/security boundary.
 * PBKDF2 avoids plaintext password storage; users can still edit localStorage.
 * User records are unencrypted and never sent to api by this adapter.
 */
export function createRepository(storage = globalThis.localStorage) {
  function read(key, fallback = null) {
    let raw;
    try {
      raw = storage.getItem(key);
    } catch {
      throw new Error("无法读取本地存储，请检查浏览器隐私设置");
    }
    if (raw == null) return fallback;
    try {
      return JSON.parse(raw);
    } catch {
      throw new Error("本地数据损坏，未自动重置，请先备份数据");
    }
  }
  function write(key, value) {
    try {
      storage.setItem(key, JSON.stringify(value));
    } catch {
      throw new Error("本地保存失败（空间不足或存储被禁用），数据尚未保存");
    }
  }
  function accounts() {
    const value = read(ACCOUNTS, {});
    if (!value || typeof value !== "object" || Array.isArray(value))
      throw new Error("本地账户索引损坏");
    return value;
  }
  function session() {
    const value = read(SESSION);
    if (!value) return null;
    if (typeof value.username !== "string") throw new Error("本地会话格式无效");
    return Object.hasOwn(accounts(), value.username)
      ? { username: value.username }
      : null;
  }
  function requireUser() {
    const user = session();
    if (!user) throw new Error("请先登录本地演示账户");
    return user;
  }
  return {
    session,
    async login(username, password) {
      const name = credentials(username, password);
      const current = accounts();
      let account = Object.hasOwn(current, name) ? current[name] : null;
      if (name === "demo" && !account) {
        // Public demonstration credentials are deliberately not a real identity check.
        if (password !== "demo1234") throw new Error("用户名或密码错误");
        const salt = hex(cryptoAPI().getRandomValues(new Uint8Array(16)));
        account = {
          username: name,
          salt,
          iterations: ITERATIONS,
          hash: await passwordHash(password, salt, ITERATIONS),
        };
        if (read(dataKey(name)) == null) write(dataKey(name), demoData());
        write(ACCOUNTS, { ...accounts(), [name]: account });
      } else {
        if (
          !account ||
          typeof account.salt !== "string" ||
          typeof account.hash !== "string" ||
          account.iterations !== ITERATIONS
        ) {
          throw new Error("用户名或密码错误");
        }
        const actual = await passwordHash(
          password,
          account.salt,
          account.iterations,
        );
        // Fixed-length comparison, without claiming browser-side authentication security.
        let difference = actual.length ^ account.hash.length;
        for (let index = 0; index < actual.length; index++)
          difference |=
            actual.charCodeAt(index) ^ account.hash.charCodeAt(index);
        if (difference !== 0) throw new Error("用户名或密码错误");
      }
      write(SESSION, { username: name });
      return { username: name };
    },
    async register(username, password) {
      const name = credentials(username, password);
      if (name.toLowerCase() === "demo")
        throw new Error("demo 为保留的演示账户");
      if (Object.hasOwn(accounts(), name))
        throw new Error("此本地用户名已存在");
      const salt = hex(cryptoAPI().getRandomValues(new Uint8Array(16)));
      const account = {
        username: name,
        salt,
        iterations: ITERATIONS,
        hash: await passwordHash(password, salt, ITERATIONS),
      };
      // Recheck after asynchronous derivation; do not overwrite an existing account.
      if (Object.hasOwn(accounts(), name))
        throw new Error("此本地用户名已存在");
      write(dataKey(name), emptyData(name));
      write(ACCOUNTS, { ...accounts(), [name]: account });
      write(SESSION, { username: name });
      return { username: name };
    },
    logout() {
      try {
        storage.removeItem(SESSION);
      } catch {
        throw new Error("无法清除本地会话，请检查浏览器存储设置");
      }
    },
    load() {
      const { username } = requireUser();
      const data = read(dataKey(username));
      validateData(data);
      return clone(data);
    },
    save(data) {
      const { username } = requireUser();
      validateData(data);
      write(dataKey(username), clone(data));
    },
  };
}
