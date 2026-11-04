import { test, expect } from "@playwright/test";
import { today, dateRange } from "../src/lib/domain.js";

const live = process.env.FTDMS_LIVE_API === "1";
const password = "Integration123";
const uniqueName = (suffix = "") => `e2e${Date.now().toString(36)}${suffix}`;
const nav = (page, path) => page.goto(`/\#${path}`);
async function register(page, username) {
  await page.goto("/");
  const toggle = page.getByRole("button", { name: "还没有账户？注册", exact: true });
  if (await toggle.isVisible()) await toggle.click();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码").fill(password);
  await page.getByRole("button", { name: "注册并登录", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText(username);
  await expect(page.getByText("真实后端模式 · JWT 认证 · 数据由服务器保存", { exact: true })).toBeVisible();
}
async function session(page) {
  return page.evaluate(() => JSON.parse(sessionStorage.getItem("ftdms.remote.v1.session")));
}
async function api(page, path, token) {
  const current = token || (await session(page)).token;
  const response = await page.request.get(`/api/v1${path}`, { headers: { Authorization: `Bearer ${current}` } });
  return { status: response.status(), ...(await response.json()) };
}

// Normal core flows below use the actual Spring/H2 service through the Vite proxy.
// Only the final failure scenarios deliberately inject failed responses.
test.describe("real backend integration", () => {
  test.skip(!live, "Set FTDMS_LIVE_API=1 and start backend on :8080 (or FTDMS_API_TARGET)");
  test.setTimeout(60000);

  test("register, diet CRUD, body upsert, training, stats, profile and server persistence/isolation", async ({ page }) => {
    const errors = [];
    page.on("pageerror", (cause) => errors.push(cause.message));
    await page.setViewportSize({ width: 375, height: 844 });
    const username = uniqueName("a");
    await register(page, username);
    const firstSession = await session(page);
    const date = today();
    await nav(page, "/diet");
    await expect(page.getByRole("button", { name: "添加到早餐" })).toBeEnabled();
    const foodId = await page.getByLabel("选择食物").inputValue();
    const food = (await api(page, `/foods/${foodId}`)).data;
    await page.getByLabel("实际摄入（g）").fill("150");
    await page.getByRole("button", { name: "添加到早餐" }).click();
    await expect(page.getByRole("status")).toContainText("饮食记录已保存");
    let records = (await api(page, `/diets?date=${date}`)).data;
    expect(records).toHaveLength(1);
    expect(records[0].snapshotKcal).toBeCloseTo(Number(food.kcalPer100g) * 1.5, 2);
    const dietId = records[0].id;
    await page.getByRole("button", { name: "修改克重" }).click();
    await page.getByLabel("实际摄入（g）").fill("200");
    await page.getByRole("button", { name: "保存修改", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("饮食记录已保存");
    records = (await api(page, `/diets?date=${date}`)).data;
    expect(records[0].snapshotKcal).toBeCloseTo(Number(food.kcalPer100g) * 2, 2);
    await page.reload();
    await expect(page.getByText("200 g", { exact: false })).toBeVisible();
    expect(await page.evaluate(() => Object.keys(localStorage).filter((key) => key.startsWith("ftdms.demo.v1.data.")))).toEqual([]);

    await nav(page, "/body");
    await expect(page.getByRole("button", { name: "保存体重", exact: true })).toBeEnabled();
    await page.getByLabel("体重（kg）").fill("68.5");
    await page.getByRole("button", { name: "保存体重", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("体重已保存");
    await page.getByLabel("体重（kg）").fill("68.2");
    await page.getByRole("button", { name: "更新同日记录" }).click();
    await expect(page.getByRole("status")).toContainText("体重已保存");
    const body = (await api(page, `/body-records?from=${date}&to=${date}`)).data;
    expect(body.list).toHaveLength(1);
    expect(body.list[0].weightKg).toBe(68.2);

    await nav(page, "/training");
    await page.getByRole("button", { name: "＋ 新建计划" }).click();
    await page.getByLabel("计划名称").fill("真实联调力量计划");
    await page.getByRole("button", { name: "保存计划", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("训练计划已创建");
    await page.getByRole("button", { name: "记录并完成", exact: true }).click();
    await page.getByLabel("实际重量 / kg").fill("20");
    await page.getByLabel("实际次数", { exact: true }).fill("8");
    await page.getByRole("button", { name: "保存记录并完成计划" }).click();
    await expect(page.getByRole("status")).toContainText("实际记录已保存，关联计划已完成");
    const plans = (await api(page, `/plans?from=${date}&to=${date}`)).data;
    const workouts = (await api(page, `/workouts?from=${date}&to=${date}`)).data;
    expect(plans[0].status).toBe("COMPLETED");
    expect(workouts).toHaveLength(1);
    expect(workouts[0].sets[0]).toMatchObject({ weightKg: 20, reps: 8 });
    await nav(page, "/stats");
    await expect(page.getByText("1 / 1 个未取消计划", { exact: true })).toBeVisible();
    await expect(page.getByText("有效 1 天", { exact: true }).first()).toBeVisible();

    await nav(page, "/profile");
    await page.getByLabel("昵称", { exact: true }).fill("真实联调记录者");
    await page.getByLabel("身高（cm）").fill("175");
    await page.getByLabel("出生日期（可选）").fill("2000-01-02");
    await page.getByRole("button", { name: "保存资料", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("个人资料已保存");
    await page.getByLabel("出生日期（可选）").fill("");
    await page.getByRole("button", { name: "保存资料", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("个人资料已保存");
    expect((await api(page, "/users/me")).data.birthDate ?? null).toBeNull();
    await page.getByLabel("能量（kcal）").fill("2300");
    await page.getByRole("button", { name: "保存生效日期目标" }).click();
    await expect(page.getByRole("status")).toContainText("目标已保存");
    expect((await api(page, "/goals/current")).data.kcal).toBe(2300);
    await page.reload();
    await expect(page.getByLabel("昵称", { exact: true })).toHaveValue("真实联调记录者");
    await page.getByRole("button", { name: "退出登录", exact: true }).click();
    await expect(page.getByRole("button", { name: "登录", exact: true })).toBeVisible();
    expect(await session(page)).toBeNull();
    expect((await api(page, "/users/me", firstSession.token)).code).toBe(1002);

    await register(page, uniqueName("b"));
    expect((await api(page, `/diets?date=${date}`)).data).toEqual([]);
    expect((await api(page, `/body-records?from=${date}&to=${date}`)).data.list).toEqual([]);
    expect((await api(page, `/plans/${plans[0].id}`)).code).toBe(1004);
    const denied = await page.request.delete(`/api/v1/diets/${dietId}`, { headers: { Authorization: `Bearer ${(await session(page)).token}` } });
    expect((await denied.json()).code).toBe(1004);

    // Re-login to A: persisted records survive logout and another account's attempted access.
    await nav(page, "/profile");
    await page.getByRole("button", { name: "退出登录", exact: true }).click();
    await page.getByRole("button", { name: "已有账户？返回登录", exact: true }).click();
    await page.getByLabel("用户名", { exact: true }).fill(username);
    await page.getByLabel("密码", { exact: true }).fill(password);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await expect(page.getByRole("heading", { level: 1 })).toContainText("真实联调记录者");
    await nav(page, "/diet");
    await expect(page.getByRole("button", { name: "删除", exact: true })).toBeEnabled();
    await page.getByRole("button", { name: "删除", exact: true }).click();
    await page.getByRole("button", { name: "确认删除", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("记录已删除");
    expect((await api(page, `/diets?date=${date}`)).data).toEqual([]);
    await nav(page, "/body");
    await expect(page.getByRole("button", { name: "删除", exact: true })).toBeEnabled();
    await page.getByRole("button", { name: "删除", exact: true }).click();
    await page.getByRole("button", { name: "确认删除", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("体重记录已删除");
    expect((await api(page, `/body-records?from=${date}&to=${date}`)).data.list).toEqual([]);
    expect(errors).toEqual([]);
    await page.screenshot({ path: "test-results/live-mobile.png", fullPage: true });
  });

  test("plan edit/cancel, temporary workout, backdated records and range statistics stay consistent", async ({ page }) => {
    await register(page, uniqueName("e"));
    const date = today(), yesterday = dateRange(date, 2)[0];
    await nav(page, "/training");
    await page.getByRole("button", { name: "＋ 新建计划" }).click();
    await page.getByLabel("计划名称").fill("待修改训练计划");
    await page.getByRole("button", { name: "保存计划", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("计划已创建");
    await page.getByRole("button", { name: "编辑", exact: true }).click();
    await page.getByLabel("计划名称").fill("修改后取消计划");
    await page.getByRole("button", { name: "保存计划", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("计划已更新");
    expect((await api(page, `/plans?from=${date}&to=${date}`)).data[0].title).toBe("修改后取消计划");
    await page.getByRole("button", { name: "取消计划", exact: true }).click();
    await page.getByRole("button", { name: "确认取消计划", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("计划已取消");
    let stats = (await api(page, `/stats/workouts?from=${date}&to=${date}`)).data;
    expect(stats).toMatchObject({ plannedCount: 1, cancelledCount: 1, completionRate: null });
    await page.getByRole("button", { name: "＋ 记录训练", exact: true }).click();
    await page.getByLabel("实际重量 / kg").fill("0");
    await page.getByLabel("实际次数", { exact: true }).fill("12");
    await page.getByRole("button", { name: "仅保存实际记录", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("实际记录已保存");
    stats = (await api(page, `/stats/workouts?from=${date}&to=${date}`)).data;
    expect(stats.extraWorkoutCount).toBe(1);
    expect(stats.completionRate).toBeNull();
    await page.getByLabel("选择训练日期").fill(yesterday);
    await expect(page.getByText("今天还没有计划。给自己安排一场刚刚好的训练吧。", { exact: true })).toBeVisible();
    await nav(page, "/diet");
    await page.getByLabel("记录日期", { exact: true }).fill(yesterday);
    await expect(page.getByRole("button", { name: "添加到早餐" })).toBeEnabled();
    await page.getByLabel("实际摄入（g）").fill("100");
    await page.getByRole("button", { name: "添加到早餐" }).click();
    await expect(page.getByRole("status")).toContainText("饮食记录已保存");
    expect((await api(page, `/diets?date=${yesterday}`)).data).toHaveLength(1);
    expect((await api(page, `/diets?date=${date}`)).data).toEqual([]);
    await nav(page, "/stats");
    await expect(page.getByText("暂无计划", { exact: true })).toBeVisible();
    await expect(page.getByText("有效 1 天", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "30 天", exact: true }).click();
    await expect(page.getByText("有效 1 天", { exact: true })).toBeVisible();
    await page.getByLabel("开始日期", { exact: true }).fill(yesterday);
    await page.getByLabel("结束日期", { exact: true }).fill(yesterday);
    await expect(page.getByText("有效 1 天", { exact: true })).toBeVisible();
    await page.setViewportSize({ width: 320, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });

  test("workout saved but completion failed closes the draft and retries only plan status", async ({ page }) => {
    await register(page, uniqueName("f"));
    const date = today();
    await nav(page, "/training");
    await page.getByRole("button", { name: "＋ 新建计划" }).click();
    await page.getByLabel("计划名称").fill("部分成功验证计划");
    await page.getByRole("button", { name: "保存计划", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("计划已创建");
    await page.route("**/api/v1/plans/*/status", (route) => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code: 1005, message: "联调测试：状态暂不可更新", data: null }) }));
    await page.getByRole("button", { name: "记录并完成", exact: true }).click();
    await page.getByLabel("实际重量 / kg").fill("20");
    await page.getByLabel("实际次数", { exact: true }).fill("8");
    await page.getByRole("button", { name: "保存记录并完成计划" }).click();
    await expect(page.getByRole("alert")).toContainText("训练记录已保存，但计划打卡失败");
    await expect(page.getByRole("button", { name: "保存记录并完成计划" })).toHaveCount(0);
    expect((await api(page, `/workouts?from=${date}&to=${date}`)).data).toHaveLength(1);
    expect((await api(page, `/plans?from=${date}&to=${date}`)).data[0].status).toBe("PLANNED");
    await page.unroute("**/api/v1/plans/*/status");
    await page.getByRole("button", { name: "仅标记完成", exact: true }).click();
    await page.getByRole("button", { name: "确认仅完成计划", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("计划已标记完成");
    expect((await api(page, `/workouts?from=${date}&to=${date}`)).data).toHaveLength(1);
    expect((await api(page, `/plans?from=${date}&to=${date}`)).data[0].status).toBe("COMPLETED");
  });

  test("invalid JWT clears private state and returns to real login without demo fallback", async ({ page }) => {
    await register(page, uniqueName("c"));
    await page.evaluate(() => {
      const value = JSON.parse(sessionStorage.getItem("ftdms.remote.v1.session"));
      value.token = "invalid-test-token";
      sessionStorage.setItem("ftdms.remote.v1.session", JSON.stringify(value));
    });
    await page.getByRole("button", { name: "刷新服务数据", exact: true }).click();
    await expect(page.getByRole("button", { name: "注册并登录", exact: true })).toBeVisible();
    await expect(page.getByRole("alert")).toContainText(/登录|令牌|认证/);
    expect(await session(page)).toBeNull();
    expect(await page.evaluate(() => sessionStorage.getItem("ftdms.connection.v1.mode"))).not.toBe("demo");
  });

  test("HTTP-200 business errors and network failure keep drafts and never create local records", async ({ page }) => {
    await register(page, uniqueName("d"));
    await nav(page, "/diet");
    await expect(page.getByRole("button", { name: "添加到早餐" })).toBeEnabled();
    await page.getByLabel("实际摄入（g）").fill("123");
    await page.route("**/api/v1/diets", async (route) => {
      if (route.request().method() === "POST") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code: 1001, message: "联调测试：摄入克重无效", data: null }) });
      return route.continue();
    });
    await page.getByRole("button", { name: "添加到早餐" }).click();
    await expect(page.getByRole("alert")).toContainText("联调测试：摄入克重无效");
    await expect(page.getByLabel("实际摄入（g）")).toHaveValue("123");
    expect((await api(page, `/diets?date=${today()}`)).data).toEqual([]);
    await page.unroute("**/api/v1/diets");
    await page.route("**/api/v1/diets", (route) => route.abort("failed"));
    await page.getByRole("button", { name: "添加到早餐" }).click();
    await expect(page.getByRole("alert")).toContainText("无法连接后端");
    await expect(page.getByLabel("实际摄入（g）")).toHaveValue("123");
    expect(await page.evaluate(() => Object.keys(localStorage).filter((key) => key.startsWith("ftdms.demo.v1.data.")))).toEqual([]);
    await page.unroute("**/api/v1/diets");
    await page.getByRole("button", { name: "添加到早餐" }).click();
    await expect(page.getByRole("status")).toContainText("饮食记录已保存");
    expect((await api(page, `/diets?date=${today()}`)).data).toHaveLength(1);
  });
});
