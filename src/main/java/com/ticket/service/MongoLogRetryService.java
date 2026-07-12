package com.ticket.service;

import com.ticket.dao.mongo.LogDAO;
import com.ticket.dao.mongo.SystemLogDAO;
import com.ticket.dao.mysql.PendingMongoWriteDAO;
import com.ticket.model.ActionLog;
import com.ticket.model.SystemLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 将失败的 MongoDB 日志持久化到 MySQL，并在启动时尽力重放。 */
public class MongoLogRetryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoLogRetryService.class);
    private final PendingMongoWriteDAO pendingDAO = new PendingMongoWriteDAO();
    private final LogDAO logDAO = new LogDAO();
    private final SystemLogDAO systemLogDAO = new SystemLogDAO();

    public boolean recordActionFailure(ActionLog log, Exception cause) {
        return persist(() -> pendingDAO.enqueueAction(log, cause.toString()), "ACTION", cause);
    }

    public boolean recordSystemFailure(SystemLog log, Exception cause) {
        return persist(() -> pendingDAO.enqueueSystem(log, cause.toString()), "SYSTEM", cause);
    }

    public void retryPending() {
        for (PendingMongoWriteDAO.PendingWrite write : pendingDAO.findPending(200)) {
            try {
                if ("ACTION".equals(write.writeType())) {
                    ActionLog log = new ActionLog();
                    log.setUserId(write.userId());
                    log.setItemId(write.itemId());
                    log.setActionType(write.logType());
                    log.setDurationSeconds("0");
                    ActionLog.ClientInfo client = new ActionLog.ClientInfo();
                    client.setClientType(write.clientType());
                    client.setIp(write.ip());
                    log.setClientInfo(client);
                    log.setCreatedAt(write.occurredAt());
                    logDAO.insert(log);
                } else {
                    SystemLog log = new SystemLog();
                    log.setUserId(write.userId());
                    log.setLogType(write.logType());
                    log.setLogLevel(write.logLevel());
                    log.setMessage(write.message());
                    SystemLog.ActionDetail detail = new SystemLog.ActionDetail();
                    detail.setIp(write.ip());
                    detail.setOperation(write.operation());
                    log.setActionDetail(detail);
                    log.setTimestamp(write.occurredAt());
                    systemLogDAO.insert(log);
                }
                pendingDAO.markDone(write.retryId());
            } catch (Exception ex) {
                pendingDAO.markFailed(write.retryId(), ex.toString());
                LOGGER.warn("MongoDB log replay failed, retryId={}", write.retryId(), ex);
            }
        }
    }

    private boolean persist(Runnable action, String type, Exception cause) {
        try {
            action.run();
            LOGGER.warn("MongoDB {} log queued for retry", type, cause);
            return true;
        } catch (Exception queueFailure) {
            LOGGER.error("MongoDB {} log and durable retry queue both failed", type, queueFailure);
            return false;
        }
    }
}
