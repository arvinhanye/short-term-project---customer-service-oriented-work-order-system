package com.ticket.service;

import com.ticket.dao.mongo.CommentDAO;
import com.ticket.dao.mongo.DetailDAO;
import com.ticket.dao.mongo.LogDAO;
import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dao.mysql.ItemDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.dao.mysql.ProfileDAO;
import com.ticket.dao.mysql.UserDAO;
import com.ticket.dto.CrossTicketDTO;
import com.ticket.dto.PageResult;
import com.ticket.dto.UserActivityDTO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Comment;
import com.ticket.model.Item;
import com.ticket.model.Order;
import com.ticket.model.User;
import java.util.ArrayList;
import java.util.List;

public class CrossDatabaseQueryService {
    private final ItemDAO itemDAO = new ItemDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO = new UserDAO();
    private final ProfileDAO profileDAO = new ProfileDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final DetailDAO detailDAO = new DetailDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final LogDAO logDAO = new LogDAO();

    public CrossTicketDTO getTicket(User actor, Long itemId) {
        UserService.requireActiveUser(actor);
        Item item = itemDAO.findById(itemId);
        if (item == null) {
            throw new BusinessException("工单不存在");
        }
        Order order = orderDAO.findByItemId(itemId);
        requireTicketVisible(actor, order);
        return buildTicket(item, order, UserService.isAdmin(actor));
    }

    public PageResult<CrossTicketDTO> pageTickets(User actor, Integer status, int page, int pageSize) {
        UserService.requireActiveUser(actor);
        PageResult<Order> orderPage = UserService.isAdmin(actor)
            ? orderDAO.pageAllByStatus(status, page, pageSize)
            : orderDAO.pageByUserAndStatus(actor.getUserId(), status, page, pageSize);
        List<CrossTicketDTO> records = new ArrayList<>();
        for (Order order : orderPage.getRecords()) {
            Item item = itemDAO.findById(order.getItemId());
            if (item != null) {
                records.add(buildTicket(item, order, UserService.isAdmin(actor)));
            }
        }
        return new PageResult<>(records, orderPage.getTotal(), orderPage.getPage(), orderPage.getPageSize());
    }

    public PageResult<CrossTicketDTO> pageMyTickets(User actor, Integer status, String keyword, int page, int pageSize) {
        UserService.requireActiveUser(actor);
        PageResult<Order> orderPage = orderDAO.pageByUserStatusAndKeyword(
            actor.getUserId(), status, keyword, page, pageSize);
        List<CrossTicketDTO> records = new ArrayList<>();
        for (Order order : orderPage.getRecords()) {
            Item item = itemDAO.findById(order.getItemId());
            if (item != null) {
                records.add(buildTicket(item, order, false));
            }
        }
        return new PageResult<>(records, orderPage.getTotal(), orderPage.getPage(), orderPage.getPageSize());
    }

    public UserActivityDTO getUserActivity(User actor, Long userId, int limit) {
        UserService.requireActiveUser(actor);
        if (!UserService.isAdmin(actor) && !actor.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该用户活动");
        }
        User targetUser = userDAO.findById(userId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        int normalizedLimit = normalizeLimit(limit);
        UserActivityDTO dto = new UserActivityDTO();
        dto.setUser(targetUser);
        dto.setProfile(profileDAO.findByUserId(userId));
        dto.setRecentComments(commentDAO.findByUserId(String.valueOf(userId), normalizedLimit, UserService.isAdmin(actor)));
        dto.setRecentActions(logDAO.findRecentByUser(String.valueOf(userId), normalizedLimit));

        List<CrossTicketDTO> tickets = new ArrayList<>();
        for (Order order : orderDAO.findRecentByUser(userId, normalizedLimit)) {
            Item item = itemDAO.findById(order.getItemId());
            if (item != null) {
                tickets.add(buildTicket(item, order, UserService.isAdmin(actor)));
            }
        }
        dto.setRecentTickets(tickets);
        return dto;
    }

    public PageResult<CrossTicketDTO> searchTickets(User actor, String keyword, int page, int pageSize) {
        UserService.requireActiveUser(actor);
        PageResult<Item> itemPage = itemDAO.pageByTitle(keyword, page, pageSize);
        List<CrossTicketDTO> records = new ArrayList<>();
        for (Item item : itemPage.getRecords()) {
            Order order = orderDAO.findByItemId(item.getItemId());
            if (order == null) {
                if (UserService.isAdmin(actor)) {
                    records.add(buildTicket(item, null, true));
                }
                continue;
            }
            if (UserService.isAdmin(actor) || actor.getUserId().equals(order.getUserId())) {
                records.add(buildTicket(item, order, UserService.isAdmin(actor)));
            }
        }
        return new PageResult<>(records, itemPage.getTotal(), itemPage.getPage(), itemPage.getPageSize());
    }

    private CrossTicketDTO buildTicket(Item item, Order order, boolean includeInternal) {
        CrossTicketDTO dto = new CrossTicketDTO();
        dto.setItem(item);
        dto.setOrder(order);
        dto.setCategory(categoryDAO.findById(item.getCategoryId()));
        dto.setItemDetail(detailDAO.findByItemId(String.valueOf(item.getItemId())));
        dto.setActionLogs(logDAO.findByItemId(String.valueOf(item.getItemId()), 50));
        dto.setActionCount(dto.getActionLogs().size());

        List<Comment> comments = commentDAO.findByItemId(String.valueOf(item.getItemId()), includeInternal);
        dto.setComments(comments);
        dto.setCommentCount(comments.size());
        dto.setInternalNoteCount(comments.stream()
            .filter(comment -> comment.getTags() != null && comment.getTags().contains("INTERNAL_NOTE"))
            .count());
        dto.setAverageRating(calculateAverageRating(comments));

        if (order == null) {
            dto.getConsistencyWarnings().add("MySQL items 存在，但 orders 缺少 item_id=" + item.getItemId() + " 的记录");
            return dto;
        }
        dto.setUser(userDAO.findById(order.getUserId()));
        dto.setProfile(profileDAO.findByUserId(order.getUserId()));
        if (item.getStatus() != null && order.getStatus() != null && !item.getStatus().equals(order.getStatus())) {
            dto.getConsistencyWarnings().add("items.status 与 orders.status 不一致");
        }
        if (dto.getItemDetail() == null) {
            dto.getConsistencyWarnings().add("MongoDB item_details 缺少 item_id=" + item.getItemId() + " 的详情文档");
        }
        return dto;
    }

    private void requireTicketVisible(User actor, Order order) {
        if (order == null) {
            if (!UserService.isAdmin(actor)) {
                throw new BusinessException("无权查看该工单");
            }
            return;
        }
        if (!UserService.isAdmin(actor) && !actor.getUserId().equals(order.getUserId())) {
            throw new BusinessException("无权查看该工单");
        }
    }

    private Double calculateAverageRating(List<Comment> comments) {
        int count = 0;
        int total = 0;
        for (Comment comment : comments) {
            if (comment.getTags() == null || !comment.getTags().contains("CUSTOMER_RATING")) {
                continue;
            }
            try {
                total += Integer.parseInt(comment.getRating());
                count += 1;
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy rating values while preserving the rest of the joined result.
            }
        }
        return count == 0 ? null : (double) total / count;
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }
}
