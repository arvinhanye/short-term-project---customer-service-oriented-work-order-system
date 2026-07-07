package com.ticket.service;

import com.ticket.dao.mongo.LogDAO;
import com.ticket.dao.mongo.SystemLogDAO;
import com.ticket.model.ActionLog;
import com.ticket.model.SystemLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LogServiceTest {
    @Test
    void shouldBuildActionLogWithDesktopClient() {
        CapturingLogDAO logDAO = new CapturingLogDAO();
        ActionLogService service = new ActionLogService(logDAO);

        service.write("10004", "2001", "VIEW");

        Assertions.assertEquals("10004", logDAO.lastLog.getUserId());
        Assertions.assertEquals("2001", logDAO.lastLog.getItemId());
        Assertions.assertEquals("VIEW", logDAO.lastLog.getActionType());
        Assertions.assertEquals("0", logDAO.lastLog.getDurationSeconds());
        Assertions.assertEquals("SWING", logDAO.lastLog.getClientInfo().getClientType());
        Assertions.assertNotNull(logDAO.lastLog.getCreatedAt());
    }

    @Test
    void shouldBuildAuditLogWithOperationDetail() {
        CapturingSystemLogDAO systemLogDAO = new CapturingSystemLogDAO();
        AuditLogService service = new AuditLogService(systemLogDAO);

        service.write("10001", "STATUS_CHANGE", "INFO", "工单状态已更新", "CHANGE_STATUS");

        Assertions.assertEquals("10001", systemLogDAO.lastLog.getUserId());
        Assertions.assertEquals("STATUS_CHANGE", systemLogDAO.lastLog.getLogType());
        Assertions.assertEquals("INFO", systemLogDAO.lastLog.getLogLevel());
        Assertions.assertEquals("工单状态已更新", systemLogDAO.lastLog.getMessage());
        Assertions.assertEquals("CHANGE_STATUS", systemLogDAO.lastLog.getActionDetail().getOperation());
        Assertions.assertNotNull(systemLogDAO.lastLog.getTimestamp());
    }

    private static class CapturingLogDAO extends LogDAO {
        private ActionLog lastLog;

        @Override
        public void insert(ActionLog log) {
            this.lastLog = log;
        }
    }

    private static class CapturingSystemLogDAO extends SystemLogDAO {
        private SystemLog lastLog;

        @Override
        public void insert(SystemLog log) {
            this.lastLog = log;
        }
    }
}
