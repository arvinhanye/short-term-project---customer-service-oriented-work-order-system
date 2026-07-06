package com.ticket.service;

import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.model.Category;
import com.ticket.model.Order;
import com.ticket.model.User;
import java.util.ArrayList;
import java.util.List;

public class RecommendService {
    private final OrderDAO orderDAO = new OrderDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    public List<Category> recommendCategories(User actor) {
        UserService.requireActiveUser(actor);
        List<Order> orders = orderDAO.pageByUserAndStatus(actor.getUserId(), null, 1, 5).getRecords();
        List<Category> categories = new ArrayList<>();
        for (Order order : orders) {
            var item = new com.ticket.dao.mysql.ItemDAO().findById(order.getItemId());
            if (item != null) {
                Category category = categoryDAO.findById(item.getCategoryId());
                if (category != null && categories.stream().noneMatch(existing -> existing.getCategoryId().equals(category.getCategoryId()))) {
                    categories.add(category);
                }
            }
        }
        return categories;
    }
}
