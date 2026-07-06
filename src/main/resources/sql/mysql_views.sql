USE ticket_management;

DROP VIEW IF EXISTS v_user_detail;
CREATE VIEW v_user_detail AS
SELECT
    u.user_id,
    u.username,
    u.email,
    u.phone,
    u.role,
    u.status,
    u.created_at,
    u.updated_at,
    p.profile_id,
    p.real_name,
    p.id_card,
    p.address,
    p.notes
FROM users u
LEFT JOIN profiles p ON u.user_id = p.user_id;

DROP VIEW IF EXISTS v_business_summary;
CREATE VIEW v_business_summary AS
SELECT
    i.item_id,
    i.title,
    c.name AS category_name,
    u.username,
    o.status AS business_status,
    o.amount,
    o.created_at
FROM items i
INNER JOIN categories c ON i.category_id = c.category_id
INNER JOIN orders o ON i.item_id = o.item_id
INNER JOIN users u ON o.user_id = u.user_id;
