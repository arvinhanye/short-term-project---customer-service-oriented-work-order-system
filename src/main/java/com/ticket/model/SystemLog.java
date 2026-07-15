package com.ticket.model;

import java.time.Instant;

public class SystemLog {
    private String userId;
    private String logType;
    private String logLevel;
    private String message;
    private ActionDetail actionDetail = new ActionDetail();
    private Instant timestamp;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ActionDetail getActionDetail() {
        return actionDetail;
    }

    public void setActionDetail(ActionDetail actionDetail) {
        this.actionDetail = actionDetail;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public static class ActionDetail {
        private String ip;
        private String operation;
        private String targetUserId;
        private String beforeRole;
        private String afterRole;
        private String reason;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(String targetUserId) {
            this.targetUserId = targetUserId;
        }

        public String getBeforeRole() {
            return beforeRole;
        }

        public void setBeforeRole(String beforeRole) {
            this.beforeRole = beforeRole;
        }

        public String getAfterRole() {
            return afterRole;
        }

        public void setAfterRole(String afterRole) {
            this.afterRole = afterRole;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
