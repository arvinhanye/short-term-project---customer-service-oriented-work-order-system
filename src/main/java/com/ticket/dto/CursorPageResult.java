package com.ticket.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 基于 created_at + order_id 的稳定游标分页结果。 */
public class CursorPageResult<T> {
    private final List<T> records;
    private final long total;
    private final LocalDateTime nextCreatedAt;
    private final Long nextOrderId;

    public CursorPageResult(List<T> records, long total, LocalDateTime nextCreatedAt, Long nextOrderId) {
        this.records = records;
        this.total = total;
        this.nextCreatedAt = nextCreatedAt;
        this.nextOrderId = nextOrderId;
    }

    public List<T> getRecords() { return records; }
    public long getTotal() { return total; }
    public LocalDateTime getNextCreatedAt() { return nextCreatedAt; }
    public Long getNextOrderId() { return nextOrderId; }
    public boolean hasNext() { return nextCreatedAt != null && nextOrderId != null; }
}
