# Admin/Agent Token Auth

## Requirements

Status: CONFIRMED

- 管理后台 `/api/admin/**` 必须统一校验 Sa-Token。
- 代理后台 `/api/agent/**` 必须统一校验 Sa-Token。
- 放行登录接口：`/api/admin/login`、`/api/agent/login`。
- 用户端接口不改，不接入本次 token 校验。
- Sa-Token 登录有效期改为 6 小时。

## Development

Status: ACCEPT_PASS

- 在现有 `WebConfig` 增加 Sa-Token MVC 拦截器。
- 拦截范围只覆盖 `/api/admin/**` 和 `/api/agent/**`。
- 使用 `StpUtil.checkLogin()` 做统一登录校验。
- 更新 `sa-token.timeout` 为 `21600` 秒。

## Acceptance

Status: DONE

- 未携带 token 请求 `/api/admin/**` 非登录接口返回未登录错误。
- 未携带 token 请求 `/api/agent/**` 非登录接口返回未登录错误。
- `/api/admin/login`、`/api/agent/login` 可正常登录并返回 token。
- `/api/user/**` 现有用户名密码认证不受影响。
- token 过期时间为 6 小时。

## Impact

Status: CONFIRMED

- 影响管理后台和代理后台所有非登录接口。
- 可能暴露前端漏传 `Account-token` 的请求，需要前端带上登录返回 token。
- 不影响用户端取号、取码接口。

## Progress

Status: DONE

- 已增加 `/api/admin/**`、`/api/agent/**` 全局 token 校验。
- 已放行 `/api/admin/login`、`/api/agent/login`。
- 已将 token 有效期改为 6 小时。
- `mvn -q -DskipTests compile` 已通过。
