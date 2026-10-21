import { ref } from "vue";
import { createRepository } from "./repository.js";

// One shared store for all views. App explicitly calls refresh() once on startup.
const data = ref(null);
const user = ref(null);
const ready = ref(false);
const error = ref(null);
const busy = ref(false);
let repository;
let generation = 0;
const repo = () => (repository ??= createRepository());
const clone = (value) => JSON.parse(JSON.stringify(value));

async function operation(work) {
  if (busy.value) throw new Error("正在保存或加载，请稍后再试");
  busy.value = true;
  error.value = null;
  try {
    return await work();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause);
    throw cause;
  } finally {
    busy.value = false;
  }
}

function readCurrent() {
  const currentUser = repo().session();
  const currentData = currentUser ? repo().load() : null;
  user.value = currentUser;
  data.value = currentData;
  ready.value = true;
}

async function refresh() {
  return operation(async () => {
    try {
      readCurrent();
    } catch (cause) {
      // Do not leave another account's records visible after a failed session reload.
      user.value = null;
      data.value = null;
      throw cause;
    } finally {
      ready.value = true;
    }
  });
}

async function authenticate(method, username, password) {
  return operation(async () => {
    const version = generation;
    await repo()[method](username, password);
    if (version !== generation) {
      repo().logout();
      throw new Error("登录操作已取消，请重新登录");
    }
    try {
      readCurrent();
      return user.value;
    } catch (cause) {
      user.value = null;
      data.value = null;
      ready.value = true;
      throw cause;
    }
  });
}

function logout() {
  try {
    repo().logout();
    generation++;
    user.value = null;
    data.value = null;
    error.value = null;
    ready.value = true;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause);
    throw cause;
  }
}

async function commit(mutator) {
  return operation(async () => {
    if (!user.value || !data.value) throw new Error("请先登录本地演示账户");
    if (typeof mutator !== "function")
      throw new TypeError("commit 需要数据修改函数");
    const username = user.value.username;
    const version = generation;
    const next = clone(data.value);
    await mutator(next);
    if (version !== generation || repo().session()?.username !== username) {
      throw new Error("账户已切换，未保存此次修改，请重新加载");
    }
    // save is synchronous and may throw (e.g. quota). Publish only after it succeeds.
    repo().save(next);
    data.value = next;
  });
}

export function useData() {
  return {
    data,
    user,
    ready,
    error,
    busy,
    refresh,
    login: (username, password) => authenticate("login", username, password),
    register: (username, password) =>
      authenticate("register", username, password),
    logout,
    commit,
  };
}
