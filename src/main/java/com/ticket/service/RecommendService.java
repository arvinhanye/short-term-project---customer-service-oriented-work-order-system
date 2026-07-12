package com.ticket.service;

import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.model.Category;
import com.ticket.model.User;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 分类推荐严格依据需求规格：按用户最近工单的分类倒序去重。
 */
public class RecommendService {
    private static final int HISTORY_LIMIT = 30;
    private static final int RECOMMENDATION_LIMIT = 5;

    private final OrderDAO orderDAO = new OrderDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    public List<Category> recommendCategories(User actor) {
        UserService.requireActiveUser(actor);
        LinkedHashSet<Long> categoryIds = new LinkedHashSet<>(
            orderDAO.findRecentCategoryIdsByUser(actor.getUserId(), HISTORY_LIMIT));
        List<Category> categories = new ArrayList<>();
        for (Long categoryId : categoryIds) {
            Category category = categoryDAO.findById(categoryId);
            if (category != null) {
                categories.add(category);
            }
            if (categories.size() == RECOMMENDATION_LIMIT) {
                return categories;
            }
        }
        // 冷启动时提供现有分类，不改变有历史用户的“最近优先”顺序。
        if (categories.isEmpty()) {
            for (Category category : categoryDAO.findAll()) {
                categories.add(category);
                if (categories.size() == RECOMMENDATION_LIMIT) {
                    break;
                }
            }
        }
        return categories;
    }
}
