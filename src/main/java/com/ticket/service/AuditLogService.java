package com.ticket.service;

import com.ticket.dao.mongo.SystemLogDAO;
import com.ticket.model.SystemLog;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);
    private final SystemLogDAO systemLogDAO;
    private final MongoLogRetryService retryService;

    public AuditLogService() {
        this(new SystemLogDAO());
    }

    public AuditLogService(SystemLogDAO systemLogDAO) {
        this.systemLogDAO = systemLogDAO;
        this.retryService = new MongoLogRetryService();
    }

    /** MongoDB 不可用时，日志会转入 MySQL 持久化重试队列。 */
    public void write(String userId, String logType, String level, String message, String operation) {
        SystemLog log = new SystemLog();
        log.setUserId(userId);
        log.setLogType(logType);
        log.setLogLevel(level);
        log.setMessage(message);
        SystemLog.ActionDetail actionDetail = new SystemLog.ActionDetail();
        actionDetail.setIp("127.0.0.1");
        actionDetail.setOperation(operation);
        log.setActionDetail(actionDetail);
        log.setTimestamp(Instant.now());
        LOGGER.info("AUDIT userId={} type={} level={} operation={} message={}",
            userId, logType, level, operation, message);
        try {
            systemLogDAO.insert(log);
        } catch (Exception ex) {
            retryService.recordSystemFailure(log, ex);
        }
    }
}
