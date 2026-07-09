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
import com.ticket.model.Item;
import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class BusinessService {
    private final ItemDAO itemDAO = new ItemDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO = new UserDAO();
    private final ProfileDAO profileDAO = new ProfileDAO();
    private final DetailDAO detailDAO = new DetailDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final ActionLogService actionLogService = new ActionLogService();
    private final AuditLogService auditLogService = new AuditLogService();

    public long createTicket(User actor, String title, Long categoryId, BigDecimal amount, String description, String priority) {
        UserService.requireActiveUser(actor);
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new BusinessException("工单标题不合法");
        }
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("金额不能为负数");
        }
        if (amount.scale() > 2) {
            throw new BusinessException("金额最多保留 2 位小数");
        }
        if (categoryId == null || categoryDAO.findById(categoryId) == null) {
            throw new BusinessException("工单分类不存在");
        }
        if (description != null && description.length() > 4000) {
            throw new BusinessException("工单描述过长");
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
        order.setCreatedAt(LocalDateTime.now());

        Long itemId = null;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                itemId = itemDAO.insert(connection, item);
                order.setItemId(itemId);
                orderDAO.insert(connection, order);
                ItemDetail detail = buildDetail(itemId, actor.getUserId(), description, normalizedPriority);
                detailDAO.upsert(detail);
                connection.commit();
                actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "CREATE_ITEM");
                auditLogService.write(String.valueOf(actor.getUserId()), "TICKET_OPERATION", "INFO",
                    "创建工单：" + item.getTitle(), "CREATE_ITEM");
                return itemId;
            } catch (Exception ex) {
                connection.rollback();
                if (itemId != null) {
                    detailDAO.deleteByItemId(String.valueOf(itemId));
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
        if (!UserService.isAdmin(actor) && !actor.getUserId().equals(order.getUserId())) {
            throw new BusinessException("无权查看该工单");
        }
        ItemDetailDTO dto = new ItemDetailDTO();
        dto.setItem(item);
        dto.setOrder(order);
        dto.setUser(userDAO.findById(order.getUserId()));
        dto.setProfile(profileDAO.findByUserId(order.getUserId()));
        dto.setItemDetail(detailDAO.findByItemId(String.valueOf(itemId)));
        dto.setComments(commentDAO.findByItemId(String.valueOf(itemId), UserService.isAdmin(actor)));
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "VIEW");
        return dto;
    }

    public void addCustomerReply(User actor, Long itemId, String content) {
        addComment(actor, itemId, content, "CUSTOMER_REPLY", null, false);
    }

    public void addAgentReply(User actor, Long itemId, String content) {
        UserService.requireAdmin(actor);
        addComment(actor, itemId, content, "AGENT_REPLY", null, true);
    }

    public void addInternalNote(User actor, Long itemId, String content) {
        UserService.requireAdmin(actor);
        addComment(actor, itemId, content, "INTERNAL_NOTE", null, true);
    }

    public void rateTicket(User actor, Long itemId, int rating, String content) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("评分必须在 1 到 5 之间");
        }
        addComment(actor, itemId, content, "CUSTOMER_RATING", String.valueOf(rating), false);
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "RATE");
    }

    public void changeOrderStatus(User actor, Long orderId, int newStatus) {
        UserService.requireAdmin(actor);
        Order order = orderDAO.findById(orderId);
        if (order == null) {
            throw new BusinessException("工单记录不存在");
        }
        validateStatusTransition(order.getStatus(), newStatus);
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                orderDAO.updateStatus(connection, orderId, newStatus);
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw new BusinessException("更新工单状态失败", ex);
        }
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(order.getItemId()), "CHANGE_STATUS");
        auditLogService.write(String.valueOf(actor.getUserId()), "STATUS_CHANGE", "INFO", "工单状态已更新", "CHANGE_STATUS");
    }

    public void assignAdmin(User actor, Long itemId, Long adminId) {
        UserService.requireAdmin(actor);
        User admin = userDAO.findById(adminId);
        if (admin == null || !"ADMIN".equals(admin.getRole())) {
            throw new BusinessException("只能分配给 ADMIN 用户");
        }
        ItemDetail detail = detailDAO.findByItemId(String.valueOf(itemId));
        if (detail == null) {
            throw new BusinessException("工单详情不存在");
        }
        detail.getMetadata().setAssignedAdminId(String.valueOf(adminId));
        detail.getMetadata().setLastProcessedAt(Instant.now());
        detailDAO.upsert(detail);
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "ASSIGN");
        auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO",
            "分配工单 " + itemId + " 给管理员 " + adminId, "ASSIGN_ADMIN");
    }

    public static void validateStatusTransition(int oldStatus, int newStatus) {
        boolean valid = (oldStatus == 0 && (newStatus == 1 || newStatus == 2 || newStatus == 3 || newStatus == 4))
            || (oldStatus == 1 && (newStatus == 2 || newStatus == 3 || newStatus == 4))
            || (oldStatus == 2 && newStatus == 3);
        if (!valid) {
            throw new BusinessException("非法状态流转");
        }
    }

    private void addComment(User actor, Long itemId, String content, String tag, String rating, boolean adminOnlyAction) {
        UserService.requireActiveUser(actor);
        Order order = orderDAO.findByItemId(itemId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        if (!UserService.isAdmin(actor) && !actor.getUserId().equals(order.getUserId())) {
            throw new BusinessException("无权操作该工单");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("内容不能为空");
        }
        if (adminOnlyAction && !UserService.isAdmin(actor)) {
            throw new BusinessException("需要 ADMIN 权限");
        }
        Comment comment = new Comment();
        comment.setUserId(String.valueOf(actor.getUserId()));
        comment.setItemId(String.valueOf(itemId));
        comment.setContent(content.trim());
        comment.setRating(rating == null ? "" : rating);
        comment.setTags(List.of(tag));
        comment.setCreatedAt(Instant.now());
        commentDAO.insert(comment);
        actionLogService.write(String.valueOf(actor.getUserId()), String.valueOf(itemId), "ADD_COMMENT");
        if (adminOnlyAction || "CUSTOMER_RATING".equals(tag)) {
            auditLogService.write(String.valueOf(actor.getUserId()), "TICKET_OPERATION", "INFO",
                "新增工单评论，类型：" + tag, "ADD_COMMENT");
        }
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

    private String validatePriority(String priority) {
        String normalized = priority == null ? "" : priority.trim().toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(normalized)) {
            throw new BusinessException("优先级非法");
        }
        return normalized;
    }

}
