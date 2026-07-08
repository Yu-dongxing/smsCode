# Security Remediation 2026-07-08

## 1. 背景

Codex Security 审查发现 6 个有效问题。用户本轮只选择 3 个修复项进入开发：

- REQ-SEC-20260708-001：公开 URL 代理接口只阻断内网访问。
- REQ-SEC-20260708-002：项目配置增删改收进管理员权限边界，并校验供应商 URL 目的地。
- REQ-SEC-20260708-003：代理项目线路更新增加 ownership 校验。

不纳入本轮：管理员默认密码、运行配置 secrets、用户/代理密码明文存储。

## 2. 目标

### 2.1 REQ-SEC-20260708-001

- 状态: CONFIRMED
- 确认状态: CONFIRMED
- 闭环状态: CLOSED_LOOP
- 目标: `/api/user/request/url` 仍可访问公网 URL，但禁止访问内网地址。
- 限制规则: “内网”只指 RFC1918 IPv4 地址段 `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`；不扩展到 loopback、link-local、metadata 地址。

### 2.2 REQ-SEC-20260708-002

- 状态: CONFIRMED
- 确认状态: CONFIRMED
- 闭环状态: CLOSED_LOOP
- 目标: `/api/project/**` 的项目配置新增、更新、删除必须具备管理员身份。
- 目标: 项目配置请求改为窄 DTO，避免直接绑定完整 `Project` 实体。
- 目标: 供应商 URL 目的地写入或执行前必须做目的地校验。
- 限制规则: 目的地校验复用 REQ-SEC-20260708-001 的 RFC1918 内网阻断规则。

### 2.3 REQ-SEC-20260708-003

- 状态: CONFIRMED
- 确认状态: CONFIRMED
- 闭环状态: CLOSED_LOOP
- 目标: 代理更新项目线路配置时，必须校验目标 `user_project_line` 归属当前代理或当前代理授权范围。
- 目标: 不匹配时直接拒绝更新。

## 3. 范围

- 影响模块: `UserController` URL 代理、`ProjectController` 项目配置、项目配置 DTO、`ModuleUtil`/供应商 URL 调用链、`UserServiceImpl.updateAgentProjectConfig`。
- 影响标签: AFFECT_AUTH, AFFECT_API, AFFECT_CONFIG, SECURITY_RISK。

## 4. 非目标

- 不处理管理员默认账号密码。
- 不处理 `application.yaml` 中数据库和管理员静态凭据。
- 不处理用户/代理密码明文存储和迁移。
- 不重构无关接口，不改用户端取号/取码业务流程。

## 5. 影响模块

- `src/main/java/com/wzz/smscode/controller/user/UserController.java`
- `src/main/java/com/wzz/smscode/controller/sys/ProjectController.java`
- `src/main/java/com/wzz/smscode/config/WebConfig.java`
- `src/main/java/com/wzz/smscode/service/impl/ProjectServiceImpl.java`
- `src/main/java/com/wzz/smscode/moduleService/ModuleUtil.java`
- `src/main/java/com/wzz/smscode/service/impl/UserServiceImpl.java`
- 相关 DTO 和测试文件。

## 6. 详细规则

- URL 安全校验优先放在共享边界，避免每个调用点重复实现。
- 项目配置接口必须复用现有 Sa-Token 管理员登录 ID 规则，即管理员 loginId 为 `0`。
- 代理项目线路更新必须在写入前完成 ownership 校验。
- 新增安全分支必须有最小可运行测试或验证命令。
- 回补规则: HTTP 客户端不得自动跟随跳转，避免 URL 校验后被 30x 带到内网地址。
- 回补规则: 代理项目线路更新只允许改价格和备注，不允许通过该接口改绑 `projectId` 或 `lineId`。
- 开发状态: CODE_DONE
- 测试状态: TEST_PASS
- 验收状态: WAIT_ACCEPT

## 7. 验收标准

- 未确认前不进入代码修改。
- 公网 URL 仍可通过允许的代理或供应商配置校验。
- 内网 URL 被拒绝，错误信息不泄露内部响应内容。
- 非管理员 token 请求项目配置新增、更新、删除被拒绝。
- 管理员 token 请求项目配置新增、更新、删除仍可正常执行。
- 代理不能更新不属于自己的 `user_project_line` 记录。
- 代理可以更新自己授权范围内的 `user_project_line` 记录。
- `sh mvnw -q -DskipTests compile` 通过。
- 增加或执行聚焦测试，覆盖拒绝路径和合法路径。
- 验证结果: `sh mvnw -q -Dtest=UrlSecurityUtilTest test` 通过；`sh mvnw -q -DskipTests compile` 通过。
- 回补验证结果: `sh mvnw -q -Dtest=UrlSecurityUtilTest,UserServiceImplSecurityTest test` 通过；`sh mvnw -q -DskipTests compile` 通过。

## 8. 风险

- 如果“内网”定义过窄，loopback、link-local 或 metadata 地址仍可能形成 SSRF 绕过。
- 项目配置改窄 DTO 可能影响前端提交字段，需要以现有前端实际字段为准。
- ownership 校验需要确认代理和下级用户关系，避免误拒合法代理操作。

## 9. 变更记录

版本: v0.1.0
时间: 2026-07-08
修改人: Codex
修改原因: 记录用户选择的 3 个安全修复项，等待确认后进入开发。
修改内容: 新增待确认需求、范围、非目标、验收标准和风险。
影响范围: URL 代理、项目配置权限、代理项目线路 ownership。
关联 ID: REQ-SEC-20260708-001, REQ-SEC-20260708-002, REQ-SEC-20260708-003

版本: v0.2.0
时间: 2026-07-08
修改人: Codex
修改原因: 用户回复“确认”，进入开发门禁。
修改内容: 三个需求改为 CONFIRMED，明确内网只按 RFC1918 IPv4 地址段阻断。
影响范围: URL 代理、项目配置权限、代理项目线路 ownership。
关联 ID: REQ-SEC-20260708-001, REQ-SEC-20260708-002, REQ-SEC-20260708-003

版本: v0.3.0
时间: 2026-07-08
修改人: Codex
修改原因: 完成安全修复开发和聚焦验证。
修改内容: 增加 RFC1918 URL 校验、项目配置管理员写权限、项目配置窄 DTO、代理项目线路 ownership 校验和 URL 单测。
影响范围: URL 代理、项目配置权限、供应商 URL 调用链、代理项目线路配置。
关联 ID: REQ-SEC-20260708-001, REQ-SEC-20260708-002, REQ-SEC-20260708-003

版本: v0.4.0
时间: 2026-07-08
修改人: Codex
修改原因: 回补审查发现的 P1/P2 残留风险。
修改内容: 禁用 RestTemplate 自动跳转；代理项目线路更新禁止改绑项目和线路；增加回归测试。
影响范围: URL 代理、特殊 API RestTemplate 调用、代理项目线路配置。
关联 ID: REQ-SEC-20260708-001, REQ-SEC-20260708-003
