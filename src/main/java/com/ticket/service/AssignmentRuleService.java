package com.ticket.service;

import com.ticket.dao.mysql.AssignmentRuleDAO;
import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dao.mysql.UserDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.AssignmentRule;
import com.ticket.model.User;
import java.util.List;
import java.util.Set;

public class AssignmentRuleService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private final AssignmentRuleDAO ruleDAO = new AssignmentRuleDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final UserDAO userDAO = new UserDAO();

    public List<AssignmentRule> list(User actor) {
        UserService.requireBusinessAdmin(actor);
        return ruleDAO.findAll();
    }

    public void create(User actor, String name, Long categoryId, String priority, String strategy,
                       Long targetAdminId, int sortOrder) {
        UserService.requireBusinessAdmin(actor);
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 100) {
            throw new BusinessException("规则名称不能为空且不能超过 100 个字符");
        }
        if (categoryId != null && categoryDAO.findById(categoryId) == null) {
            throw new BusinessException("规则分类不存在");
        }
        String normalizedPriority = priority == null || priority.isBlank() ? null : priority.trim().toUpperCase();
        if (normalizedPriority != null && !PRIORITIES.contains(normalizedPriority)) {
            throw new BusinessException("规则优先级无效");
        }
        if (!"SPECIFIC_ADMIN".equals(strategy) && !"LEAST_LOADED".equals(strategy)) {
            throw new BusinessException("自动分配策略无效");
        }
        Long normalizedAdminId = null;
        if ("SPECIFIC_ADMIN".equals(strategy)) {
            User admin = targetAdminId == null ? null : userDAO.findByIdForSecurity(targetAdminId);
            if (admin == null || !"ADMIN".equals(admin.getRole()) || !Integer.valueOf(1).equals(admin.getStatus())) {
                throw new BusinessException("指定管理员必须是启用状态的 ADMIN");
            }
            normalizedAdminId = targetAdminId;
        }
        AssignmentRule rule = new AssignmentRule();
        rule.setRuleName(normalizedName);
        rule.setCategoryId(categoryId);
        rule.setPriority(normalizedPriority);
        rule.setStrategy(strategy);
        rule.setTargetAdminId(normalizedAdminId);
        rule.setEnabled(true);
        rule.setSortOrder(Math.max(0, Math.min(sortOrder, 10000)));
        ruleDAO.insert(rule);
    }

    public void setEnabled(User actor, Long ruleId, boolean enabled) {
        UserService.requireBusinessAdmin(actor);
        if (ruleId == null) throw new BusinessException("请选择分配规则");
        ruleDAO.setEnabled(ruleId, enabled);
    }
}
