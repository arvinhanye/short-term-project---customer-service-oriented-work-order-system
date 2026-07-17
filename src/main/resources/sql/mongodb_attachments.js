// Idempotent upgrade for existing databases. GridFS collections are created on first upload.
const database = db.getSiblingDB("ticket_management_logs");

database.comments.createIndex({ "attachments.file_id": 1 });
