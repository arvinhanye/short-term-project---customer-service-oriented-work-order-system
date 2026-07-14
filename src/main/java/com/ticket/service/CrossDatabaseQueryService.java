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
import com.ticket.dto.CursorPageResult;
import com.ticket.dto.PageResult;
import com.ticket.dto.UserActivityDTO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Comment;
import com.ticket.model.Item;
import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import com.ticket.model.User;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

public class CrossDatabaseQueryService {
    public enum AssignmentScope {
        ALL,
        UNASSIGNED,
        ASSIGNED_TO
    }

    public record AssignedWorkOverview(Map<Integer, Long> statusCounts, List<CrossTicketDTO> riskTickets) {
    }

    public record UserWorkOverview(Map<Integer, Long> statusCounts, List<CrossTicketDTO> recentTickets) {
    }

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
        return pageTicketSummaries(actor, UserService.isAdmin(actor) ? null : actor.getUserId(), status, null, page, pageSize);
    }

    public PageResult<CrossTicketDTO> pageTickets(User actor, Integer status, String keyword, int page, int pageSize) {
        UserService.requireActiveUser(actor);
        return pageTicketSummaries(actor, UserService.isAdmin(actor) ? null : actor.getUserId(), status, keyword, page, pageSize);
    }

    public PageResult<CrossTicketDTO> pageAdminTickets(User actor, Integer status, String keyword,
                                                        AssignmentScope assignmentScope, String assignedAdminId,
                                                        int page, int pageSize) {
        UserService.requireAdmin(actor);
        AssignmentScope normalizedScope = assignmentScope == null ? AssignmentScope.ALL : assignmentScope;
        if (normalizedScope == AssignmentScope.ALL) {
            return pageTicketSummaries(actor, null, status, keyword, page, pageSize);
        }

        List<CrossTicketDTO> records = orderDAO.listTicketSummaries(null, status, keyword);
        enrichSummaryDetails(records);
        return filterAndPageByAssignment(records, normalizedScope, assignedAdminId, page, pageSize);
    }

    public AssignedWorkOverview assignedWorkOverview(User actor, String assignedAdminId, int riskLimit) {
        UserService.requireAdmin(actor);
        List<CrossTicketDTO> records = orderDAO.listTicketSummaries(null, null, null);
        enrichSummaryDetails(records);
        return buildAssignedWorkOverview(records, assignedAdminId, riskLimit);
    }

    public UserWorkOverview userWorkOverview(User actor, int recentLimit) {
        UserService.requireActiveUser(actor);
        List<CrossTicketDTO> records = orderDAO.listTicketSummaries(actor.getUserId(), null, null);
        enrichSummaryDetails(records);
        return buildUserWorkOverview(records, recentLimit);
    }

    public PageResult<CrossTicketDTO> pageMyTickets(User actor, Integer status, String keyword, int page, int pageSize) {
        UserService.requireActiveUser(actor);
        return pageTicketSummaries(actor, actor.getUserId(), status, keyword, page, pageSize);
    }

    public CursorPageResult<CrossTicketDTO> pageMyTicketsAfter(User actor, Integer status, String keyword,
                                                                 java.time.LocalDateTime cursorCreatedAt, Long cursorOrderId,
                                                                 int pageSize) {
        UserService.requireActiveUser(actor);
        CursorPageResult<CrossTicketDTO> result = orderDAO.pageTicketSummariesAfter(
            actor.getUserId(), status, keyword, cursorCreatedAt, cursorOrderId, pageSize);
        enrichSummaryDetails(result.getRecords());
        return result;
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
        return pageTicketSummaries(actor, UserService.isAdmin(actor) ? null : actor.getUserId(), null, keyword, page, pageSize);
    }

    private PageResult<CrossTicketDTO> pageTicketSummaries(User actor, Long userId, Integer status, String keyword, int page, int pageSize) {
        PageResult<CrossTicketDTO> result = orderDAO.pageTicketSummaries(userId, status, keyword, page, pageSize);
        enrichSummaryDetails(result.getRecords());
        return result;
    }

    private void enrichSummaryDetails(List<CrossTicketDTO> records) {
        Set<String> itemIds = records.stream().map(dto -> String.valueOf(dto.getItem().getItemId()))
            .collect(Collectors.toSet());
        Map<String, com.ticket.model.ItemDetail> details = detailDAO.findByItemIds(itemIds);
        for (CrossTicketDTO ticket : records) {
            ticket.setItemDetail(details.get(String.valueOf(ticket.getItem().getItemId())));
        }
    }

    static PageResult<CrossTicketDTO> filterAndPageByAssignment(List<CrossTicketDTO> records,
                                                                 AssignmentScope assignmentScope,
                                                                 String assignedAdminId,
                                                                 int page, int pageSize) {
        AssignmentScope normalizedScope = assignmentScope == null ? AssignmentScope.ALL : assignmentScope;
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.max(1, Math.min(pageSize, 100));
        List<CrossTicketDTO> filtered = records == null ? List.of() : records.stream()
            .filter(ticket -> matchesAssignment(ticket, normalizedScope, assignedAdminId))
            .toList();
        int fromIndex = Math.min(filtered.size(), (normalizedPage - 1) * normalizedPageSize);
        int toIndex = Math.min(filtered.size(), fromIndex + normalizedPageSize);
        return new PageResult<>(new ArrayList<>(filtered.subList(fromIndex, toIndex)), filtered.size(),
            normalizedPage, normalizedPageSize);
    }

    static Map<Integer, Long> countStatusesByAssignment(List<CrossTicketDTO> records, String assignedAdminId) {
        Map<Integer, Long> counts = new java.util.LinkedHashMap<>();
        if (records == null || assignedAdminId == null) {
            return counts;
        }
        for (CrossTicketDTO ticket : records) {
            if (!matchesAssignment(ticket, AssignmentScope.ASSIGNED_TO, assignedAdminId)
                    || ticket.getOrder() == null || ticket.getOrder().getStatus() == null) {
                continue;
            }
            counts.merge(ticket.getOrder().getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    static AssignedWorkOverview buildAssignedWorkOverview(List<CrossTicketDTO> records,
                                                           String assignedAdminId, int riskLimit) {
        Map<Integer, Long> counts = countStatusesByAssignment(records, assignedAdminId);
        if (records == null || assignedAdminId == null) {
            return new AssignedWorkOverview(counts, List.of());
        }
        int normalizedLimit = Math.max(1, Math.min(riskLimit, 20));
        List<CrossTicketDTO> risks = records.stream()
            .filter(ticket -> matchesAssignment(ticket, AssignmentScope.ASSIGNED_TO, assignedAdminId))
            .filter(ticket -> ticket.getOrder() != null
                && (Integer.valueOf(0).equals(ticket.getOrder().getStatus())
                    || Integer.valueOf(1).equals(ticket.getOrder().getStatus())))
            .sorted(Comparator.comparingInt(CrossDatabaseQueryService::priorityRank)
                .thenComparing(CrossDatabaseQueryService::createdAt,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(normalizedLimit)
            .toList();
        return new AssignedWorkOverview(Map.copyOf(counts), List.copyOf(risks));
    }

    static UserWorkOverview buildUserWorkOverview(List<CrossTicketDTO> records, int recentLimit) {
        Map<Integer, Long> counts = new java.util.LinkedHashMap<>();
        if (records == null) {
            return new UserWorkOverview(Map.of(), List.of());
        }
        for (CrossTicketDTO ticket : records) {
            if (ticket != null && ticket.getOrder() != null && ticket.getOrder().getStatus() != null) {
                counts.merge(ticket.getOrder().getStatus(), 1L, Long::sum);
            }
        }
        int normalizedLimit = Math.max(1, Math.min(recentLimit, 20));
        List<CrossTicketDTO> recentTickets = records.stream()
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(CrossDatabaseQueryService::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(normalizedLimit)
            .toList();
        return new UserWorkOverview(Map.copyOf(counts), List.copyOf(recentTickets));
    }

    private static int priorityRank(CrossTicketDTO ticket) {
        ItemDetail detail = ticket == null ? null : ticket.getItemDetail();
        ItemDetail.Metadata metadata = detail == null ? null : detail.getMetadata();
        String priority = metadata == null ? null : metadata.getPriority();
        if (priority == null) {
            return 4;
        }
        return switch (priority.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "URGENT" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            case "LOW" -> 3;
            default -> 4;
        };
    }

    private static java.time.LocalDateTime createdAt(CrossTicketDTO ticket) {
        return ticket == null || ticket.getOrder() == null ? null : ticket.getOrder().getCreatedAt();
    }

    private static boolean matchesAssignment(CrossTicketDTO ticket, AssignmentScope assignmentScope,
                                             String assignedAdminId) {
        ItemDetail detail = ticket == null ? null : ticket.getItemDetail();
        ItemDetail.Metadata metadata = detail == null ? null : detail.getMetadata();
        String actualAdminId = metadata == null ? null : metadata.getAssignedAdminId();
        return switch (assignmentScope) {
            case ALL -> true;
            case UNASSIGNED -> actualAdminId == null || actualAdminId.isBlank();
            case ASSIGNED_TO -> assignedAdminId != null && assignedAdminId.equals(actualAdminId);
        };
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
