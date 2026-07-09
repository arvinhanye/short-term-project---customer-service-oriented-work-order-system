USE ticket_management;

SET @hana_id := (SELECT user_id FROM users WHERE username = 'hana' LIMIT 1);

INSERT INTO items (item_id, title, category_id, status, created_at, updated_at) VALUES
    (9101, 'Hana测试：无法收到验证码', 4005, 0, '2026-07-01 09:10:00', '2026-07-01 09:10:00'),
    (9102, 'Hana测试：实名认证审核卡住', 4006, 1, '2026-07-02 10:20:00', '2026-07-02 11:05:00'),
    (9103, 'Hana测试：退款迟迟未到账', 4007, 2, '2026-07-03 11:30:00', '2026-07-03 15:40:00'),
    (9104, 'Hana测试：订单重复扣费', 4008, 3, '2026-07-04 12:40:00', '2026-07-04 16:20:00'),
    (9105, 'Hana测试：页面提交后报错', 4009, 4, '2026-07-05 13:50:00', '2026-07-05 14:05:00'),
    (9106, 'Hana测试：导出按钮无响应', 4010, 0, '2026-07-06 15:00:00', '2026-07-06 15:00:00'),
    (9107, 'Hana测试：订单状态未同步', 4004, 1, '2026-07-07 16:10:00', '2026-07-07 17:30:00'),
    (9108, 'Hana测试：支付成功未生成发票', 4002, 2, '2026-07-08 17:20:00', '2026-07-08 18:10:00')
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    category_id = VALUES(category_id),
    status = VALUES(status),
    created_at = VALUES(created_at),
    updated_at = VALUES(updated_at);

INSERT INTO orders (order_id, user_id, item_id, amount, status, created_at) VALUES
    (9201, @hana_id, 9101, 0.00, 0, '2026-07-01 09:12:00'),
    (9202, @hana_id, 9102, 0.00, 1, '2026-07-02 10:22:00'),
    (9203, @hana_id, 9103, 88.80, 2, '2026-07-03 11:32:00'),
    (9204, @hana_id, 9104, 128.00, 3, '2026-07-04 12:42:00'),
    (9205, @hana_id, 9105, 0.00, 4, '2026-07-05 13:52:00'),
    (9206, @hana_id, 9106, 0.00, 0, '2026-07-06 15:02:00'),
    (9207, @hana_id, 9107, 39.90, 1, '2026-07-07 16:12:00'),
    (9208, @hana_id, 9108, 299.00, 2, '2026-07-08 17:22:00')
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    item_id = VALUES(item_id),
    amount = VALUES(amount),
    status = VALUES(status),
    created_at = VALUES(created_at);
