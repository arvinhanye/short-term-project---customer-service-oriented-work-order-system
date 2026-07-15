# 三角色演示账号与测试数据

以下账号仅用于本地课程演示环境，不应复制到生产数据库。三个账号均处于启用状态，不要求首次登录换密，密码不含空格。

| 身份 | 用户名 | 密码 | 主要展示内容 |
| --- | --- | --- | --- |
| ROOT | `demo_root` | `Cedar#47Quartz!` | 权限治理、管理员维护、全量审计 |
| ADMIN | `demo_admin` | `Mango#58Orbit!` | 工单认领、回复、内部备注、状态流转、确认式转派 |
| USER | `demo_user` | `Lunar#69Pebble!` | 创建和跟进工单、客户回复、催促、评价 |

## 数据规模

| 账号 | 业务数据 | 行为日志 | 系统日志 |
| --- | --- | ---: | ---: |
| `demo_root` | 60 条权限治理和安全审计场景 | 60 | 60 |
| `demo_admin` | 当前负责 51 张工单、78 条客服回复/内部备注，另有 3 张待确认接手 | 60 | 60 |
| `demo_user` | 60 张本人创建的工单、84 条客户回复/评价 | 60 | 60 |

60 张工单按固定测试编号 `70001`—`70060` 生成，覆盖：

- 待处理、处理中、已完成、已关闭、已取消各 12 张；
- LOW、MEDIUM、HIGH、URGENT 四档优先级各 15 张；
- 8 个有效业务分类、35 个带金额场景、6 张未分配工单；
- 认领、客服回复、客户回复、内部备注、催促和 1—5 星评价；
- 转派已接受 6 次、已拒绝 3 次、已撤销 3 次，以及 6 个待确认接手邀请；
- MySQL `ticket_history` 共 417 条追加式历史，包含 PUBLIC 和 STAFF_ONLY 可见性；
- 详情、附件示例、负责人和转派镜像同步保存在 MongoDB。

ROOT 根据权限边界不会被设置为工单负责人，其 50+ 数据由权限治理行为和系统审计构成。

## 重建与验证

依次执行：

```bash
mysql -uroot ticket_management --execute="source src/main/resources/sql/mysql_demo_scenarios.sql"
mongosh --quiet src/main/resources/sql/mongodb_demo_scenarios.js
```

脚本通过固定账号编号、工单编号、评论事件编号和 `seed_batch=three-role-demo-v1` 保证幂等。重复执行不会增加工单、评论或日志数量；MySQL 脚本会将三个专用账号的密码恢复为上表密码，但不会重置已经存在的演示工单流程。
