const database = db.getSiblingDB("ticket_management_logs");

// P1 增量升级：只创建日志归档集合索引，不修改在线日志和工单数据。
database.action_logs_archive.createIndex({ created_at: 1 });
database.system_logs_archive.createIndex({ timestamp: 1 });
database.action_logs_archive.createIndex({ archived_at: 1 }, { expireAfterSeconds: 31536000 });
database.system_logs_archive.createIndex({ archived_at: 1 }, { expireAfterSeconds: 31536000 });
