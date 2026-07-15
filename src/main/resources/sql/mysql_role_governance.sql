USE ticket_management;

-- 兼容旧版四角色数据库：先保留 AGENT 枚举完成无损迁移，再收敛为三角色。
ALTER TABLE users
    MODIFY COLUMN role ENUM('ROOT', 'ADMIN', 'AGENT', 'USER') NOT NULL;

-- 保留账号、密码、状态及全部业务数据，仅同步角色。
UPDATE users
SET role = 'ADMIN'
WHERE role = 'AGENT';

-- 初始化两名 OWNER，其余内置后台人员统一为 ADMIN；不重置任何现有密码。
UPDATE users
SET role = 'ROOT'
WHERE username IN ('admin01', 'admin02');
UPDATE users
SET role = 'ADMIN'
WHERE username IN ('admin03', 'admin04', 'admin05');

-- 所有 AGENT 行已迁移后再删除枚举值；该操作不会删除账号或关联数据。
ALTER TABLE users
    MODIFY COLUMN role ENUM('ROOT', 'ADMIN', 'USER') NOT NULL;

-- 支持工单负责人下拉框按角色和启用状态直接查询，避免用户全表扫描。
SET @role_status_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'idx_users_role_status'
);
SET @role_status_index_sql = IF(
    @role_status_index_exists = 0,
    'CREATE INDEX idx_users_role_status ON users (role, status)',
    'SELECT 1'
);
PREPARE role_status_index_statement FROM @role_status_index_sql;
EXECUTE role_status_index_statement;
DEALLOCATE PREPARE role_status_index_statement;

-- 升级后确认角色分布；应至少保留两个有效 ROOT。
SELECT user_id, username, role, status
FROM users
WHERE role IN ('ROOT', 'ADMIN')
ORDER BY FIELD(role, 'ROOT', 'ADMIN'), user_id;
