# Day07 安全检查清单

## 1. 账号与密码

| 检查项 | 状态 | 说明 |
| --- | --- | --- |
| BCrypt 哈希存储密码 | 已完成 | `PasswordUtil.hashPassword` 使用 BCrypt |
| 登录密码不明文比对 | 已完成 | `PasswordUtil.matches` 使用 BCrypt 校验 |
| 注册密码强度校验 | 已完成 | 要求 8 到 64 位，包含大小写字母、数字和特殊字符 |
| 空密码哈希安全处理 | 已完成 | `matches` 对空值直接返回 false |
| 禁用账号不可登录 | 已完成 | `UserService.login` 校验 `status` |

## 2. 权限控制

| 检查项 | 状态 | 说明 |
| --- | --- | --- |
| 普通用户只能查看本人资料 | 已完成 | `UserService.updateUser` 和 `saveProfile` 校验用户归属 |
| 普通用户只能查看本人工单 | 已完成 | `BusinessService` 和 `CrossDatabaseQueryService` 校验提交人 |
| 管理员功能要求 ADMIN 权限 | 已完成 | 统计、审计、用户管理、批处理均调用 `requireAdmin` |
| 内部备注仅管理员可见 | 已完成 | 评论查询按角色过滤 `INTERNAL_NOTE` |

## 3. 输入校验

| 检查项 | 状态 | 说明 |
| --- | --- | --- |
| 邮箱格式校验 | 已完成 | `UserService.validateEmail` |
| 手机号格式校验 | 已完成 | `UserService.validatePhone` |
| 工单标题长度校验 | 已完成 | 标题不能为空且不超过 200 |
| 工单金额校验 | 已完成 | 金额非负且最多 2 位小数 |
| 工单分类存在性校验 | 已完成 | 创建前检查 `CategoryDAO.findById` |
| 工单描述长度校验 | 已完成 | 描述不超过 4000 字符 |
| 用户资料长度校验 | 已完成 | 姓名、身份证、地址、备注均做长度限制 |

## 4. 数据访问安全

| 检查项 | 状态 | 说明 |
| --- | --- | --- |
| SQL 注入防护 | 已完成 | DAO 层统一使用 `PreparedStatement` / `CallableStatement` |
| 批处理权限保护 | 已完成 | `MaintenanceService` 要求 ADMIN 并复用状态流转规则 |
| 跨库写入补偿 | 已完成 | 创建工单失败时清理 MongoDB 详情 |
| 审计日志记录 | 已完成 | 登录、状态变更、批处理等写入 `system_logs` |

## 5. 剩余建议

- 生产环境应将 `db.properties` 中的数据库密码改为环境变量或密钥管理方案。
- 生产环境应开启 MySQL TLS 和 MongoDB 认证。
- 可在后续版本增加登录失败次数限制和验证码。
