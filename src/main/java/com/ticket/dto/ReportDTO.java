package com.ticket.dto;

import java.math.BigDecimal;

public class ReportDTO {
    private String label;
    private long count;
    private BigDecimal amount;

    public ReportDTO() {
    }

    public ReportDTO(String label, long count, BigDecimal amount) {
        this.label = label;
        this.count = count;
        this.amount = amount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
