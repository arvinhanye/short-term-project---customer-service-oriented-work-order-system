USE ticket_management;

-- 无损修复历史三级分类：先迁移工单，再删除已经为空的异常分类。
START TRANSACTION;

SET @abnormal_category_id := 4006;
SET @fallback_category_id := 4007;

-- 分类变化同样进入工单历史，并与订单工作流版本保持一致。
UPDATE orders order_record
JOIN items affected_item ON affected_item.item_id = order_record.item_id
SET order_record.workflow_version = order_record.workflow_version + 1
WHERE affected_item.category_id = @abnormal_category_id
  AND NOT EXISTS (
      SELECT 1 FROM ticket_history history
      WHERE history.item_id = order_record.item_id
        AND history.source_type = 'CATEGORY_MIGRATION'
        AND BINARY history.source_id = BINARY CONCAT(
            'category-cleanup:', @abnormal_category_id, ':', order_record.item_id
        )
  );

INSERT INTO ticket_history (
    event_id, item_id, order_id, event_seq, event_type, visibility,
    reason, source_type, source_id, event_payload, occurred_at
)
SELECT UUID(), order_record.item_id, order_record.order_id, order_record.workflow_version,
       'CATEGORY_REASSIGNED', 'PUBLIC',
       '清理历史三级分类，工单迁移到合法父分类',
       'CATEGORY_MIGRATION', CONCAT('category-cleanup:', @abnormal_category_id, ':', order_record.item_id),
       JSON_OBJECT('from_category_id', @abnormal_category_id, 'to_category_id', @fallback_category_id),
       CURRENT_TIMESTAMP(3)
FROM orders order_record
JOIN items affected_item ON affected_item.item_id = order_record.item_id
WHERE affected_item.category_id = @abnormal_category_id
  AND NOT EXISTS (
      SELECT 1 FROM ticket_history history
      WHERE history.item_id = order_record.item_id
        AND history.source_type = 'CATEGORY_MIGRATION'
        AND BINARY history.source_id = BINARY CONCAT(
            'category-cleanup:', @abnormal_category_id, ':', order_record.item_id
        )
  );

UPDATE items
SET category_id = @fallback_category_id,
    updated_at = CURRENT_TIMESTAMP
WHERE category_id = @abnormal_category_id;

DELETE abnormal
FROM categories abnormal
LEFT JOIN categories child ON child.parent_id = abnormal.category_id
LEFT JOIN items linked_item ON linked_item.category_id = abnormal.category_id
WHERE abnormal.category_id = @abnormal_category_id
  AND child.category_id IS NULL
  AND linked_item.item_id IS NULL;

COMMIT;

SELECT category_id, name, parent_id
FROM categories
ORDER BY COALESCE(parent_id, category_id), category_id;
