<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useData } from "./lib/useData.js";
const { user, ready, error, busy, refresh, login, register } = useData();
const router = useRouter();
const mode = ref("login"),
  username = ref(""),
  password = ref(""),
  confirm = ref(""),
  message = ref("");
const links = [
  ["/", "◈", "今日"],
  ["/training", "↗", "训练"],
  ["/diet", "◒", "饮食"],
  ["/stats", "▥", "统计"],
  ["/profile", "○", "我的"],
];
onMounted(() => refresh().catch(() => {}));
async function submit(demo = false) {
  message.value = "";
  if (!demo && mode.value === "register" && password.value !== confirm.value) {
    message.value = "两次密码不一致";
    return;
  }
  try {
    await (mode.value === "register" && !demo ? register : login)(
      demo ? "demo" : username.value.trim(),
      demo ? "demo1234" : password.value,
    );
    password.value = "";
    confirm.value = "";
    await router.replace("/");
  } catch (e) {
    message.value = e.message || "操作失败，请重试";
  }
}
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div v-if="!ready" class="boot" role="status">正在准备你的记录空间…</div>
  <template v-else-if="!user">
    <main id="main" class="auth-layout">
      <section class="auth-story">
        <div class="brand-mark">循</div>
        <p class="eyebrow">FTDMS / 每一天，都算数</p>
        <h1>循着自己的节奏，<br />练好每一天。</h1>
        <p>把训练、饮食与身体变化放在一起。<br />不必完美，记录就是开始。</p>
        <div class="story-orbit" aria-hidden="true">
          <span>MOVE</span><b>一点一滴<br />看见改变</b
          ><span>NOURISH · REPEAT</span>
        </div>
        <small>G13 · 健身训练与饮食管理</small>
      </section>
      <section class="auth-panel">
        <div class="badge">本地模拟演示 · 非真实服务</div>
        <h2>{{ mode === "login" ? "欢迎回来" : "开始你的记录" }}</h2>
        <p class="muted">
          数据仅保存在当前浏览器，请勿使用真实密码或敏感信息。
        </p>
        <form class="stack" @submit.prevent="submit()">
          <label class="field"
            >用户名<input
              v-model="username"
              autocomplete="username"
              required
              minlength="3"
              maxlength="32"
              placeholder="3–32 位用户名"
          /></label>
          <label class="field"
            >演示密码<input
              v-model="password"
              type="password"
              :autocomplete="
                mode === 'login' ? 'current-password' : 'new-password'
              "
              required
              minlength="8"
              maxlength="128"
              placeholder="至少 8 位，仅用于本地演示"
          /></label>
          <label v-if="mode === 'register'" class="field"
            >确认密码<input
              v-model="confirm"
              type="password"
              autocomplete="new-password"
              required
              minlength="8"
          /></label>
          <p v-if="message || error" class="error" role="alert">
            {{ message || error }}
          </p>
          <button class="button" :disabled="busy">
            {{
              busy ? "处理中…" : mode === "login" ? "登录" : "创建本地演示账户"
            }}
          </button>
        </form>
        <button
          class="button ghost full"
          :disabled="busy"
          @click="
            mode = mode === 'login' ? 'register' : 'login';
            message = '';
          "
        >
          {{ mode === "login" ? "还没有账户？注册" : "已有账户？返回登录" }}
        </button>
        <div class="divider">或者先了解一下</div>
        <button
          class="button secondary full"
          :disabled="busy"
          @click="submit(true)"
        >
          体验示例数据 →
        </button>
        <p class="caption">
          演示账号 demo /
          demo1234。账户和健康记录未上传服务器，本地存储并不等于安全认证。
        </p>
        <button
          v-if="error"
          class="button ghost"
          @click="refresh().catch(() => {})"
        >
          重新读取本地数据
        </button>
      </section>
    </main>
  </template>
  <div v-else class="app-layout">
    <aside class="sidebar">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">循</span
        ><span>循练<small>TRAIN · EAT · REPEAT</small></span></RouterLink
      >
      <nav aria-label="主导航">
        <RouterLink
          v-for="[path, icon, name] in links"
          :key="path"
          :to="path"
          :class="{ active: $route.path === path }"
          ><span aria-hidden="true">{{ icon }}</span
          >{{ name }}</RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <b>小小坚持，慢慢发生。</b>
        <p>记录事实，而不是评价自己。</p>
        <span>FTDMS · 前端预览版</span>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <span>你的健康记录空间</span
        ><RouterLink to="/profile"
          >{{ user.username }} <span aria-hidden="true">↗</span></RouterLink
        >
      </header>
      <div class="demo-strip">
        模拟模式 · 仅本机保存 · 后端未连接，请勿录入敏感数据
      </div>
      <main id="main" tabindex="-1"><RouterView /></main>
      <footer class="disclaimer">
        循练仅用于个人记录，营养数据为演示估算，不构成医疗建议。
      </footer>
    </div>
    <nav class="bottom-nav" aria-label="移动端导航">
      <RouterLink
        v-for="[path, icon, name] in links"
        :key="path"
        :to="path"
        :class="{ active: $route.path === path }"
        ><span aria-hidden="true">{{ icon }}</span
        >{{ name }}</RouterLink
      >
    </nav>
  </div>
</template>
