package com.ticket.service;

import com.ticket.dao.mongo.DetailDAO;
import com.ticket.dao.mysql.CrossDatabaseRepairDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 处理 MySQL 回滚后未能立即删除 MongoDB 详情的补偿任务。 */
public class CrossDatabaseRepairService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossDatabaseRepairService.class);
    private final CrossDatabaseRepairDAO repairDAO = new CrossDatabaseRepairDAO();
    private final DetailDAO detailDAO = new DetailDAO();

    public void recordDeleteItemDetailFailure(long itemId, Exception cause) {
        try {
            repairDAO.enqueueDeleteItemDetail(itemId, cause.toString());
            LOGGER.warn("Cross-database compensation queued, itemId={}", itemId, cause);
        } catch (Exception queueFailure) {
            // 不能掩盖原始的创建工单失败；本地日志保留人工修复所需上下文。
            LOGGER.error("Cross-database compensation and durable repair queue both failed, itemId={}", itemId, queueFailure);
        }
    }

    public void retryPending() {
        for (CrossDatabaseRepairDAO.RepairRecord record : repairDAO.findPending(200)) {
            try {
                if (!"DELETE_ITEM_DETAIL".equals(record.repairType())) {
                    throw new IllegalStateException("Unsupported repair type: " + record.repairType());
                }
                detailDAO.deleteByItemId(String.valueOf(record.itemId()));
                repairDAO.markDone(record.repairId());
            } catch (Exception ex) {
                repairDAO.markFailed(record.repairId(), ex.toString());
                LOGGER.warn("Cross-database compensation retry failed, repairId={}", record.repairId(), ex);
            }
        }
    }
}
