package com.ticket.service;

import com.ticket.dao.mongo.SystemLogDAO;
import com.ticket.model.SystemLog;
import java.time.Instant;

public class AuditLogService {
    private final SystemLogDAO systemLogDAO;

    public AuditLogService() {
        this(new SystemLogDAO());
    }

    public AuditLogService(SystemLogDAO systemLogDAO) {
        this.systemLogDAO = systemLogDAO;
    }

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
        systemLogDAO.insert(log);
    }
}
