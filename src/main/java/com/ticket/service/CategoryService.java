package com.ticket.service;

import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Category;
import com.ticket.model.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Centralizes authorization and integrity rules for administrator category management. */
public class CategoryService {
    private static final int MAX_NAME_LENGTH = 50;
    private final CategoryDAO categoryDAO;
    private final AuditLogService auditLogService;

    public CategoryService() {
        this(new CategoryDAO(), new AuditLogService());
    }

    CategoryService(CategoryDAO categoryDAO, AuditLogService auditLogService) {
        this.categoryDAO = categoryDAO;
        this.auditLogService = auditLogService;
    }

    public List<Category> listCategories(User actor) {
        UserService.requireAdmin(actor);
        return categoryDAO.findAll();
    }

    /** Returns categories that an authenticated user may select while creating a ticket. */
    public List<Category> listAvailableCategories(User actor) {
        UserService.requireActiveUser(actor);
        return categoryDAO.findAll();
    }

    public long createCategory(User actor, String name, Long parentId) {
        UserService.requireAdmin(actor);
        Category category = newCategory(name, parentId);
        long categoryId = categoryDAO.insert(category);
        audit(actor, "新增分类 " + categoryId, "CREATE_CATEGORY");
        return categoryId;
    }

    public void updateCategory(User actor, Long categoryId, String name, Long parentId) {
        UserService.requireAdmin(actor);
        if (categoryId == null || categoryDAO.findById(categoryId) == null) {
            throw new BusinessException("分类不存在");
        }
        if (categoryId.equals(parentId)) {
            throw new BusinessException("分类不能设置为自己的父分类");
        }
        validateParent(parentId);
        ensureNoCycle(categoryId, parentId);
        Category category = newCategory(name, parentId);
        category.setCategoryId(categoryId);
        categoryDAO.update(category);
        audit(actor, "更新分类 " + categoryId, "UPDATE_CATEGORY");
    }

    public void deleteCategory(User actor, Long categoryId) {
        UserService.requireAdmin(actor);
        if (categoryId == null || categoryDAO.findById(categoryId) == null) {
            throw new BusinessException("分类不存在");
        }
        if (categoryDAO.countChildren(categoryId) > 0 || categoryDAO.countItems(categoryId) > 0) {
            throw new BusinessException("该分类仍有子分类或工单，不能删除");
        }
        categoryDAO.delete(categoryId);
        audit(actor, "删除分类 " + categoryId, "DELETE_CATEGORY");
    }

    private Category newCategory(String name, Long parentId) {
        if (name == null || name.isBlank() || name.trim().length() > MAX_NAME_LENGTH) {
            throw new BusinessException("分类名称长度不合法");
        }
        validateParent(parentId);
        Category category = new Category();
        category.setName(name.trim());
        category.setParentId(parentId);
        return category;
    }

    private void validateParent(Long parentId) {
        if (parentId != null && categoryDAO.findById(parentId) == null) {
            throw new BusinessException("父分类不存在");
        }
    }

    private void ensureNoCycle(Long categoryId, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long candidate = parentId;
        while (candidate != null) {
            if (!visited.add(candidate) || categoryId.equals(candidate)) {
                throw new BusinessException("分类层级不能形成循环");
            }
            Category parent = categoryDAO.findById(candidate);
            candidate = parent == null ? null : parent.getParentId();
        }
    }

    private void audit(User actor, String message, String operation) {
        auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO", message, operation);
    }
}
