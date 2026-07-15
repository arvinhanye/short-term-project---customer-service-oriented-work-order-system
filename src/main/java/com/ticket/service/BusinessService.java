package com.ticket.service;

import com.ticket.dao.mongo.CommentDAO;
import com.ticket.dao.mongo.DetailDAO;
import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dao.mysql.ItemDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.dao.mysql.ProfileDAO;
import com.ticket.dao.mysql.UserDAO;
import com.ticket.dto.ItemDetailDTO;
import com.ticket.dto.PageResult;
import com.ticket.exception.BusinessException;
import com.ticket.exception.DBException;
import com.ticket.model.Comment;
import com.ticket.model.Category;
import com.ticket.model.Item;
import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import com.ticket.model.TicketHistory;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import com.ticket.util.WorkflowMetadataUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.bson.Document;

public class BusinessService {
    static final int REMINDER_COOLDOWN_MINUTES = 30;
    private final ItemDAO itemDAO = new ItemDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO = new UserDAO();
    private final ProfileDAO profileDAO = new ProfileDAO();
    private final DetailDAO detailDAO = new DetailDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final ActionLogService actionLogService = new ActionLogService();
    private final AuditLogService auditLogService = new AuditLogService();
    private final CrossDatabaseRepairService repairService = new CrossDatabaseRepairService();
    private final TicketHistoryService ticketHistoryService = new TicketHistoryService();

    public long createTicket(User actor, String title, Long categoryId, BigDecimal amount, String description, String priority) {
        UserService.requireActiveUser(actor);
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new BusinessException("工单标题不合法");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessException("问题描述不能为空");
        }
        if (description.length() > 4000) {
            throw new BusinessException("工单描述过长");
        }
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("金额不能为负数");
        }
        if (amount.scale() > 2) {
            throw new BusinessException("金额最多保留 2 位小数");
        }
        Category category = categoryId == null ? null : categoryDAO.findById(categoryId);
        if (category == null) {
            throw new BusinessException("工单分类不存在");
        }
        if (category.getParentId() != null) {
            Category parent = categoryDAO.findById(category.getParentId());
            if (parent == null || parent.getParentId() != null) {
                throw new BusinessException("该分类层级异常，请选择一级或二级分类");
            }
        }
        String normalizedPriority = validatePriority(priority);
        Item item = new Item();
        item.setTitle(title.trim());
        item.setCategoryId(categoryId);
        item.setStatus(0);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        Order order = new Order();
        order.setUserId(actor.getUserId());
        order.setAmount(amount);
        order.setStatus(0);
        order.setWorkflowVersion(1);
        order.setCreatedAt(LocalDateTime.now());

        Long itemId = null;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                itemId = itemDAO.insert(connection, item);
                order.setItemId(itemId);
                order.setOrderId(orderDAO.insert(connection, order));
                ItemDetail detail = buildDetail(itemId, actor.getUserId(), description, normalizedPriority);
                detailDAO.upsert(detail);
                TicketHistory history = ticketHistoryService.event(
                    order, actor, "TICKET_CREATED", TicketHistoryService.PUBLIC, 1);
                history.setToStatus(0);
                history.setEventPayload(new Document("title", item.getTitle())
                    .append("priority", normalizedPriority).toJson());
                ticketHistoryService.append(connection, history);
                connection.commit();
                actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "CREATE_ITEM");
                auditLogService.write(String.valueOf(actor.getUserId()), "TICKET_OPERATION", "INFO",
                    "创建工单：" + item.getTitle(), "CREATE_ITEM");
                return itemId;
            } catch (Exception ex) {
                connection.rollback();
                if (itemId != null) {
                    try {
                        detailDAO.deleteByItemId(String.valueOf(itemId));
                    } catch (Exception compensationFailure) {
                        repairService.recordDeleteItemDetailFailure(itemId, compensationFailure);
                    }
                }
                auditLogService.write(String.valueOf(actor.getUserId()), "CROSS_DB_FAIL", "ERROR", "创建工单失败", "CREATE_ITEM");
                throw ex instanceof BusinessException ? (BusinessException) ex : new BusinessException("创建工单失败", ex);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof DBException ? (DBException) ex : new BusinessException("创建工单失败", ex);
        }
    }

    public PageResult<Order> pageMyOrders(User actor, Integer status, int page, int pageSize) {
        UserService.requireActiveUser(actor);
        actionLogService.write(String.valueOf(actor.getUserId()), null, "SEARCH");
        return orderDAO.pageByUserAndStatus(actor.getUserId(), status, page, pageSize);
    }

    public ItemDetailDTO getTicketDetail(User actor, Long itemId) {
        UserService.requireActiveUser(actor);
        Item item = itemDAO.findById(itemId);
        if (item == null) {
            throw new BusinessException("工单不存在");
        }
        Order order = orderDAO.findByItemId(itemId);
        if (!UserService.isTicketStaff(actor) && !actor.getUserId().equals(order.getUserId())) {
            throw new BusinessException("无权查看该工单");
        }
        ItemDetailDTO dto = new ItemDetailDTO();
        dto.setItem(item);
        dto.setOrder(order);
        dto.setUser(userDAO.findById(order.getUserId()));
        dto.setProfile(profileDAO.findByUserId(order.getUserId()));
        dto.setItemDetail(detailDAO.findByItemId(String.valueOf(itemId)));
        WorkflowMetadataUtil.apply(order, dto.getItemDetail());
        dto.setComments(commentDAO.findByItemId(String.valueOf(itemId), UserService.isTicketStaff(actor)));
        dto.setHistories(ticketHistoryService.listForTicket(actor, itemId, 500));
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "VIEW");
        return dto;
    }

    public void addCustomerReply(User actor, Long itemId, String content) {
        addComment(actor, itemId, content, "CUSTOMER_REPLY", null, false);
    }

    public void addAdminReply(User actor, Long itemId, String content) {
        UserService.requireTicketStaff(actor);
        addComment(actor, itemId, content, "ADMIN_REPLY", null, true);
    }

    public void addInternalNote(User actor, Long itemId, String content) {
        UserService.requireTicketStaff(actor);
        addComment(actor, itemId, content, "INTERNAL_NOTE", null, true);
    }

    public void rateTicket(User actor, Long itemId, int rating, String content) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("评分必须在 1 到 5 之间");
        }
        addComment(actor, itemId, content, "CUSTOMER_RATING", String.valueOf(rating), false);
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "RATE");
    }

    public void urgeTicket(User actor, Long itemId) {
        actor = requireFreshTicketUser(actor);
        LocalDateTime now = LocalDateTime.now();
        Order order;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                order = orderDAO.findByItemIdForUpdate(connection, itemId);
                if (order == null || !actor.getUserId().equals(order.getUserId())) {
                    throw new BusinessException("只能催促自己提交的工单");
                }
                requireReminderEligibleStatus(order.getStatus());
                int updated = orderDAO.recordReminder(connection, order, actor.getUserId(),
                    now.minusMinutes(REMINDER_COOLDOWN_MINUTES), now);
                if (updated != 1) {
                    throw new BusinessException("催促过于频繁，请在 " + REMINDER_COOLDOWN_MINUTES + " 分钟后再试");
                }
                TicketHistory history = ticketHistoryService.event(order, actor, "REMINDER_SENT",
                    TicketHistoryService.PUBLIC, order.getWorkflowVersion() + 1);
                history.setEventPayload(new Document("reminder_count", order.getReminderCount() + 1).toJson());
                ticketHistoryService.append(connection, history);
                connection.commit();
                order.setReminderCount(order.getReminderCount() + 1);
                order.setLastRemindedAt(now);
                order.setWorkflowVersion(order.getWorkflowVersion() + 1);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof BusinessException businessException ? businessException
                : new BusinessException("催促工单失败", ex);
        }
        syncWorkflowMirror(actor, order, "REMINDER_SENT");
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "URGE_TICKET");
        auditLogService.write(String.valueOf(actor.getUserId()), "TICKET_REMINDER", "WARN",
            "用户催促工单=" + itemId, "URGE_TICKET");
    }

    public void changeOrderStatus(User actor, Long orderId, int newStatus) {
        actor = requireFreshTicketAdmin(actor);
        Order order;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                order = orderDAO.findByIdForUpdate(connection, orderId);
                if (order == null) {
                    throw new BusinessException("工单记录不存在");
                }
                requireAssignedAdminCanProcess(actor, order);
                if (order.getTransferRequestId() != null) {
                    throw new BusinessException("当前存在待确认的接手邀请，请先处理后再流转状态");
                }
                validateStatusTransition(order.getStatus(), newStatus);
                int updated = orderDAO.updateStatusIfCurrent(connection, order, actor.getUserId(), newStatus);
                if (updated != 1) {
                    throw new BusinessException("工单状态已被其他操作更新，请刷新后重试");
                }
                TicketHistory history = ticketHistoryService.event(order, actor, "STATUS_CHANGED",
                    TicketHistoryService.PUBLIC, order.getWorkflowVersion() + 1);
                history.setFromStatus(order.getStatus());
                history.setToStatus(newStatus);
                ticketHistoryService.append(connection, history);
                connection.commit();
                order.setStatus(newStatus);
                order.setWorkflowVersion(order.getWorkflowVersion() + 1);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof BusinessException businessException
                ? businessException : new BusinessException("更新工单状态失败", ex);
        }
        syncWorkflowMirror(actor, order, "STATUS_CHANGED");
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(order.getItemId()), "CHANGE_STATUS");
        auditLogService.write(String.valueOf(actor.getUserId()), "STATUS_CHANGE", "INFO", "工单状态已更新", "CHANGE_STATUS");
    }

    public void claimTicket(User actor, Long itemId) {
        actor = requireFreshTicketAdmin(actor);
        Order order;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                order = orderDAO.findByItemIdForUpdate(connection, itemId);
                requireAssignableOrder(order);
                if (order.getAssignedAdminId() != null) {
                    throw new BusinessException(order.getAssignedAdminId().equals(actor.getUserId())
                        ? "该工单已经由当前账号负责" : "该工单已被其他管理员认领");
                }
                if (order.getTransferRequestId() != null) {
                    throw new BusinessException("该工单存在待确认的接手邀请，暂不能认领");
                }
                if (orderDAO.claimIfUnassigned(connection, order, actor.getUserId()) != 1) {
                    throw new BusinessException("工单归属刚刚发生变化，请刷新后重试");
                }
                TicketHistory history = ticketHistoryService.event(order, actor, "TICKET_CLAIMED",
                    TicketHistoryService.PUBLIC, order.getWorkflowVersion() + 1);
                history.setToAdminId(actor.getUserId());
                ticketHistoryService.append(connection, history);
                connection.commit();
                order.setAssignedAdminId(actor.getUserId());
                order.setWorkflowVersion(order.getWorkflowVersion() + 1);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof BusinessException businessException ? businessException
                : new BusinessException("认领工单失败", ex);
        }
        syncWorkflowMirror(actor, order, "TICKET_CLAIMED");
        auditAssignment(actor, itemId, "管理员确认认领未分配工单", "CLAIM_TICKET", "INFO");
    }

    public void requestTicketTransfer(User actor, Long itemId, Long targetAdminId, String reason) {
        actor = requireFreshTicketAdmin(actor);
        User target = userDAO.findByIdForSecurity(targetAdminId);
        if (target == null || !"ADMIN".equals(target.getRole())
                || target.getStatus() == null || target.getStatus() != 1) {
            throw new BusinessException("只能邀请启用状态的 ADMIN 接手");
        }
        String requestId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = LocalDateTime.now();
        String normalizedReason;
        Order order;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                order = orderDAO.findByItemIdForUpdate(connection, itemId);
                requireAssignableOrder(order);
                normalizedReason = validateTransferRequest(actor, stringValue(order.getAssignedAdminId()),
                    order.getTransferRequestId(), targetAdminId, reason);
                if (orderDAO.requestTransfer(connection, order, actor.getUserId(), targetAdminId,
                        requestId, normalizedReason, requestedAt) != 1) {
                    throw new BusinessException("工单归属或转派申请刚刚发生变化，请刷新后重试");
                }
                TicketHistory history = ticketHistoryService.event(order, actor, "TRANSFER_REQUESTED",
                    TicketHistoryService.STAFF_ONLY, order.getWorkflowVersion() + 1);
                history.setFromAdminId(order.getAssignedAdminId());
                history.setToAdminId(targetAdminId);
                history.setTargetUserId(targetAdminId);
                history.setReason(normalizedReason);
                history.setSourceType("TRANSFER_REQUEST");
                history.setSourceId(requestId);
                ticketHistoryService.append(connection, history);
                connection.commit();
                order.setTransferRequestId(requestId);
                order.setTransferRequestedBy(actor.getUserId());
                order.setTransferTargetAdminId(targetAdminId);
                order.setTransferReason(normalizedReason);
                order.setTransferRequestedAt(requestedAt);
                order.setWorkflowVersion(order.getWorkflowVersion() + 1);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof BusinessException businessException ? businessException
                : new BusinessException("发起接手邀请失败", ex);
        }
        syncWorkflowMirror(actor, order, "TRANSFER_REQUESTED");
        auditAssignment(actor, itemId,
            "发起接手邀请，当前负责人=" + displayId(stringValue(order.getAssignedAdminId()))
                + "，目标管理员=" + targetAdminId + "，原因=" + normalizedReason,
            "REQUEST_TICKET_TRANSFER", "WARN");
    }

    public void respondToTicketTransfer(User actor, Long itemId, String expectedRequestId, boolean accept) {
        actor = requireFreshTicketAdmin(actor);
        Order order;
        String requesterDisplay;
        String transferReason;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                order = orderDAO.findByItemIdForUpdate(connection, itemId);
                if (order == null || !actor.getUserId().equals(order.getTransferTargetAdminId())) {
                    throw new BusinessException("当前账号不是本次接手邀请的目标管理员");
                }
                if (expectedRequestId == null || !expectedRequestId.equals(order.getTransferRequestId())) {
                    throw new BusinessException("接手邀请已发生变化，请刷新后重试");
                }
                if (accept) requireAssignableOrder(order);
                requesterDisplay = stringValue(order.getTransferRequestedBy());
                transferReason = order.getTransferReason();
                if (orderDAO.respondToTransfer(connection, order, actor.getUserId(), expectedRequestId, accept) != 1) {
                    throw new BusinessException("接手邀请已被其他操作处理，请刷新后重试");
                }
                TicketHistory history = ticketHistoryService.event(order, actor,
                    accept ? "TRANSFER_ACCEPTED" : "TRANSFER_REJECTED",
                    TicketHistoryService.STAFF_ONLY, order.getWorkflowVersion() + 1);
                history.setFromAdminId(order.getAssignedAdminId());
                history.setToAdminId(accept ? actor.getUserId() : order.getAssignedAdminId());
                history.setTargetUserId(actor.getUserId());
                history.setReason(order.getTransferReason());
                history.setSourceType("TRANSFER_REQUEST");
                history.setSourceId(expectedRequestId);
                ticketHistoryService.append(connection, history);
                connection.commit();
                if (accept) order.setAssignedAdminId(actor.getUserId());
                clearTransfer(order);
                order.setWorkflowVersion(order.getWorkflowVersion() + 1);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof BusinessException businessException ? businessException
                : new BusinessException("处理接手邀请失败", ex);
        }
        syncWorkflowMirror(actor, order, accept ? "TRANSFER_ACCEPTED" : "TRANSFER_REJECTED");
        auditAssignment(actor, itemId,
            (accept ? "接受" : "拒绝") + "接手邀请，发起人=" + requesterDisplay
                + "，原因=" + transferReason,
            accept ? "ACCEPT_TICKET_TRANSFER" : "REJECT_TICKET_TRANSFER", accept ? "INFO" : "WARN");
    }

    public void cancelTicketTransfer(User actor, Long itemId, String expectedRequestId) {
        actor = requireFreshTicketAdmin(actor);
        Order order;
        Long targetAdminId;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                order = orderDAO.findByItemIdForUpdate(connection, itemId);
                if (order == null || !actor.getUserId().equals(order.getTransferRequestedBy())) {
                    throw new BusinessException("只有接手邀请发起人可以取消申请");
                }
                if (expectedRequestId == null || !expectedRequestId.equals(order.getTransferRequestId())) {
                    throw new BusinessException("接手邀请已发生变化，请刷新后重试");
                }
                targetAdminId = order.getTransferTargetAdminId();
                if (orderDAO.cancelTransfer(connection, order, actor.getUserId(), expectedRequestId) != 1) {
                    throw new BusinessException("接手邀请已被其他操作处理，请刷新后重试");
                }
                TicketHistory history = ticketHistoryService.event(order, actor, "TRANSFER_CANCELLED",
                    TicketHistoryService.STAFF_ONLY, order.getWorkflowVersion() + 1);
                history.setFromAdminId(order.getAssignedAdminId());
                history.setToAdminId(targetAdminId);
                history.setTargetUserId(targetAdminId);
                history.setReason(order.getTransferReason());
                history.setSourceType("TRANSFER_REQUEST");
                history.setSourceId(expectedRequestId);
                ticketHistoryService.append(connection, history);
                connection.commit();
                clearTransfer(order);
                order.setWorkflowVersion(order.getWorkflowVersion() + 1);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof BusinessException businessException ? businessException
                : new BusinessException("取消接手邀请失败", ex);
        }
        syncWorkflowMirror(actor, order, "TRANSFER_CANCELLED");
        auditAssignment(actor, itemId, "取消接手邀请，目标管理员=" + targetAdminId,
            "CANCEL_TICKET_TRANSFER", "INFO");
    }

    static String validateTransferRequest(User actor, String assignedAdminId, String pendingTargetAdminId,
                                          Long targetAdminId, String reason) {
        if (actor == null || actor.getUserId() == null || targetAdminId == null) {
            throw new BusinessException("接手邀请参数不完整");
        }
        String actorId = String.valueOf(actor.getUserId());
        String targetId = String.valueOf(targetAdminId);
        if (actorId.equals(targetId)) {
            throw new BusinessException("未分配工单请直接认领，不能向自己发起接手邀请");
        }
        if (hasText(pendingTargetAdminId)) {
            throw new BusinessException("已有待确认的接手邀请，请先处理或取消");
        }
        if (hasText(assignedAdminId) && !actorId.equals(assignedAdminId)) {
            throw new BusinessException("工单已分配给其他管理员，当前账号不能发起转派");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("邀请其他管理员接手必须填写原因");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 200) {
            throw new BusinessException("接手原因不能超过 200 个字符");
        }
        return normalizedReason;
    }

    public static void validateStatusTransition(int oldStatus, int newStatus) {
        boolean valid = (oldStatus == 0 && (newStatus == 1 || newStatus == 4))
            || (oldStatus == 1 && (newStatus == 2 || newStatus == 4))
            || (oldStatus == 2 && newStatus == 3);
        if (!valid) {
            throw new BusinessException("非法状态流转");
        }
    }

    private void addComment(User actor, Long itemId, String content, String tag, String rating, boolean adminOnlyAction) {
        UserService.requireActiveUser(actor);
        if (adminOnlyAction) {
            actor = requireFreshTicketAdmin(actor);
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("内容不能为空");
        }
        String eventId = UUID.randomUUID().toString();
        Comment comment = new Comment();
        comment.setEventId(eventId);
        comment.setUserId(String.valueOf(actor.getUserId()));
        comment.setItemId(String.valueOf(itemId));
        comment.setContent(content.trim());
        comment.setRating(rating == null ? "" : rating);
        comment.setTags(List.of(tag));
        comment.setCreatedAt(Instant.now());
        commentDAO.insert(comment);
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                Order order = orderDAO.findByItemIdForUpdate(connection, itemId);
                validateCommentOperation(actor, order, tag, adminOnlyAction);
                if (orderDAO.incrementWorkflowVersion(connection, order) != 1) {
                    throw new BusinessException("工单刚刚发生变化，请刷新后重试");
                }
                TicketHistory history = ticketHistoryService.event(order, actor, historyEventType(tag),
                    "INTERNAL_NOTE".equals(tag) ? TicketHistoryService.STAFF_ONLY : TicketHistoryService.PUBLIC,
                    order.getWorkflowVersion() + 1);
                history.setSourceType("COMMENT");
                history.setSourceId(eventId);
                if (rating != null) {
                    history.setEventPayload(new Document("rating", rating).toJson());
                }
                ticketHistoryService.append(connection, history);
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            try {
                commentDAO.deleteByEventId(eventId);
            } catch (Exception compensationFailure) {
                auditLogService.write(String.valueOf(actor.getUserId()), "CROSS_DB_FAIL", "ERROR",
                    "评论历史写入失败且 MongoDB 补偿删除失败，工单=" + itemId,
                    "COMMENT_HISTORY_COMPENSATION");
            }
            throw ex instanceof BusinessException businessException ? businessException
                : new BusinessException("保存工单沟通历史失败", ex);
        }
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "ADD_COMMENT");
        if (adminOnlyAction || "CUSTOMER_RATING".equals(tag)) {
            auditLogService.write(String.valueOf(actor.getUserId()), "TICKET_OPERATION", "INFO",
                "新增工单评论，类型：" + tag, "ADD_COMMENT");
        }
    }

    private void validateCommentOperation(User actor, Order order, String tag, boolean adminOnlyAction) {
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        if (!UserService.isTicketStaff(actor) && !actor.getUserId().equals(order.getUserId())) {
            throw new BusinessException("无权操作该工单");
        }
        if ("CUSTOMER_RATING".equals(tag)) {
            if (!Integer.valueOf(2).equals(order.getStatus()) && !Integer.valueOf(3).equals(order.getStatus())) {
                throw new BusinessException("工单完成或关闭后才能评价");
            }
        } else if (!"INTERNAL_NOTE".equals(tag)
                && (Integer.valueOf(3).equals(order.getStatus()) || Integer.valueOf(4).equals(order.getStatus()))) {
            throw new BusinessException("已关闭或已取消的工单不能继续回复");
        }
        if (adminOnlyAction) {
            requireAssignedAdminCanProcess(actor, order);
        }
    }

    private String historyEventType(String tag) {
        return switch (tag) {
            case "CUSTOMER_REPLY" -> "CUSTOMER_REPLY_ADDED";
            case "ADMIN_REPLY" -> "ADMIN_REPLY_ADDED";
            case "INTERNAL_NOTE" -> "INTERNAL_NOTE_ADDED";
            case "CUSTOMER_RATING" -> "RATING_SUBMITTED";
            default -> "COMMENT_ADDED";
        };
    }

    private ItemDetail buildDetail(Long itemId, Long userId, String description, String priority) {
        ItemDetail detail = new ItemDetail();
        detail.setItemId(String.valueOf(itemId));
        detail.setDescription(description == null ? "" : description.trim());
        detail.setImages(List.of());
        ItemDetail.Metadata metadata = new ItemDetail.Metadata();
        metadata.setLanguage("zh-CN");
        metadata.setPriority(priority);
        metadata.setCreatedByUserId(String.valueOf(userId));
        metadata.setAssignedAdminId(null);
        metadata.setContactChannel("DESKTOP");
        metadata.setLastProcessedAt(Instant.now());
        detail.setMetadata(metadata);
        return detail;
    }

    private void requireAssignedAdminCanProcess(User actor, Order order) {
        if (order.getAssignedAdminId() == null) {
            throw new BusinessException("工单 " + order.getItemId() + " 尚未分配，请先确认认领后再处理");
        }
        if (!order.getAssignedAdminId().equals(actor.getUserId())) {
            throw new BusinessException("工单 " + order.getItemId() + " 已分配给管理员 "
                + order.getAssignedAdminId() + "，当前管理员只能查看，不能处理");
        }
    }

    private String validatePriority(String priority) {
        String normalized = priority == null ? "" : priority.trim().toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(normalized)) {
            throw new BusinessException("优先级非法");
        }
        return normalized;
    }

    static void requireReminderEligibleStatus(Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException("只有待处理或处理中的工单可以催促");
        }
    }

    private void requireAssignableOrder(Order order) {
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        if (!Integer.valueOf(0).equals(order.getStatus()) && !Integer.valueOf(1).equals(order.getStatus())) {
            throw new BusinessException("只有待处理或处理中的工单可以分配或转派");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String displayId(String value) {
        return hasText(value) ? value : "未分配";
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private void clearTransfer(Order order) {
        order.setTransferRequestId(null);
        order.setTransferRequestedBy(null);
        order.setTransferTargetAdminId(null);
        order.setTransferReason(null);
        order.setTransferRequestedAt(null);
    }

    private void syncWorkflowMirror(User actor, Order order, String operation) {
        try {
            detailDAO.syncWorkflow(order);
        } catch (Exception ex) {
            auditLogService.write(actor == null ? null : String.valueOf(actor.getUserId()),
                "CROSS_DB_FAIL", "ERROR", "MySQL 工作流已提交，MongoDB 镜像同步失败，工单="
                    + order.getItemId(), operation);
        }
    }

    private void auditAssignment(User actor, Long itemId, String message, String operation, String level) {
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), operation);
        auditLogService.write(String.valueOf(actor.getUserId()), "TICKET_ASSIGNMENT", level,
            "工单=" + itemId + "，" + message, operation);
    }

    private User requireFreshTicketAdmin(User actor) {
        UserService.requireTicketStaff(actor);
        return requireFreshRole(actor, "ADMIN", "需要 ADMIN 权限");
    }

    private User requireFreshTicketUser(User actor) {
        UserService.requireActiveUser(actor);
        return requireFreshRole(actor, "USER", "只有普通用户可以催促本人工单");
    }

    private User requireFreshRole(User actor, String requiredRole, String roleMessage) {
        User stored = userDAO.findByIdForSecurity(actor.getUserId());
        if (stored == null || stored.getStatus() == null || stored.getStatus() != 1) {
            throw new BusinessException("当前账号已不可用，请重新登录");
        }
        if (!stored.getRole().equals(actor.getRole())) {
            throw new BusinessException("当前账号角色已变更，请重新登录");
        }
        if (!requiredRole.equals(stored.getRole())) {
            throw new BusinessException(roleMessage);
        }
        return stored;
    }

}
