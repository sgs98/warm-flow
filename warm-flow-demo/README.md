# warm-flow-demo

Warm-Flow 全栈集成演示工程（非正式发布，不参与 Warm-Flow Maven 反应堆，不改动引擎公共契约）。

- 后端：`warm-flow-demo/`（Spring Boot 3.0 + MyBatis-Plus 3.5.12 + MySQL 8，源码编译基线 Java 8）
- 前端：`warm-flow-demo-web/`（Vue 3 + Vite 5 + TypeScript + Element Plus，通过 iframe 使用后端内置设计器）

## 目录结构

```text
warm-flow-demo/
├── warm-flow-demo/          # 后端独立 Maven 工程
│   └── src/main/resources/sql/   # schema.sql / data.sql
└── warm-flow-demo-web/      # 前端独立 Vite 工程
```

## 数据库准备

需要本机存在 MySQL 8 与数据库 `warm-flow`（可通过环境变量 `WARM_DEMO_DB_URL` / `WARM_DEMO_DB_USERNAME` / `WARM_DEMO_DB_PASSWORD` 覆盖）。

按顺序执行 SQL：

```bash
# 1) Warm-Flow 引擎 7 张核心表（官方规范脚本，本 demo 不复制）
mysql -uroot -p warm-flow < <repo>/sql/mysql/warm-flow-all.sql

# 2) demo 业务表
mysql -uroot -p warm-flow < warm-flow-demo/src/main/resources/sql/schema.sql

# 3) demo 初始数据
mysql -uroot -p warm-flow < warm-flow-demo/src/main/resources/sql/data.sql
```

## 启动

后端：

```bash
cd warm-flow-demo
mvn spring-boot:run            # 或打包 java -jar target/warm-flow-demo-1.8.9.jar
```

前端：

```bash
cd warm-flow-demo-web
pnpm install
pnpm dev                      # http://localhost:5173
```

## 说明与约束

- 本目录不参与正式发布；设计器页面由后端 `warm-flow-plugin-ui-sb-web` 与 `warm-flow-plugin-vue3-ui` 提供，前端通过 `/warm-flow-ui/index.html` iframe 集成。
- 不改动 Warm-Flow 公共 API、实体字段、状态枚举、正式 SQL 脚本与部署配置。
- 本 demo 不执行 `git commit` / `git push`，由维护者自行提交。
- 任务操作口径：通过/退回需流程中存在对应跳转分支；退回（REJECT）要求审批节点配置了 REJECT 跳转边，单级纯通过流程不能退回。转办/委派/加签/减签/撤回/终止已在 demo 数据上真机跑通；终止需由当前节点办理人执行，撤回由发起人执行。
