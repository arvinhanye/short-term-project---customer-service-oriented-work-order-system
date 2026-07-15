USE ticket_management;

-- 三角色演示数据：固定编号、幂等追加，不删除或覆盖已有业务工单。
-- ROOT 仅产生治理审计；ADMIN 负责工单协作；USER 提交、催促和评价。

DROP PROCEDURE IF EXISTS append_demo_history;
DELIMITER $$
CREATE PROCEDURE append_demo_history(
    IN p_item_id BIGINT,
    IN p_order_id BIGINT,
    IN p_event_seq BIGINT,
    IN p_event_type VARCHAR(50),
    IN p_visibility VARCHAR(20),
    IN p_actor_user_id BIGINT,
    IN p_actor_username VARCHAR(50),
    IN p_actor_role VARCHAR(20),
    IN p_target_user_id BIGINT,
    IN p_from_status TINYINT,
    IN p_to_status TINYINT,
    IN p_from_admin_id BIGINT,
    IN p_to_admin_id BIGINT,
    IN p_reason VARCHAR(500),
    IN p_source_type VARCHAR(30),
    IN p_source_id VARCHAR(64),
    IN p_event_payload LONGTEXT,
    IN p_occurred_at DATETIME(3)
)
BEGIN
    INSERT IGNORE INTO ticket_history (
        event_id, item_id, order_id, event_seq, event_type, visibility,
        actor_user_id, actor_username, actor_role, target_user_id,
        from_status, to_status, from_admin_id, to_admin_id, reason,
        source_type, source_id, event_payload, occurred_at
    ) VALUES (
        CONCAT('d', LPAD(p_item_id, 7, '0'), '-', LPAD(p_event_seq, 4, '0'),
               '-4000-8000-', LPAD(p_item_id, 12, '0')),
        p_item_id, p_order_id, p_event_seq, p_event_type, p_visibility,
        p_actor_user_id, p_actor_username, p_actor_role, p_target_user_id,
        p_from_status, p_to_status, p_from_admin_id, p_to_admin_id, p_reason,
        p_source_type, p_source_id, p_event_payload, p_occurred_at
    );
END $$
DELIMITER ;

DROP PROCEDURE IF EXISTS seed_demo_scenarios;
DELIMITER $$
CREATE PROCEDURE seed_demo_scenarios()
BEGIN
    DECLARE v_index INT DEFAULT 1;
    DECLARE v_reminder_index INT;
    DECLARE v_item_id BIGINT;
    DECLARE v_order_id BIGINT;
    DECLARE v_status TINYINT;
    DECLARE v_category_id BIGINT;
    DECLARE v_title VARCHAR(200);
    DECLARE v_amount DECIMAL(10, 2);
    DECLARE v_created_at DATETIME(3);
    DECLARE v_sequence BIGINT;
    DECLARE v_reminder_count INT;
    DECLARE v_current_admin_id BIGINT;
    DECLARE v_initial_admin_id BIGINT;
    DECLARE v_initial_admin_name VARCHAR(50);
    DECLARE v_transfer_request_id CHAR(36);
    DECLARE v_transfer_requester BIGINT;
    DECLARE v_transfer_target BIGINT;
    DECLARE v_transfer_reason VARCHAR(200);

    IF (SELECT COUNT(*) FROM categories WHERE category_id IN (4001, 4004, 4005, 4007, 4008, 4009, 4010, 4013)) < 8 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '演示数据需要的标准分类不完整，请先初始化分类';
    END IF;

    INSERT INTO users (
        user_id, username, password_hash, email, phone, role, status,
        failed_login_attempts, locked_until, must_change_password,
        password_changed_at, created_at, updated_at
    ) VALUES
        (19001, 'demo_root',
         '$2a$12$GcCEoFxYZPpkJPv.Z9D4x.CbqJsUMcHdmpotTuWW024mFWZ/5u90K',
         'demo.root@example.test', '13900009001', 'ROOT', 1, 0, NULL, 0,
         '2026-07-15 09:00:00', '2026-05-01 09:00:00', CURRENT_TIMESTAMP),
        (19002, 'demo_admin',
         '$2a$12$zTSOOAT7sRwJfe3J0z/FhuJHYVmpoUYYQG8P2gnETnpQyzjAeb0BC',
         'demo.admin@example.test', '13900009002', 'ADMIN', 1, 0, NULL, 0,
         '2026-07-15 09:00:00', '2026-05-01 09:05:00', CURRENT_TIMESTAMP),
        (19003, 'demo_user',
         '$2a$12$kvQeKmkG/xRvEnJaSnSpcuTg9IuWQtcv1TmEpWbQTsPMtSbO25QNG',
         'demo.user@example.test', '13900009003', 'USER', 1, 0, NULL, 0,
         '2026-07-15 09:00:00', '2026-05-01 09:10:00', CURRENT_TIMESTAMP)
    ON DUPLICATE KEY UPDATE
        password_hash = VALUES(password_hash),
        email = VALUES(email),
        phone = VALUES(phone),
        role = VALUES(role),
        status = 1,
        failed_login_attempts = 0,
        locked_until = NULL,
        must_change_password = 0,
        password_changed_at = VALUES(password_changed_at);

    INSERT INTO profiles (user_id, real_name, id_card, address, notes) VALUES
        (19001, '系统所有者演示账号', NULL, '演示环境 / 平台治理组', '仅用于权限治理、账号管理和审计展示'),
        (19002, '一线支持演示管理员', NULL, '演示环境 / 客户支持组', '负责认领、回复、备注、状态流转和确认式转派'),
        (19003, '客户演示账号', NULL, '演示环境 / 华东地区客户', '用于提交、跟进、催促和评价工单')
    ON DUPLICATE KEY UPDATE
        real_name = VALUES(real_name),
        address = VALUES(address),
        notes = VALUES(notes);

    WHILE v_index <= 60 DO
        SET v_item_id = 70000 + v_index;
        SET v_order_id = 80000 + v_index;

        IF NOT EXISTS (SELECT 1 FROM orders WHERE item_id = v_item_id) THEN
            SET v_status = MOD(v_index - 1, 5);
            SET v_created_at = DATE_ADD('2026-05-01 08:30:00', INTERVAL v_index DAY);
            SET v_reminder_count = IF(v_status IN (0, 1), MOD(v_index, 3), 0);
            SET v_current_admin_id = CASE
                WHEN MOD(v_index - 1, 10) = 0 THEN NULL
                WHEN MOD(v_index, 20) = 12 THEN 10003
                ELSE 19002
            END;
            SET v_initial_admin_id = CASE
                WHEN MOD(v_index, 10) = 6 THEN 10003
                ELSE v_current_admin_id
            END;
            SET v_initial_admin_name = IF(v_initial_admin_id = 10003, 'admin03', 'demo_admin');

            SET v_transfer_request_id = CASE
                WHEN MOD(v_index, 20) IN (2, 12)
                    THEN CONCAT('e', LPAD(v_item_id, 7, '0'), '-0000-4000-8000-', LPAD(v_item_id, 12, '0'))
                ELSE NULL
            END;
            SET v_transfer_requester = CASE
                WHEN MOD(v_index, 20) = 2 THEN 19002
                WHEN MOD(v_index, 20) = 12 THEN 10003
                ELSE NULL
            END;
            SET v_transfer_target = CASE
                WHEN MOD(v_index, 20) = 2 THEN 10003
                WHEN MOD(v_index, 20) = 12 THEN 19002
                ELSE NULL
            END;
            SET v_transfer_reason = CASE
                WHEN MOD(v_index, 20) = 2 THEN '需要支付渠道经验，请二线管理员确认接手'
                WHEN MOD(v_index, 20) = 12 THEN '客户已补充完整材料，邀请演示管理员继续处理'
                ELSE NULL
            END;

            SET v_category_id = CASE MOD(v_index - 1, 12)
                WHEN 0 THEN 4005 WHEN 1 THEN 4007 WHEN 2 THEN 4008
                WHEN 3 THEN 4010 WHEN 4 THEN 4013 WHEN 5 THEN 4004
                WHEN 6 THEN 4013 WHEN 7 THEN 4004 WHEN 8 THEN 4009
                WHEN 9 THEN 4004 WHEN 10 THEN 4001 ELSE 4010
            END;
            SET v_title = CASE MOD(v_index - 1, 12)
                WHEN 0 THEN '登录后频繁被要求重新验证身份'
                WHEN 1 THEN '退款已审核但银行卡仍未到账'
                WHEN 2 THEN '同一订单出现两笔扣款记录'
                WHEN 3 THEN '移动端提交按钮点击后无响应'
                WHEN 4 THEN '报表导出后金额列显示为空'
                WHEN 5 THEN '修改收货地址后订单没有同步'
                WHEN 6 THEN '电子发票抬头保存失败'
                WHEN 7 THEN '优惠券显示可用但结算失败'
                WHEN 8 THEN '夜间访问订单页面偶发 502'
                WHEN 9 THEN '订单状态长时间停留在处理中'
                WHEN 10 THEN '账户绑定手机号无法更换'
                ELSE '报销附件上传后预览模糊'
            END;
            SET v_title = CONCAT(v_title, '（演示 ', LPAD(v_index, 2, '0'), '）');
            SET v_amount = CASE v_category_id
                WHEN 4007 THEN 128.00 + v_index * 3.25
                WHEN 4008 THEN 86.00 + v_index * 5.10
                WHEN 4004 THEN 49.90 + v_index * 2.30
                WHEN 4013 THEN 320.00 + v_index * 8.50
                ELSE 0.00
            END;

            INSERT INTO items (item_id, title, category_id, status, created_at, updated_at)
            VALUES (v_item_id, v_title, v_category_id, v_status, v_created_at,
                    DATE_ADD(v_created_at, INTERVAL 3 DAY));

            INSERT INTO orders (
                order_id, user_id, item_id, amount, status, assigned_admin_id,
                transfer_request_id, transfer_requested_by, transfer_target_admin_id,
                transfer_reason, transfer_requested_at, reminder_count,
                last_reminded_at, workflow_version, created_at
            ) VALUES (
                v_order_id, 19003, v_item_id, v_amount, v_status, v_current_admin_id,
                v_transfer_request_id, v_transfer_requester, v_transfer_target,
                v_transfer_reason,
                IF(v_transfer_request_id IS NULL, NULL, DATE_ADD(v_created_at, INTERVAL 30 HOUR)),
                v_reminder_count,
                IF(v_reminder_count = 0, NULL, DATE_ADD(v_created_at, INTERVAL 2 DAY)),
                0, v_created_at
            );

            SET v_sequence = 1;
            CALL append_demo_history(
                v_item_id, v_order_id, v_sequence, 'TICKET_CREATED', 'PUBLIC',
                19003, 'demo_user', 'USER', NULL, NULL, 0, NULL, NULL, NULL,
                'DEMO_SEED', CONCAT('demo-ticket-', v_item_id),
                JSON_OBJECT('title', v_title,
                            'priority', ELT(MOD(v_index - 1, 4) + 1, 'LOW', 'MEDIUM', 'HIGH', 'URGENT')),
                DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
            );

            IF v_initial_admin_id IS NOT NULL THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TICKET_CLAIMED', 'PUBLIC',
                    v_initial_admin_id, v_initial_admin_name, 'ADMIN', NULL,
                    NULL, NULL, NULL, v_initial_admin_id, NULL,
                    'DEMO_SEED', CONCAT('demo-claim-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            -- 处理中工单先进入处理中，再发起接手邀请；避免构造出待确认转派期间流转状态的非法历史。
            IF v_status = 1 THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'STATUS_CHANGED', 'PUBLIC',
                    v_initial_admin_id, v_initial_admin_name, 'ADMIN',
                    NULL, 0, 1, NULL, NULL, '开始核对客户提交的材料',
                    'DEMO_SEED', CONCAT('demo-status-processing-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            IF MOD(v_index, 10) = 6 THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TRANSFER_REQUESTED', 'STAFF_ONLY',
                    10003, 'admin03', 'ADMIN', 19002, NULL, NULL, 10003, 19002,
                    '原负责人排班结束，由演示管理员继续跟进', 'TRANSFER_REQUEST',
                    CONCAT('demo-transfer-accepted-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TRANSFER_ACCEPTED', 'STAFF_ONLY',
                    19002, 'demo_admin', 'ADMIN', 19002, NULL, NULL, 10003, 19002,
                    '原负责人排班结束，由演示管理员继续跟进', 'TRANSFER_REQUEST',
                    CONCAT('demo-transfer-accepted-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            ELSEIF MOD(v_index, 20) = 7 THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TRANSFER_REQUESTED', 'STAFF_ONLY',
                    19002, 'demo_admin', 'ADMIN', 10003, NULL, NULL, 19002, 10003,
                    '需要二线管理员协助核对支付流水', 'TRANSFER_REQUEST',
                    CONCAT('demo-transfer-rejected-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TRANSFER_REJECTED', 'STAFF_ONLY',
                    10003, 'admin03', 'ADMIN', 10003, NULL, NULL, 19002, 19002,
                    '材料不足，建议原负责人先向客户补充取证', 'TRANSFER_REQUEST',
                    CONCAT('demo-transfer-rejected-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            ELSEIF MOD(v_index, 20) = 17 THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TRANSFER_REQUESTED', 'STAFF_ONLY',
                    19002, 'demo_admin', 'ADMIN', 10003, NULL, NULL, 19002, 10003,
                    '请求二线管理员确认边界场景', 'TRANSFER_REQUEST',
                    CONCAT('demo-transfer-cancelled-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TRANSFER_CANCELLED', 'STAFF_ONLY',
                    19002, 'demo_admin', 'ADMIN', 10003, NULL, NULL, 19002, 19002,
                    '已从知识库找到解决方案，撤销接手邀请', 'TRANSFER_REQUEST',
                    CONCAT('demo-transfer-cancelled-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            ELSEIF v_transfer_request_id IS NOT NULL THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'TRANSFER_REQUESTED', 'STAFF_ONLY',
                    v_transfer_requester,
                    IF(v_transfer_requester = 19002, 'demo_admin', 'admin03'), 'ADMIN',
                    v_transfer_target, NULL, NULL, v_current_admin_id, v_transfer_target,
                    v_transfer_reason, 'TRANSFER_REQUEST', v_transfer_request_id, JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            SET v_sequence = v_sequence + 1;
            CALL append_demo_history(
                v_item_id, v_order_id, v_sequence, 'CUSTOMER_REPLY_ADDED', 'PUBLIC',
                19003, 'demo_user', 'USER', NULL, NULL, NULL, NULL, NULL, NULL,
                'COMMENT', CONCAT('demo-comment-user-', v_item_id), JSON_OBJECT(),
                DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
            );

            IF v_current_admin_id IS NOT NULL THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'ADMIN_REPLY_ADDED', 'PUBLIC',
                    v_current_admin_id,
                    IF(v_current_admin_id = 19002, 'demo_admin', 'admin03'), 'ADMIN',
                    NULL, NULL, NULL, NULL, NULL, NULL,
                    'COMMENT', CONCAT('demo-comment-admin-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            IF v_current_admin_id = 19002 AND MOD(v_index, 2) = 0 THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'INTERNAL_NOTE_ADDED', 'STAFF_ONLY',
                    19002, 'demo_admin', 'ADMIN', NULL, NULL, NULL, NULL, NULL,
                    '已核对用户材料和关联订单，下一步按标准流程处理',
                    'COMMENT', CONCAT('demo-note-admin-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            SET v_reminder_index = 1;
            WHILE v_reminder_index <= v_reminder_count DO
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'REMINDER_SENT', 'PUBLIC',
                    19003, 'demo_user', 'USER', NULL, NULL, NULL, NULL, NULL, NULL,
                    'DEMO_SEED', CONCAT('demo-reminder-', v_item_id, '-', v_reminder_index),
                    JSON_OBJECT('reminder_count', v_reminder_index),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
                SET v_reminder_index = v_reminder_index + 1;
            END WHILE;

            IF v_status IN (2, 3) THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'STATUS_CHANGED', 'PUBLIC',
                    v_current_admin_id,
                    IF(v_current_admin_id = 19002, 'demo_admin', 'admin03'), 'ADMIN',
                    NULL, 0, 1, NULL, NULL, '开始核对客户提交的材料',
                    'DEMO_SEED', CONCAT('demo-status-processing-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            IF v_status IN (2, 3) THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'STATUS_CHANGED', 'PUBLIC',
                    v_current_admin_id,
                    IF(v_current_admin_id = 19002, 'demo_admin', 'admin03'), 'ADMIN',
                    NULL, 1, 2, NULL, NULL, '问题已处理并向客户反馈结果',
                    'DEMO_SEED', CONCAT('demo-status-completed-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            IF v_status IN (2, 3) THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'RATING_SUBMITTED', 'PUBLIC',
                    19003, 'demo_user', 'USER', NULL, NULL, NULL, NULL, NULL, NULL,
                    'COMMENT', CONCAT('demo-rating-user-', v_item_id),
                    JSON_OBJECT('rating', MOD(v_index, 5) + 1),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            IF v_status = 3 THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'STATUS_CHANGED', 'PUBLIC',
                    v_current_admin_id,
                    IF(v_current_admin_id = 19002, 'demo_admin', 'admin03'), 'ADMIN',
                    NULL, 2, 3, NULL, NULL, '客户确认处理结果后关闭工单',
                    'DEMO_SEED', CONCAT('demo-status-closed-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            ELSEIF v_status = 4 THEN
                SET v_sequence = v_sequence + 1;
                CALL append_demo_history(
                    v_item_id, v_order_id, v_sequence, 'STATUS_CHANGED', 'PUBLIC',
                    v_current_admin_id, 'demo_admin', 'ADMIN', NULL, 0, 4, NULL, NULL,
                    '客户确认问题已自行解决，取消本次申请',
                    'DEMO_SEED', CONCAT('demo-status-cancelled-', v_item_id), JSON_OBJECT(),
                    DATE_ADD(v_created_at, INTERVAL v_sequence HOUR)
                );
            END IF;

            UPDATE orders SET workflow_version = v_sequence WHERE order_id = v_order_id;
        END IF;

        SET v_index = v_index + 1;
    END WHILE;
END $$
DELIMITER ;

START TRANSACTION;
CALL seed_demo_scenarios();
COMMIT;

DROP PROCEDURE seed_demo_scenarios;
DROP PROCEDURE append_demo_history;

SELECT user_id, username, role, status, must_change_password
FROM users
WHERE user_id IN (19001, 19002, 19003)
ORDER BY user_id;

SELECT
    COUNT(*) AS demo_ticket_count,
    SUM(status = 0) AS pending_count,
    SUM(status = 1) AS processing_count,
    SUM(status = 2) AS completed_count,
    SUM(status = 3) AS closed_count,
    SUM(status = 4) AS cancelled_count,
    SUM(assigned_admin_id = 19002) AS assigned_to_demo_admin,
    SUM(assigned_admin_id IS NULL) AS unassigned_count,
    SUM(transfer_target_admin_id = 19002) AS pending_transfer_to_demo_admin
FROM orders
WHERE item_id BETWEEN 70001 AND 70060;
