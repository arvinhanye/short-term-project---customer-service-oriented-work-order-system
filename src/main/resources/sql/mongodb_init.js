const database = db.getSiblingDB("ticket_management_logs");

database.action_logs.createIndex({ user_id: 1 });
database.action_logs.createIndex({ item_id: 1 });
database.action_logs.createIndex({ action_type: 1 });
database.action_logs.createIndex({ created_at: 1 });
database.action_logs.createIndex({ user_id: 1, created_at: -1 });
database.action_logs.createIndex({ item_id: 1, created_at: -1 });
database.action_logs.createIndex({ action_type: 1, created_at: -1 });
database.action_logs.createIndex({ "client_info.client_type": 1 });

database.comments.createIndex({ item_id: 1, created_at: 1 });
database.comments.createIndex({ user_id: 1 });
database.comments.createIndex({ tags: 1 });
database.comments.createIndex({ user_id: 1, created_at: -1 });
database.comments.createIndex({ tags: 1, created_at: -1 });
database.comments.createIndex({ rating: 1 });
database.comments.createIndex({ event_id: 1 }, { unique: true, sparse: true });

database.item_details.createIndex({ item_id: 1 }, { unique: true });
database.item_details.createIndex({ "metadata.assigned_admin_id": 1 });
database.item_details.createIndex({ "metadata.transfer_target_admin_id": 1 });
database.item_details.createIndex({ "metadata.last_reminded_at": 1 });

database.system_logs.createIndex({ user_id: 1 });
database.system_logs.createIndex({ log_type: 1 });
database.system_logs.createIndex({ log_level: 1 });
database.system_logs.createIndex({ timestamp: -1 });
database.system_logs.createIndex({ log_type: 1, timestamp: -1 });
database.system_logs.createIndex({ log_level: 1, timestamp: -1 });
database.system_logs.createIndex({ user_id: 1, timestamp: -1 });
database.system_logs.createIndex({ "action_detail.target_user_id": 1, timestamp: -1 });

const detailSeeds = [];
const adminIds = ["10003", "10011", "10012"];
for (let i = 1; i <= 20; i += 1) {
  const itemId = String(2000 + i);
  const creatorId = String(10004 + ((i - 1) % 7));
  const assignedAdmin = i % 6 === 0 ? null : adminIds[(i - 1) % adminIds.length];
  const priorities = ["LOW", "MEDIUM", "HIGH", "URGENT"];
  detailSeeds.push({
    item_id: itemId,
    description: `工单 ${itemId} 的详细描述，包含复现步骤、业务背景和用户补充信息。`,
    images: [
      `/attachments/${itemId}/screenshot-1.png`,
      `/attachments/${itemId}/screenshot-2.png`
    ],
    metadata: {
      language: "zh-CN",
      priority: priorities[(i - 1) % priorities.length],
      created_by_user_id: creatorId,
      assigned_admin_id: assignedAdmin,
      transfer_requested_by_admin_id: null,
      transfer_target_admin_id: null,
      transfer_reason: null,
      transfer_requested_at: null,
      reminder_count: 0,
      last_reminded_at: null,
      contact_channel: "DESKTOP",
      last_processed_at: new Date(`2026-03-${String(((i - 1) % 10) + 1).padStart(2, "0")}T09:00:00Z`)
    }
  });
}

detailSeeds.forEach((doc) => {
  database.item_details.updateOne(
    { item_id: doc.item_id },
    { $set: doc },
    { upsert: true }
  );
});

const commentTypes = [
  { tag: "CUSTOMER_REPLY", rating: "" },
  { tag: "ADMIN_REPLY", rating: "" },
  { tag: "INTERNAL_NOTE", rating: "" },
  { tag: "CUSTOMER_RATING", rating: "5" }
];

for (let i = 1; i <= 20; i += 1) {
  const itemId = String(2000 + i);
  const variant = commentTypes[(i - 1) % commentTypes.length];
  const userId = variant.tag === "ADMIN_REPLY" || variant.tag === "INTERNAL_NOTE"
    ? adminIds[(i - 1) % adminIds.length]
    : String(10004 + ((i - 1) % 7));

  database.comments.updateOne(
    { item_id: itemId, tags: [variant.tag] },
    {
      $set: {
        user_id: userId,
        item_id: itemId,
        content: `${variant.tag} for item ${itemId}`,
        rating: variant.tag === "CUSTOMER_RATING" ? String(((i - 1) % 5) + 1) : "",
        tags: [variant.tag],
        created_at: new Date(`2026-04-${String(((i - 1) % 10) + 1).padStart(2, "0")}T08:00:00Z`)
      }
    },
    { upsert: true }
  );
}

for (let i = 1; i <= 100; i += 1) {
  const itemId = String(2001 + ((i - 1) % 20));
  const userId = String(10001 + ((i - 1) % 10));
  const actions = ["CREATE_ITEM", "VIEW", "SEARCH", "UPDATE_PROFILE", "ADD_COMMENT", "CHANGE_STATUS", "ASSIGN", "RATE", "LOGIN", "LOGOUT"];
  const action = actions[(i - 1) % actions.length];

  database.action_logs.updateOne(
    { user_id: userId, item_id: itemId, action_type: action, created_at: new Date(`2026-05-${String(((i - 1) % 28) + 1).padStart(2, "0")}T07:00:00Z`) },
    {
      $set: {
        user_id: userId,
        item_id: itemId,
        action_type: action,
        duration_seconds: String(30 + (i % 180)),
        client_info: {
          client_type: "SWING",
          ip: "127.0.0.1"
        },
        created_at: new Date(`2026-05-${String(((i - 1) % 28) + 1).padStart(2, "0")}T07:00:00Z`)
      }
    },
    { upsert: true }
  );
}

for (let i = 1; i <= 100; i += 1) {
  const userId = String(10001 + ((i - 1) % 10));
  const logTypes = ["LOGIN", "LOGIN_FAIL", "DB_ERROR", "USER_DISABLED", "ITEM_DELETE", "STATUS_CHANGE", "ADMIN_OPERATION", "CROSS_DB_FAIL", "TX_ROLLBACK", "SYSTEM_STARTUP"];
  const levels = ["INFO", "WARN", "ERROR"];
  const type = logTypes[(i - 1) % logTypes.length];

  database.system_logs.updateOne(
    { user_id: userId, log_type: type, timestamp: new Date(`2026-06-${String(((i - 1) % 28) + 1).padStart(2, "0")}T06:00:00Z`) },
    {
      $set: {
        user_id: userId,
        log_type: type,
        log_level: levels[(i - 1) % levels.length],
        message: `${type} event captured for user ${userId}`,
        action_detail: {
          ip: "127.0.0.1",
          operation: type
        },
        timestamp: new Date(`2026-06-${String(((i - 1) % 28) + 1).padStart(2, "0")}T06:00:00Z`)
      }
    },
    { upsert: true }
  );
}
