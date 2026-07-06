# Logout Disabled User

## Requirements

Status: CONFIRMED

- 总后台或代理端禁用用户账号后，立即使该账号已登录 token 失效。
- 解禁不自动登录，不恢复旧 token。
- 用户端取号/取码认证逻辑不改。

## Development

Status: DONE

- 在共享状态更新入口 `UserServiceImpl.updateUserStatusById` 中处理。
- 当状态为 `1(禁用)` 时调用 Sa-Token 按登录 ID 登出。

## Acceptance

Status: ACCEPT_PASS

- 禁用已登录代理账号后，旧 token 请求 `/api/agent/**` 返回未登录。
- 禁用普通用户账号不影响接口稳定性。
- 解禁后旧 token 仍不可用，需要重新登录。

## Impact

Status: CONFIRMED

- 影响所有调用 `updateUserStatusById` 的禁用入口。
- 不影响用户端用户名密码认证流程。

## Progress

Status: DONE

- 已在禁用入口增加 Sa-Token 登出。
- `mvn -q -DskipTests compile` 已通过。
