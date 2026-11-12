# SDD ledger — plan: docs/superpowers/plans/2026-11-12-review-fixes.md

Pre-flight: core、设计器/鉴权和文档没有新增跨任务接口；按现有公共接口逐项验证。
Ruling: 在用户当前 dev 检出执行，不创建/切换工作树、不提交 — 用户要求按方案在此修复，工作区初始干净；源码结果保留在此供审阅。
Ruling: 保留公共接口破坏性变更、不加 @Deprecated — 用户明确覆盖兼容性规则；下游升级须遵循已有破坏性变更指引。
Ruling: 许可头规范项暂不批量处理 — 本次范围是功能修复；新 Java 文件照用许可头。
Ruling: 使用现有工具执行 TDD 与验证，不调用未列出的 superpowers 子技能 — 当前技能列表没有 test-driven-development/verification-before-completion；以真实 RED→GREEN 命令和测试记录承担验证。

Task 1: implementation done — 新增 6 个核心用例；RED: /tmp/warm-flow-fixes-core-red.log（图元覆盖、转办异常、3 条普通任务 4 次节点查询）。聚焦 GREEN: 组签到达 20/20、转办 6/6、递归图元通过。事件监听器用例暴露变量快照为空，已修复共享事件加载；完整 509 用例首次仅旧 ordinaryNodeRejectsMultipleTransferTargets 断言失败，已按批准的行为修复为接受多目标，等待复跑。
Task 1: Ruling: 在 loadEventExecution 合并实例变量与 payload，再调用 start — 原入口可能给监听器 null 变量并忽略实例旧值；测试已覆盖旧值可读、新值选路，覆盖消息/定时捕获与边界事件。
Task 1: Ruling: 调整组签测试内的普通节点多目标预期并设置 ignorePermission — 旧测试固定了本次明确修复的限制，门面普通权限须明确提供宿主权限或使用忽略通道；底层普通转办用例仍走真实权限校验。
Task 2: implementation done — UI 属性面板用例 RED→GREEN，Node tests 17/17；自动配置测试 RED 为 NoSuchBeanDefinitionException；登录 MockMvc RED 为 NPE 与 HTTP200 而非400，已补 @Valid，待完整 GREEN。
Task 1: complete — /tmp/warm-flow-fixes-reactor-tests.log：core 509/509 GREEN（新增 6 例）；含消息/定时捕获与边界监听器变量及递归图元断言，组签失败保护全部通过。
Task 2: complete tests — SpEL 74/74、自动配置 2/2、Demo 6/6（其中登录 MockMvc 新增2），设计器 Node 17/17；本地依赖已用 -DskipTests install 更新，未发布。
Task 3: in progress — README、Java/JS 协议注释已同步；公共接口删除、许可头批量补齐不处理。设计器生产构建通过（6.45s，仅 chunk size 警告）；浏览器已验证匿名401、缺失密码400、登录后元数据200，未保存流程定义或推进业务实例。
Final review: 一次独立只读复查确认空变量 / 类路径 start 监听器写入丢失；ListenerUtil 将空 map 当 null 替换，导致上下文不共享。新增捕获 / 边界用例（消息与定时各一轮）在无实例变量且空 payload 下 RED→GREEN；仅在变量为 null 时创建新 map。RED: /tmp/warm-flow-fixes-listener-red.log，GREEN: /tmp/warm-flow-fixes-listener-green.log。
Final: Ruling: 捕获事件到达时也执行 start，首变量夹具在等待建立后才激活写入 — 隔离本次触发入口问题，不让到达写入掩盖空变量场景；另补监听器选路到无有效办理人组签的边界失败快照用例。最终全量回归通过：core 512/512、SpEL 74/74、自动配置 2/2、Demo 6/6、Node 17/17；日志 /tmp/warm-flow-fixes-final-{reactor,demo,ui}.log。
Final: Ruling: 复查不评判已批准的公共接口删除与已有许可头积压；本次不恢复接口、不扩展许可头清理，风险为下游须接受不兼容升级。外部监听器副作用回滚与跨数据库事务 / 租约实现未改变，不宣称本次覆盖；自动配置测试证明 bean 注册，匿名 HTTP401 由 Demo 拦截器验证，不冒充 advice 专属 HTTP 测试。
Task 3: Ruling: Demo 浏览器验证关闭 timer-enabled 并保持 sql.init.mode=never — 不推进本机既有等待/实例，不初始化或重建表；仅测未保存节点属性与鉴权/登录负例，不创建业务数据。
Task 3: complete — 生产构建、17项设计器测试通过。仿钉钉浏览器属性面板通过会签（100%）、票签（75%）、顺签、组签（review组/user:admin成员）的直返校验；切到开始节点后，画布保留组签与 RETURN_TO_REJECTOR 配置，未请求保存定义。浏览器无新增控制台错误，元数据请求200；匿名401、缺失密码400已验证。已有用户与组织数据仅读取。
Final: fixed 空变量类路径监听器首写丢失 — catchClassPathStartListenerCreatesFirstRoutingVariable / boundaryClassPathStartListenerCreatesFirstRoutingVariable RED→GREEN，最终核心512/512；组合边界无有效组签成员的快照用例通过。
Final: complete — 10项原审查状态已更新：8 fixed，2 skipped（用户接受公共接口不兼容；许可头批量补齐不在本轮范围）。未提交、未推送、HEAD仍c7386f7。
Cleanup: 临时登录已用logout退出（HTTP200），模拟视口恢复desktop；临时API已停止，launch.json恢复原启动参数，UI预览停止。保留源码和本记录供审阅。
Not covered: advice专属HTTP dispatch、不保存真实定义的运行流转、多ORM/SB3/SB4与四数据库集成矩阵、外部监听器副作用回滚；经典/仿钉钉转换由Node测试覆盖，本次浏览器仅仿钉钉模式。

Demo integration (2026-11-12):
- Env: Docker MySQL 8.0.42 `warm-flow`; API 8081 with `--warm-flow.timer-enabled=false --spring.sql.init.mode=never`; web 5174; admin/approver1/approver2/approver3 login via HttpOnly Cookie.
- Fixture: imported `qa_collab_return_20261112` (definition 2120819278724431873), start → 申请人(admin) → 会签(approver1+approver2, 100%) → 组签(legal=approver1, finance=approver2) → 驳回发起(approver3, `reject.afterApproval=RETURN_TO_REJECTOR`, anyNodeSkip=submit) → 结束; rejector→submit REJECT edge.
- Instance 2120819296491503617 / businessId QA-WF-20261112-A: admin submit; both countersign users pass; group legal transferred approver1→approver3 (flow_user type 2 seq 0), approver3+approver2 complete groups; approver3 rejects to submit. `flow_instance.variable` held `{"_wf_reject_return":{"nodeCode":"submit","taskId":"...","sourceTaskId":"...","returnNodeCode":"rejector"}}`.
- admin passes returned submit: next todo is 驳回发起 directly; no countersign/group task or history re-created; association cleared (`{}`); approver3 passes → instance end/finish.
- DB final: history counts start1, submit2(pass,pass), countersign2, groupSign3(transfer,pass,pass), rejector2(back,pass), no leftover tasks; server log no errors.
- UI: instance list filter shows 1 finished record; detail history shows 申请人通过 → 驳回发起 (skips collaborative nodes) and 组签确认 transfer row. Screenshot captured in-session.
- Auth negatives: anonymous `/api/definitions` and `/warm-flow/query-def` → HTTP 401; missing/blank password → HTTP 400 `password 密码不能为空`; wrong password returns HTTP 200 with business `code=401` (demo design observation, not changed).
- Ordinary multi-target transfer is not reachable through Demo facade (`TaskAppServiceImpl.transfer` forces single target for non-group); core regression covers it. Group multi-target slot not exercised in this run.
- Test data retained (skill default): definition 2120819278724431873 + instance 2120819296491503617. Temporary launch args restored; preview servers stopped; browser/API sessions logged out.
Cleanup (2026-11-12): 用户要求清理测试数据。通过 Demo API 删除实例 2120819296491503617（历史/待办/等待级联为 0）与定义 2120819278724431873（节点/连线为 0）；临时 API 已停止、launch.json 恢复、Cookie 与 /tmp 夹具删除。库计数恢复到测试前：definition 70、instance 90、task 69、his 396、user 93、wait 4，QA 定义残留 0。

Skill update + full test run (2026-11-12):
- Updated tracked `.codex/skills/warm-flow-demo-testing`（仓库主版本）并同步到用户级 `~/.claude/skills/warm-flow-demo-testing`：SKILL.md、references/test-matrix.md、references/regression-cases.md、agents/openai.yaml。首次同步误用较小的用户级版本覆盖仓库版本，已从 HEAD 恢复后改为在仓库主版本上增量更新，未丢失既有 GRP/RST 内容。
- Added RR（驳回直返：配置识别、协作区间跳过、并行回退、`_wf_reject_return`、限制动作、事务前置）、AUTH（登录校验、Cookie 身份、匿名 401、设计器 advice 自动装配）、GRP-16（组签与直返组合）、事件 start 监听器选路与首变量不变量；修正过期 `X-User-Name`、`/reset`、working-tree-only 描述和 `flow_his_task`/`flow_user`/`flow_node` 查询字段。
- Validation: frontmatter + reference links + case IDs checked; 仓库 test-matrix 39 个带编号用例（GRP-01~16、RST-01~10、RR-01~08、AUTH-01~05），regression-cases 无重复标题；仅保留 `X-User-Name` 作为被拒绝身份来源的说明。
- Full Maven reactor `mvn test`: 693 tests, 0 failures/errors, BUILD SUCCESS. Module totals: core 512, mybatis-core 11, plugin-modes 74, mybatis-sb3 20, mybatis-sb4 18, mybatis-plus-core 20, mybatis-plus-sb3 18, mybatis-plus-sb4 18, ui-sb-web 2. Log /tmp/wf-full-mvn-test.log.
- Demo backend: 6/6, BUILD SUCCESS (/tmp/wf-demo-mvn-test.log). Designer Node: 17/17 (/tmp/wf-ui-node-test.log). Designer production build passed (/tmp/wf-ui-build.log); Demo web `vue-tsc --noEmit && vite build` passed (/tmp/wf-demo-web-build.log).
- Real Demo integration for new features was completed before cleanup and is recorded above; no test data remains. No code changes in this turn beyond skill docs; no commit/push.
