package com.ticket.service;

import com.ticket.dao.mongo.LogDAO;
import com.ticket.model.ActionLog;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionLogService.class);
    private final LogDAO logDAO;
    private final MongoLogRetryService retryService;

    public ActionLogService() {
        this(new LogDAO());
    }

    public ActionLogService(LogDAO logDAO) {
        this.logDAO = logDAO;
        this.retryService = new MongoLogRetryService();
    }

    /** MongoDB 不可用时，日志会转入 MySQL 持久化重试队列。 */
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
            retryService.recordActionFailure(log, ex);
        }
    }
}
