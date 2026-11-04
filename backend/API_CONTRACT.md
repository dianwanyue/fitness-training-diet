# 前后端联调契约（按实际 Controller / DTO 核对）

统一前缀 `/api/v1`。本文件描述当前实现；AI G 族尚未实现。

## 通用约定

- 请求/响应 JSON。私有接口带 `Authorization: Bearer <token>`，不传 `userId`。
- 所有接口响应封套 `{code,message,data,timestamp,traceId}`。成功 `code=0`；无数据为 `data:null`。
- **业务错误通常仍为 HTTP 200，必须检查 code**。Security 返回 HTTP 401/code1002、HTTP403/code1003。
- 1001 参数，1002 未认证/令牌失效，1003 无权限，1004 资源不存在或不属于本人，1005 状态冲突，1006 重复，2001 账号/口令错误，2002 账户停用，9000 内部错误。
- DTO 可选字段通常省略 null；`data`、统计空均值、空完成率和空目标明确输出 null。调用方不要把缺失或 null 当零值。
- 日期 `yyyy-MM-dd`；日期时间 **`yyyy-MM-dd HH:mm:ss`（空格，不是 T）**；业务时区 `Asia/Shanghai`。
- 所有 `from/to` 查询含首尾，要求 `from<=to`，最多 **366 个自然日**。没有服务端默认范围，除注明外必须同时提供。
- 计划/饮食/体重/目标写日期为 `[今天-365,今天+7]`。训练也受365天补录下界限制，实际时间不得超过当前时间5分钟。
- 动作库/食物库分页 `{list,page,size,total}`，页码从 1 开始，默认 page=1,size=20；page下限1，size收敛为1–100。其余数组查询不是此分页结构。
- 数值字段以 JSON number 传递，金额/营养类最多2位小数。实体id使用后端返回值，不沿用演示数据id。

## A 账户与资料

| 方法/路径 | 请求 | data |
| --- | --- | --- |
| POST `/auth/register` | `{username,password,nickname?,gender?,birthDate?,heightCm?}` | `{id,username,nickname,role,createdAt}`，**不返回token** |
| POST `/auth/login` | `{username,password}` | `{token,tokenType:"Bearer",expiresIn,user:{id,username,nickname,role}}` |
| POST `/auth/logout` | 无 | null，当前令牌失效 |
| GET `/users/me` | 无 | `{id,username,nickname?,gender,birthDate?,heightCm?,role,status,createdAt}` |
| PUT `/users/me` | `{nickname?,gender?,birthDate?,heightCm?}` | `{id,nickname?,gender,birthDate?,heightCm?}`，保存后应GET重查 |
| PUT `/users/me/password` | `{oldPassword,newPassword}` | null |

- username：4–20位字母/数字/下划线；password：8–32位可见ASCII且同时含字母和数字。
- nickname：1–50字，首尾不可空白；gender：MALE/FEMALE/UNKNOWN；birthDate不可晚于今天；heightCm：100–250。
- 资料字段缺省/null表示不修改；昵称与出生日期空字符串表达清除。身高目前没有清除接口。
- 注册后需另调用登录。新账户没有训练、饮食、体重或服务端默认目标。

## B 目标

`Goal = {id,effectiveDate,kcal,proteinG,carbG,fatG}`。

| 方法/路径 | 请求 | data |
| --- | --- | --- |
| GET `/goals/current?date=...` | date可省略，默认今天 | Goal 或 null，取不晚于date最近一条 |
| POST `/goals` | `{effectiveDate,kcal,proteinG?,carbG?,fatG?}` | Goal，同日覆盖 |
| GET `/goals/history?from=...&to=...` | 生效日期范围 | Goal[]，生效日期升序 |

kcal：500–6000；三营养素：0–1000，省略时保存0。

## C 动作、计划与实际训练

| 方法/路径 | 请求 | data |
| --- | --- | --- |
| GET `/exercises` | keyword?,muscleGroup?,page?,size? | 分页，list项`{id,name,muscleGroup?,equipment?,isPublic}` |
| GET `/plans` | from,to,status? | `[{id,plannedDate,title?,status,itemCount}]`，日期/id倒序 |
| GET `/plans/{id}` | 无 | `{id,plannedDate,title?,status,note?,items:[{exerciseId,exerciseName,targetSets,targetReps,targetWeightKg?,orderNo}]}` |
| POST `/plans` | PlanSave | `{id,plannedDate,title?,status}` |
| PUT `/plans/{id}` | PlanSave | `{id,status,updatedAt}` |
| POST `/plans/{id}/status` | `{status:"COMPLETED"或"CANCELLED"}` | `{id,status,updatedAt}` |
| GET `/workouts` | from,to（logDate范围） | `[{id,planId?,performedAt,logDate,note?,sets:[{exerciseId,exerciseName,setNo,weightKg?,reps}]}]` |
| POST `/workouts` | WorkoutCreate | `{id,logDate,planId?}` |
| DELETE `/workouts/{id}` | 无 | null |

`PlanSave = {plannedDate,title?,note?,items:[{exerciseId,targetSets,targetReps,targetWeightKg?,orderNo?}]}`。

- items完整替换，长度1–20；targetSets1–20，targetReps1–100，targetWeightKg0–500，orderNo0–99。
- 计划只能从 PLANNED 单向进入 COMPLETED/CANCELLED，不能回退或重复变更；终态不能编辑。
- 无物理删除计划接口，应取消。

`WorkoutCreate = {planId?,performedAt,note?,sets:[{exerciseId,setNo?,weightKg?,reps}]}`。

- sets长度1–50；setNo1–20，weightKg0–500（null可表示自重），reps1–100。
- **计划完成与实际记录独立**：保存workout不会完成plan，完成plan不会创建workout。仅打卡不应伪造实际组。
- “记录并完成”需要先 POST workout，成功后 POST plan status；第二步失败不能自动重发workout而制造重复记录，应提供单独完成状态的重试。
- 当前允许多条workout关联同一plan，校验plan归属，不要求同日或动作匹配；可为已完成计划补录实际训练。
- 不传planId即临时训练，单独计数。删除workout不会回退plan完成状态。
- 建议显式传每个动作的setNo，避免依赖默认编号。无修改workout接口；界面不要显示不存在的更新操作。

## D 食物与饮食

| 方法/路径 | 请求 | data |
| --- | --- | --- |
| GET `/foods` | keyword?,state?,page?,size? | 分页，list项`{id,name,kcalPer100g,state,isPublic}` |
| GET `/foods/{id}` | 无 | `{id,name,brand?,kcalPer100g,proteinPer100g,carbPer100g,fatPer100g,state,source}` |
| GET `/diets` | date必填 | `[{id,meal,foodId,foodName,grams,snapshotKcal,snapshotProteinG,snapshotCarbG,snapshotFatG}]` |
| POST `/diets` | `{recordDate,meal,foodId,grams}` | `{id,recordDate,meal,grams,snapshotKcal,snapshotProteinG,snapshotCarbG,snapshotFatG}` |
| PUT `/diets/{id}` | `{meal?,grams?}` | 同新增响应 |
| DELETE `/diets/{id}` | 无 | null |
| GET `/diets/summary` | date必填 | `{date,kcal,proteinG,carbG,fatG,target:Goal或null,meals:{BREAKFAST:[],LUNCH:[],DINNER:[],SNACK:[]}}` |

- state：RAW/COOKED/BRAND；meal：BREAKFAST/LUNCH/DINNER/SNACK；grams1–2000。
- 列表不含三营养素，选择食物后取detail。新增只传foodId/grams，**权威换算由后端完成**。
- snapshot为实际摄入营养量，不是每100g。修改克重按原快照与原克重比例换算（2位小数），保留原来源；不读取最新食物库。仅修改餐次不改快照。换食物需删除旧记录并新增。
- 无记录时summary合计为0、meals全空；趋势统计用recorded标志区分无记录和0摄入。

## E 身体数据

| 方法/路径 | 请求 | data |
| --- | --- | --- |
| PUT `/body-records` | `{recordDate,weightKg,note?}` | `{id,recordDate,weightKg}`，同用户同日upsert |
| GET `/body-records` | from,to | `{list:[{id,recordDate,weightKg}],validDays,total}`，日期升序、非分页 |
| DELETE `/body-records/{id}` | 无 | null |

weightKg20–300。列表仅有效日，不补零；note不在当前读DTO中。

## F 服务端统计

所有路径均 GET，必须from/to。前端优先展示服务端统计，不以本地全量重算代替。

| 路径 | data及口径 |
| --- | --- |
| `/stats/workouts` | `{plannedCount,completedCount,cancelledCount,completionRate,extraWorkoutCount}`。plannedCount含已取消；完成率=completed/(planned-cancelled)，0–1，分母0返回null |
| `/stats/intake` | `{days:[{date,kcal,recorded}],avg:{kcal,proteinG,carbG,fatG}或null,validDays,target:Goal或null}`。包含区间每一天；无记录kcal=null，recorded=false；avg是**整个所选区间有效日**平均，不必然七日 |
| `/stats/weight` | `{points:[{date,weightKg}],first,last,change,sevenDayAvg,validDays}`。points仅有效日；change=last-first；七日窗口以to为锚、并限于所选区间，validDays为该窗口有效日数；无值返回null |

## H 管理（仅 ADMIN）

- POST `/admin/exercises`：`{name,muscleGroup?,equipment?}`。
- PUT `/admin/exercises/{id}`：`{name?,muscleGroup?,equipment?,status?}`。
- POST `/admin/foods`：`{name,brand?,kcalPer100g?,proteinPer100g?,carbPer100g?,fatPer100g?,state,source}`。
- PUT `/admin/foods/{id}`：`{kcalPer100g?,proteinPer100g?,carbPer100g?,fatPer100g?,source?,status?}`；无重命名/改state字段。
- 上述返回`{id,name,status}`。食物每100g能量0–900，三营养素0–100。
- PUT `/admin/users/{id}/status`：`{status:"ACTIVE"或"DISABLED"}`；管理员不能停用自己。
- 前端路由判断不是权限边界；普通用户调用应由后端拒绝。每次JWT请求都会核对账户当前状态与角色，停用/删除账户的旧令牌返回401/code1002。

## 验证范围

`ApiIntegrationTest` 使用真实过滤器、控制器、服务与H2事务验证；不等价于MySQL验收、移动网络与Android真机验收。不要将浏览器本地模拟数据测试当作服务端隔离证明。
