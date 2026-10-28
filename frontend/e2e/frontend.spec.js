import { test, expect } from "@playwright/test";

async function demo(page) {
  await page.goto("/");
  await page.getByRole("button", { name: "体验示例数据 →" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText(
    "今天也照顾好自己",
  );
}
async function nav(page, path) {
  await page.goto("/#" + path);
}
async function accountData(page, username) {
  return page.evaluate(
    (name) =>
      JSON.parse(
        localStorage.getItem("ftdms.demo.v1.data." + encodeURIComponent(name)),
      ),
    username,
  );
}

test("desktop and 320px navigation render without overflow or runtime errors", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await demo(page);
  await page.getByLabel("查看日期").fill("");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.screenshot({
    path: "test-results/desktop-home.png",
    fullPage: true,
  });
  for (const width of [1280, 375, 320]) {
    await page.setViewportSize({ width, height: 844 });
    for (const path of [
      "/",
      "/training",
      "/diet",
      "/body",
      "/stats",
      "/profile",
    ]) {
      await nav(page, path);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true);
    }
  }
  await nav(page, "/diet");
  await page.getByLabel("实际摄入（g）").fill("-1");
  await page.getByLabel("搜索演示食物").fill("无此食物");
  await expect(
    page.getByText("没有找到食物，请尝试其他关键词。"),
  ).toBeVisible();
  await nav(page, "/stats");
  await page.getByLabel("开始日期").fill("");
  await expect(page.getByRole("alert")).toBeVisible();
  await nav(page, "/");
  await page.screenshot({
    path: "test-results/mobile-home.png",
    fullPage: true,
  });
  expect(errors).toEqual([]);
});

test("new account completes diet, body, training and goal flow with persistence and isolation", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.setViewportSize({ width: 375, height: 844 });
  await page.goto("/");
  await page.getByRole("button", { name: "还没有账户？注册" }).click();
  await page.getByLabel("用户名", { exact: true }).fill("testuser");
  await page.getByLabel("演示密码", { exact: true }).fill("test12345");
  await page.getByLabel("确认密码").fill("test12345");
  await page.getByRole("button", { name: "创建本地演示账户" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText(
    "testuser",
  );
  await nav(page, "/diet");
  await page.getByLabel("选择食物").selectOption("rice");
  await page.getByLabel("实际摄入（g）").fill("150");
  await page.getByRole("button", { name: "添加到早餐" }).click();
  await expect(page.getByRole("status")).toContainText("已保存");
  expect((await accountData(page, "testuser")).diet[0].snapshot.kcal).toBe(174);
  await page.getByRole("button", { name: "修改克重" }).click();
  await page.getByLabel("实际摄入（g）").fill("200");
  await page.getByRole("button", { name: "保存修改" }).click();
  await expect
    .poll(
      async () => (await accountData(page, "testuser")).diet[0].snapshot.kcal,
    )
    .toBe(232);
  await nav(page, "/body");
  await page.getByLabel("体重（kg）").fill("68.5");
  await page.getByRole("button", { name: "保存体重", exact: true }).click();
  await expect(page.getByRole("status")).toBeVisible();
  await page.getByLabel("体重（kg）").fill("68.2");
  await page.getByRole("button", { name: "更新同日记录" }).click();
  await expect
    .poll(async () => (await accountData(page, "testuser")).body[0].weight)
    .toBe(68.2);
  expect((await accountData(page, "testuser")).body).toHaveLength(1);
  await nav(page, "/training");
  await page.getByRole("button", { name: "＋ 新建计划" }).click();
  await page.getByLabel("计划名称").fill("测试下肢计划");
  await page.getByRole("button", { name: "保存计划", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("计划已创建");
  await page.getByRole("button", { name: "记录并完成", exact: true }).click();
  await expect(page.getByLabel("实际重量 / kg")).toHaveValue("");
  await page.getByLabel("实际重量 / kg").fill("20");
  await page.getByLabel("实际次数", { exact: true }).fill("8");
  await page.getByRole("button", { name: "保存记录并完成计划" }).click();
  await expect
    .poll(async () => (await accountData(page, "testuser")).plans[0].status)
    .toBe("completed");
  const d = await accountData(page, "testuser");
  expect(d.workouts[0].items[0].sets).toEqual([{ weight: 20, reps: 8 }]);
  await nav(page, "/profile");
  await page.getByLabel("昵称").fill("记录者");
  await page.getByLabel("身高（cm）").fill("175");
  await page.getByRole("button", { name: "保存资料" }).click();
  await expect(page.getByRole("status")).toContainText("个人资料已保存");
  await page.getByLabel("生效日期", { exact: true }).fill("2027-01-01");
  await page.getByLabel("能量（kcal）").fill("2300");
  await page.getByRole("button", { name: "保存生效日期目标" }).click();
  await expect
    .poll(async () => (await accountData(page, "testuser")).goals.length)
    .toBe(2);
  await page.reload();
  await expect(page.getByLabel("昵称")).toHaveValue("记录者");
  await page.getByRole("button", { name: "退出登录" }).click();
  await page.getByRole("button", { name: "体验示例数据 →" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText(
    "训练伙伴",
  );
  expect(
    (await accountData(page, "demo")).diet.some((x) => x.id === d.diet[0].id),
  ).toBe(false);
  expect(errors).toEqual([]);
});

test("failed local save keeps old data and shows retryable error", async ({
  page,
}) => {
  await demo(page);
  await nav(page, "/body");
  const before = await accountData(page, "demo");
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.includes(".data."))
        throw new DOMException("quota", "QuotaExceededError");
      return original.call(this, key, value);
    };
  });
  await page.getByLabel("体重（kg）").fill("80");
  await page.getByRole("button", { name: "更新同日记录" }).click();
  await expect(page.getByRole("alert")).toContainText("本地保存失败");
  expect(await accountData(page, "demo")).toEqual(before);
  await expect(page.getByLabel("体重（kg）")).toHaveValue("80");
});
