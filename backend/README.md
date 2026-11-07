# FTDMS 后端服务（数据层与后端核心）

健身训练与饮食管理系统（FTDMS）的后端实现，Spring Boot 3.3 + Spring Data JPA + Spring Security(JWT) + MySQL 5.7+（验收环境 8.4）。
本目录交付**数据层与后端核心**：账户鉴权、每日目标、训练计划与记录、饮食记录、身体数据、统计分析、后台管理。
AI 分析接口（G 族）按 SDP 安排在 W7 实现，本次不包含（数据表与实体已就位）。

## 快速开始

### 方式一：零依赖试跑（内置 H2，适合先看效果）

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

启动后访问 <http://localhost:8080/api/v1/exercises> 应返回 401（未带令牌即为正常）。
H2 数据文件位于 `backend/data/ftdms.mv.db`，删除该目录即可重置。演示管理员 `admin / Admin@12345`。

### 方式二：连接 MySQL（正式运行）

```bash
# 1) 准备数据库（任选其一）
docker compose up -d                       # 自动建库、建表、灌入种子数据
# 或手工：
mysql -uroot -p -e "CREATE DATABASE fitness_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p -e "CREATE USER 'fitness_app'@'%' IDENTIFIED BY 'fitness_app_pwd'; GRANT ALL ON fitness_db.* TO 'fitness_app'@'%';"

# 2) 启动（表结构与种子数据由应用自动初始化）
mvn spring-boot:run
```

默认连接 `127.0.0.1:3306/fitness_db`，账号 `fitness_app / fitness_app_pwd`。建表脚本使用 `utf8mb4_unicode_ci`，MySQL 5.7 与 8.x 均可直接执行（已在 5.7.44 上验证）。
可用环境变量覆盖：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、`SERVER_PORT`。

### 方式三：打包后运行

```bash
mvn clean package            # 产物 target/ftdms-backend-1.0.0.jar
java -jar target/ftdms-backend-1.0.0.jar
```

## 目录结构

```text
backend/
├── pom.xml                        # 依赖与构建配置
├── API_CONTRACT.md                # 实际 Controller/DTO 联调契约
├── docker-compose.yml             # MySQL 8.4 一键起库（含建表脚本挂载）
├── 技术说明.docx                   # 交付文档：结构、部署、配置、调试
├── test/schema.sql                # M1 冻结的建表基线（历史留档，不参与运行）
└── src/
    ├── main/
    │   ├── java/com/g13/ftdms/
    │   │   ├── FtdmsApplication.java  # 启动类
    │   │   ├── common/                # 统一响应、错误码、异常、校验、日期、traceId
    │   │   ├── config/                # 安全、序列化、应用属性、启动引导
    │   │   ├── security/              # JWT 签发/解析、认证过滤器、令牌失效名单
    │   │   ├── controller/            # 接口层
    │   │   ├── service/               # 业务层
    │   │   ├── repository/            # 持久层（Spring Data JPA）
    │   │   ├── entity/                # 12 张表的实体映射
    │   │   └── dto/                   # 请求/响应载体（按接口族聚合）
    │   └── resources/                # application*.yml、db/*.sql
    └── test/java/                    # 纯函数测试、真实 Spring/H2 接口集成测试
```

## 接口一览

统一前缀 `/api/v1`，统一响应 `{code, message, data, timestamp, traceId}`。

| 族 | 内容 | 端点 |
| --- | --- | --- |
| A 账户 | 注册、登录、登出、资料、改密 | `/auth/*`、`/users/me*` |
| B 目标 | 当前目标、设置、历史 | `/goals`、`/goals/current`、`/goals/history` |
| C 训练 | 动作库、计划新增/修改/状态、训练记录 | `/exercises`、`/plans*`、`/workouts*` |
| D 饮食 | 食物库、记录 CRUD、当日汇总 | `/foods*`、`/diets*` |
| E 身体 | 体重 upsert、列表、删除 | `/body-records*` |
| F 统计 | 完成率、摄入趋势、体重趋势 | `/stats/*` |
| H 管理 | 动作/食物维护、账户状态 | `/admin/*`（需 ADMIN） |

实际 Controller/DTO 的字段、分页、日期限制、响应封套与计划/训练记录关系见 [联调契约](API_CONTRACT.md)。需求基线另见 `docs/接口文档/健身训练与饮食管理系统_接口文档V1.4.docx`。

> 业务错误通常仍返回 HTTP 200，调用方必须检查 `code`；不要仅依赖 HTTP 状态判断保存成功。注册不返回 token，需另调用登录。

## 测试与构建环境

```bash
mvn test
mvn package
```

集成测试使用独立的内存 H2 数据库 `api-integration`，不清空演示 profile 的文件数据库。测试覆盖真实 JWT、资源归属、提交后重新查询、日期规则、计划与实际记录关系及统计口径；不等价于 MySQL/Android 真机验收。

2026-11-04 在 Windows / IDEA JBR 25.0.4 下执行：**25 项自动化测试通过，0 失败、0 错误、0 跳过**（15 项 `ApiIntegrationTest`、5 项 `DateGuardTest`、5 项 `NutritionCalculatorTest`）。该数字不与历史 README 的55项手工自测混算。

本轮修复包括：修改饮食克重沿用原快照与来源；账户停用后旧 JWT 即时失效；昵称/生日空串清除；缺失摄入 kcal 显式输出 null；未知接口返回1004；训练记录最多补录365天。原快照按两位小数保存，克重比例换算仍有相同精度边界。

推荐 JDK 21；编译目标仍为 Java 17。为兼容 JDK 23+（包括 IDEA JBR 25），已显式配置支持 JDK 25 的 Lombok 1.18.42 注解处理器，不依赖旧版 javac 自动发现行为。

## 约定

- 业务时区固定 `Asia/Shanghai`，日期一律 `yyyy-MM-dd`，时间 `yyyy-MM-dd HH:mm:ss`。
- 缺失数据不补零：无记录返回 `null` / `recorded=false`。
- 用户归属只认令牌，不接受请求里的 userId。
- 统计口径、校验规则与《接口文档》第 12 章、FAR 附录 E 保持一致。

## 更多

部署细节、配置项清单、常见问题排查见 [技术说明.docx](技术说明.docx)。
