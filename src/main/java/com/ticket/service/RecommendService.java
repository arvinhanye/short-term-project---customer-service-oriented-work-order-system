package com.ticket.service;

import com.ticket.dao.mongo.CommentDAO;
import com.ticket.dao.mongo.LogDAO;
import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dao.mysql.ItemDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.dto.RecommendationDTO;
import com.ticket.model.Category;
import com.ticket.model.Comment;
import com.ticket.model.Item;
import com.ticket.model.Order;
import com.ticket.model.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;

public class RecommendService {
    private final OrderDAO orderDAO = new OrderDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final LogDAO logDAO = new LogDAO();
    private final CommentDAO commentDAO = new CommentDAO();

    public List<Category> recommendCategories(User actor) {
        UserService.requireActiveUser(actor);
        List<Category> categories = new ArrayList<>();
        for (RecommendationDTO recommendation : recommendTickets(actor, 10)) {
            if (recommendation.getCategoryId() == null) {
                continue;
            }
            boolean exists = categories.stream()
                .anyMatch(category -> category.getCategoryId().equals(recommendation.getCategoryId()));
            if (!exists) {
                Category category = categoryDAO.findById(recommendation.getCategoryId());
                if (category != null) {
                    categories.add(category);
                }
            }
            if (categories.size() >= 5) {
                return categories;
            }
        }
        if (categories.isEmpty()) {
            for (Category category : categoryDAO.findAll()) {
                categories.add(category);
                if (categories.size() >= 5) {
                    break;
                }
            }
        }
        return categories;
    }

    public List<RecommendationDTO> recommendTickets(User actor, int limit) {
        UserService.requireActiveUser(actor);
        int normalizedLimit = normalizeLimit(limit);
        Map<Long, Double> categoryScores = new HashMap<>();
        Map<Long, Long> categoryHistory = new HashMap<>();
        Set<Long> userItemIds = new HashSet<>();

        collectOrderSignals(actor, categoryScores, categoryHistory, userItemIds);
        collectActionSignals(actor, categoryScores, categoryHistory);
        collectRatingSignals(actor, categoryScores, categoryHistory);

        Map<Long, Long> hotItemActions = hotItemActionMap();
        Map<Long, RatingSignal> ratingSignals = ratingSignalMap();
        List<RecommendationDTO> recommendations = new ArrayList<>();
        Set<Long> candidateItemIds = new HashSet<>();

        List<Long> rankedCategories = categoryScores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .toList();
        for (Long categoryId : rankedCategories) {
            for (Item item : itemDAO.findRecentByCategory(categoryId, 5)) {
                addCandidate(item, categoryScores, categoryHistory, hotItemActions, ratingSignals,
                    userItemIds, candidateItemIds, recommendations);
            }
        }

        if (recommendations.size() < normalizedLimit) {
            for (Item item : itemDAO.findRecent(30)) {
                addCandidate(item, categoryScores, categoryHistory, hotItemActions, ratingSignals,
                    userItemIds, candidateItemIds, recommendations);
                if (recommendations.size() >= normalizedLimit * 2) {
                    break;
                }
            }
        }

        return recommendations.stream()
            .sorted(Comparator.comparing(RecommendationDTO::getScore).reversed())
            .limit(normalizedLimit)
            .toList();
    }

    private void collectOrderSignals(User actor, Map<Long, Double> categoryScores,
                                     Map<Long, Long> categoryHistory, Set<Long> userItemIds) {
        List<Order> orders = orderDAO.findRecentByUser(actor.getUserId(), 30);
        for (int index = 0; index < orders.size(); index++) {
            Order order = orders.get(index);
            Item item = itemDAO.findById(order.getItemId());
            if (item == null) {
                continue;
            }
            userItemIds.add(item.getItemId());
            double recencyScore = Math.max(1.0, 30.0 - index) / 10.0;
            double statusScore = order.getStatus() != null && order.getStatus() == 2 ? 1.5 : 1.0;
            categoryScores.merge(item.getCategoryId(), recencyScore + statusScore, Double::sum);
            categoryHistory.merge(item.getCategoryId(), 1L, Long::sum);
        }
    }

    private void collectActionSignals(User actor, Map<Long, Double> categoryScores, Map<Long, Long> categoryHistory) {
        for (Document action : logDAO.findRecentByUser(String.valueOf(actor.getUserId()), 80)) {
            Long itemId = parseLong(action.getString("item_id"));
            if (itemId == null) {
                continue;
            }
            Item item = itemDAO.findById(itemId);
            if (item == null) {
                continue;
            }
            categoryScores.merge(item.getCategoryId(), actionWeight(action.getString("action_type")), Double::sum);
            categoryHistory.merge(item.getCategoryId(), 1L, Long::sum);
        }
    }

    private void collectRatingSignals(User actor, Map<Long, Double> categoryScores, Map<Long, Long> categoryHistory) {
        for (Comment comment : commentDAO.findByUserId(String.valueOf(actor.getUserId()), 50, false)) {
            Long itemId = parseLong(comment.getItemId());
            Integer rating = parseInteger(comment.getRating());
            if (itemId == null || rating == null) {
                continue;
            }
            Item item = itemDAO.findById(itemId);
            if (item == null) {
                continue;
            }
            categoryScores.merge(item.getCategoryId(), Math.max(0, rating - 2) * 1.2, Double::sum);
            categoryHistory.merge(item.getCategoryId(), 1L, Long::sum);
        }
    }

    private void addCandidate(Item item, Map<Long, Double> categoryScores, Map<Long, Long> categoryHistory,
                              Map<Long, Long> hotItemActions, Map<Long, RatingSignal> ratingSignals,
                              Set<Long> userItemIds, Set<Long> candidateItemIds,
                              List<RecommendationDTO> recommendations) {
        if (item == null || userItemIds.contains(item.getItemId()) || !candidateItemIds.add(item.getItemId())) {
            return;
        }
        Category category = categoryDAO.findById(item.getCategoryId());
        RatingSignal ratingSignal = ratingSignals.get(item.getItemId());
        long globalActions = hotItemActions.getOrDefault(item.getItemId(), 0L);
        double score = categoryScores.getOrDefault(item.getCategoryId(), 0.8)
            + Math.log1p(globalActions)
            + (ratingSignal == null ? 0.0 : ratingSignal.averageRating);

        RecommendationDTO dto = new RecommendationDTO();
        dto.setItemId(item.getItemId());
        dto.setTitle(item.getTitle());
        dto.setCategoryId(item.getCategoryId());
        dto.setCategoryName(category == null ? "" : category.getName());
        dto.setScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
        dto.setUserHistoryCount(categoryHistory.getOrDefault(item.getCategoryId(), 0L));
        dto.setGlobalActionCount(globalActions);
        if (ratingSignal != null) {
            dto.setAverageRating(ratingSignal.averageRating);
            dto.setRatingCount(ratingSignal.ratingCount);
        }
        dto.setReason(buildReason(dto));
        recommendations.add(dto);
    }

    private Map<Long, Long> hotItemActionMap() {
        Map<Long, Long> result = new HashMap<>();
        for (Document document : logDAO.aggregateHotItems()) {
            Long itemId = parseLong(document.getString("_id"));
            if (itemId != null) {
                result.put(itemId, numberValue(document.get("action_count")).longValue());
            }
        }
        return result;
    }

    private Map<Long, RatingSignal> ratingSignalMap() {
        Map<Long, RatingSignal> result = new HashMap<>();
        for (Document document : commentDAO.aggregateAverageRatingByItem()) {
            Long itemId = parseLong(document.getString("_id"));
            if (itemId != null) {
                RatingSignal signal = new RatingSignal();
                signal.averageRating = numberValue(document.get("avg_rating")).doubleValue();
                signal.ratingCount = numberValue(document.get("rating_count")).longValue();
                result.put(itemId, signal);
            }
        }
        return result;
    }

    private String buildReason(RecommendationDTO dto) {
        if (dto.getUserHistoryCount() > 0) {
            return "匹配你近期常用分类，结合行为热度与评分排序";
        }
        if (dto.getGlobalActionCount() > 0) {
            return "近期全站行为热度较高";
        }
        return "最新工单候选，适合作为冷启动推荐";
    }

    private double actionWeight(String actionType) {
        if ("CREATE_ITEM".equals(actionType)) {
            return 3.0;
        }
        if ("ADD_COMMENT".equals(actionType) || "RATE".equals(actionType)) {
            return 2.4;
        }
        if ("VIEW".equals(actionType)) {
            return 1.2;
        }
        if ("SEARCH".equals(actionType)) {
            return 0.8;
        }
        return 1.0;
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Number numberValue(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 20));
    }

    private static class RatingSignal {
        private double averageRating;
        private long ratingCount;
    }
}
