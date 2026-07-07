USE ticket_management;

CREATE INDEX idx_items_category_created_at ON items (category_id, created_at);
CREATE FULLTEXT INDEX ft_items_title ON items (title);

DROP INDEX idx_orders_item_id ON orders;
CREATE UNIQUE INDEX uk_orders_item_id ON orders (item_id);
CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at);
CREATE INDEX idx_orders_user_status_created_at ON orders (user_id, status, created_at);
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);

EXPLAIN SELECT *
FROM orders
WHERE user_id = 10004
ORDER BY created_at DESC
LIMIT 20;

EXPLAIN SELECT *
FROM orders
WHERE user_id = 10004
  AND status = 1
ORDER BY created_at DESC
LIMIT 20;

EXPLAIN SELECT *
FROM orders
WHERE status = 1
ORDER BY created_at DESC
LIMIT 20;
