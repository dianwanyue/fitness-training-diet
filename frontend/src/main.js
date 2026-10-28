import { createApp } from "vue";
import { createRouter, createWebHashHistory } from "vue-router";
import App from "./App.vue";
import "./style.css";
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", component: () => import("./views/HomeView.vue") },
    { path: "/training", component: () => import("./views/TrainingView.vue") },
    { path: "/diet", component: () => import("./views/DietView.vue") },
    { path: "/body", component: () => import("./views/BodyView.vue") },
    { path: "/stats", component: () => import("./views/StatsView.vue") },
    { path: "/profile", component: () => import("./views/ProfileView.vue") },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
  scrollBehavior: () => ({ top: 0 }),
});
createApp(App).use(router).mount("#app");
