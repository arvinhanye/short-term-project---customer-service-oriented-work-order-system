const database = db.getSiblingDB("ticket_management_logs");

database.comments.createIndex({ event_id: 1 }, { unique: true, sparse: true });

database.item_details.createIndex({ "metadata.assigned_admin_id": 1 });
database.item_details.createIndex({ "metadata.transfer_target_admin_id": 1 });
database.item_details.createIndex({ "metadata.last_reminded_at": 1 });
database.item_details.updateMany(
  { "metadata.reminder_count": { $exists: false } },
  { $set: { "metadata.reminder_count": 0 } }
);

// 将历史客服回复标签无损改名为 ADMIN_REPLY；可重复执行。
database.comments.updateMany(
  { tags: "AGENT_REPLY" },
  [
    {
      $set: {
        tags: {
          $map: {
            input: { $ifNull: ["$tags", []] },
            as: "tag",
            in: { $cond: [{ $eq: ["$$tag", "AGENT_REPLY"] }, "ADMIN_REPLY", "$$tag"] }
          }
        }
      }
    }
  ]
);

printjson({
  legacy_agent_reply_count: database.comments.countDocuments({ tags: "AGENT_REPLY" }),
  admin_reply_count: database.comments.countDocuments({ tags: "ADMIN_REPLY" })
});
