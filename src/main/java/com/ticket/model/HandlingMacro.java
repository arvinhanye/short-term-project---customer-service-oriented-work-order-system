package com.ticket.model;

public class HandlingMacro {
    private Long macroId;
    private String macroName;
    private ReplyTemplate replyTemplate;
    private Integer targetStatus;
    private boolean enabled;

    public Long getMacroId() { return macroId; }
    public void setMacroId(Long value) { macroId = value; }
    public String getMacroName() { return macroName; }
    public void setMacroName(String value) { macroName = value; }
    public ReplyTemplate getReplyTemplate() { return replyTemplate; }
    public void setReplyTemplate(ReplyTemplate value) { replyTemplate = value; }
    public Integer getTargetStatus() { return targetStatus; }
    public void setTargetStatus(Integer value) { targetStatus = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    @Override public String toString() { return macroName; }
}
