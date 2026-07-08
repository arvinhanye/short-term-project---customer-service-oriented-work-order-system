package com.ticket.service;

import com.ticket.dao.mongo.LogDAO;
import com.ticket.model.ActionLog;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionLogService.class);
    private final LogDAO logDAO;

    public ActionLogService() {
        this(new LogDAO());
    }

    public ActionLogService(LogDAO logDAO) {
        this.logDAO = logDAO;
    }

    public void write(String userId, String itemId, String actionType) {
        ActionLog log = new ActionLog();
        log.setUserId(userId);
        log.setItemId(itemId);
        log.setActionType(actionType);
        log.setDurationSeconds("0");
        ActionLog.ClientInfo clientInfo = new ActionLog.ClientInfo();
        clientInfo.setClientType("SWING");
        clientInfo.setIp("127.0.0.1");
        log.setClientInfo(clientInfo);
        log.setCreatedAt(Instant.now());
        LOGGER.info("ACTION userId={} itemId={} actionType={}", userId, itemId, actionType);
        try {
            logDAO.insert(log);
        } catch (Exception ex) {
            LOGGER.error("Failed to persist action log to MongoDB, actionType={}", actionType, ex);
        }
    }
}
