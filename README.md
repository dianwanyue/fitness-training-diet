# 健身训练与饮食管理系统

基于 Spring Boot + Vue 3 的健身训练与饮食管理移动应用，支持训练计划与记录、饮食摄入换算、体重趋势统计，以及经用户授权的一键 AI 健身分析；移动端通过 Capacitor 封装为 Android App。

- **课题名称**：基于 SpringBoot+Vue 的健身训练与饮食管理移动应用设计与实现
- **课程 / 组别**：软件工程 / G13
- **小组成员**：方文锦、陈智杰
- **计划周期**：2026-09-28 ～ 2026-12-20（共 12 周）

---

## 一、项目简介

面向有日常健身与饮食记录需求的用户，解决训练动作、组数、饮食重量与体重分散记录的问题，在手机上提供**制定计划 → 记录执行 → 查看统计 → 分析情况 → 调整安排**的完整流程。

**核心模块**

| 模块 | 功能 |
| --- | --- |
| 账户与目标 | 注册登录、个人资料、每日能量与三大营养素目标（按生效日期保留历史） |
| 训练管理 | 创建日期计划、选择动作、记录每组重量与次数、完成打卡、临时训练 |
| 饮食管理 | 搜索食物、按餐次录入克重、汇总能量与三大营养素 |
| 身体数据 | 按日期记录体重，展示区间变化与七日平均 |
| 统计分析 | 训练完成率、每日摄入、体重趋势，图表支持 7 天 / 30 天 / 自定义 |
| 后台管理 | 动作库、公共食物库维护与账户状态管理，管理接口独立鉴权 |
| AI 健身分析 | 授权后自动汇总本人已有数据，一键生成阶段性报告，授权可撤回、报告可删除 |
| 移动端 App | 手机布局与触控适配，Capacitor 封装 Android 安装包 |

**关键业务口径**（开发与验收共同依据，详见 `docs/`）

- 完成率 = 区间内标记完成的计划数 ÷ **未取消**的计划总数；无计划时显示「暂无计划」
- 单项摄入量 = 每 100g 营养值 × 实际克重 ÷ 100；记录保存**营养快照**，食物库后续修改不影响历史汇总
- 同一用户同一天只保留一条体重记录；缺失记录显示为空，**不补零**
- AI 分析默认取最近 30 天（可切 7 天），先按确定规则算出统计值再交模型解释，数值不一致时不作为确定结论

**边界说明**

- 食物营养数据采用标注来源的每 100g 数据，结果属于**估算**
- AI 分析仅提供一般性健身与饮食参考，**不作疾病诊断或治疗建议**
- 首版不纳入食物图片识别、穿戴设备接入、社交社区、动处方式推荐
- 本次移动端交付目标为 **Android 安装包**；iOS 适配作为条件允许时的后续扩展，不计入 12 周强制交付

---

## 二、技术栈

| 层次 | 技术 | 版本 |
| --- | --- | --- |
| 运行环境 | JDK | Java 21 LTS |
| 运行环境 | Node.js | Node 20 LTS |
| 业务层 | Spring Boot | 3.3.x |
| 业务层 | Spring Security | 随 Spring Boot 3.3.x 依赖 |
| 持久层 | Spring Data JPA | 随 Spring Boot 3.3.x 依赖 |
| 持久层 | MySQL | 8.4.x |
| 表现层 | Vue 3 / Vue Router / Axios | 3.5.x |
| 图表层 | Apache ECharts | 5.5.x |
| AI 服务层 | Ollama | 稳定版（版本回填见 `docs/`） |
| AI 服务层 | Qwen3-4B-Instruct-2507 | Apache-2.0，模型版本随模型卡核对 |
| 移动端 | Capacitor CLI | 7.x |
| 移动端 | Android SDK | API 34（按真机环境确认） |
| 协作与构建 | Git / GitHub / Maven / Vite | 稳定版 |

> 手机端不直接连接数据库或模型服务：Vue 页面经 HTTPS 调用 Spring Boot 的 JSON 接口，后端统一处理身份校验、业务规则、统计计算与 AI 请求。

---

## 三、目录结构

```
.
├── backend/            # Spring Boot 后端工程（Maven）
├── frontend/           # Vue 3 + Capacitor 前端工程（Vite）
├── docs/               # 需求说明书、接口文档、schema.sql、原型
├── README.md
├── CONTRIBUTING.md
└── .gitignore
```

---

## 四、启动步骤

### 4.1 准备数据库

```bash
mysql -u root -p
CREATE DATABASE IF NOT EXISTS fitness_db
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

导入建表脚本（12 张表，含 `ai_consent`、`ai_report`）：

```bash
mysql -u root -p fitness_db < docs/schema.sql
```

创建应用账号（**勿使用示例口令**，且不要提交真实口令到仓库）：

```sql
CREATE USER 'fitness_app'@'%' IDENTIFIED BY '<强口令>';
GRANT SELECT, INSERT, UPDATE, DELETE ON fitness_db.* TO 'fitness_app'@'%';
```

> AI 服务账号不拥有数据库直连权限，仅后端服务访问数据库。

### 4.2 启动后端

复制配置模板并填入本机参数：

```bash
cd backend
cp src/main/resources/application-example.yml src/main/resources/application.yml
# 编辑 application.yml：数据库连接、端口、AI 服务地址
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

服务默认 `http://localhost:8080`，接口前缀 `/api`。

### 4.3 启动 AI 服务（AI 模块开发阶段需要）

```bash
ollama pull qwen3:4b-instruct-2507      # 模型名以实际模型卡为准
ollama serve
```

部署前核对模型版本与 Apache-2.0 许可。模型不可用或请求超时时，系统保留原始统计并提示稍后重试，**不编造报告**。

### 4.4 启动前端

```bash
cd frontend
npm install
cp .env.example .env    # 填入后端接口地址
npm run dev
```

### 4.5 构建 Android 安装包

```bash
cd frontend
npm run build
npx cap sync android
npx cap open android    # 在 Android Studio 中打包 / 真机调试
```

> 需本地安装 Android SDK（API 34）；iOS 构建需要 macOS 环境，本次不作交付目标。

---

## 五、文档

| 文档 | 说明 |
| --- | --- |
| `docs/第一阶段_需求与技术方案说明书.docx` | 需求清单、业务规则、版本锁定、表结构、接口清单与 M1 自查表 |
| `docs/schema.sql` | MySQL 8.4 建表脚本 |
| `docs/prototype.html` | 手机端线框原型（11 个页面，浏览器直接打开） |

---

## 六、进度

| 里程碑 | 计划日期 | 内容 |
| --- | --- | --- |
| M1 | 2026-10-11 | 需求与技术方案冻结 |
| M2 | 2026-10-25 | 后端核心接口可联调 |
| M3 | 2026-11-08 | Web 端全流程可用 |
| M4 | 2026-11-29 | AI 分析全链路打通 |
| M5 | 2026-12-06 | Android 安装包可用 |
| M6 | 2026-12-13 | 测试验收通过 |
| M7 | 2026-12-20 | 结项答辩完成 |

---

## 七、参考资料

1. Broadcom. Spring Boot 官方文档. https://docs.spring.io/spring-boot/index.html
2. Vue.js. Vue 3 中文文档简介. https://cn.vuejs.org/guide/introduction
3. Oracle. MySQL 8.4 Reference Manual. https://dev.mysql.com/doc/refman/8.4/en/
4. Apache Software Foundation. Apache ECharts Handbook. https://echarts.apache.org/handbook/en/get-started/
5. Ionic. Capacitor Documentation. https://capacitorjs.com/docs
6. Qwen. Qwen3-4B-Instruct-2507 Model Card. https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507
7. Ollama. Chat API Documentation. https://docs.ollama.com/api/chat

食物营养数据在实际录入时另行登记包装标签或数据来源，不以软件技术资料作为营养数据依据。
