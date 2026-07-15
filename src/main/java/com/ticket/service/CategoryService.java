package com.ticket.service;

import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dto.CategoryOverviewDTO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Category;
import com.ticket.model.User;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
        UserService.requireBusinessAdmin(actor);
        return categoryDAO.findAll();
    }

    public List<CategoryOverviewDTO> listCategoryOverview(User actor) {
        UserService.requireBusinessAdmin(actor);
        List<CategoryOverviewDTO> categories = categoryDAO.findManagementOverview();
        Map<String, List<CategoryOverviewDTO>> bySiblingName = categories.stream().collect(Collectors.groupingBy(
            category -> (category.getParentId() == null ? "ROOT" : category.getParentId()) + "\u0000"
                + category.getName().trim().toLowerCase(Locale.ROOT)));
        bySiblingName.values().stream().filter(group -> group.size() > 1)
            .forEach(group -> group.forEach(category -> category.setDuplicateName(true)));
        return categories;
    }

    /** Returns categories that an authenticated user may select while creating a ticket. */
    public List<Category> listAvailableCategories(User actor) {
        UserService.requireActiveUser(actor);
        return categoryDAO.findAll();
    }

    public long createCategory(User actor, String name, Long parentId) {
        UserService.requireBusinessAdmin(actor);
        Category category = newCategory(name, parentId);
        validateUniqueName(null, category.getName(), parentId);
        long categoryId = categoryDAO.insert(category);
        audit(actor, "新增分类 " + categoryId, "CREATE_CATEGORY");
        return categoryId;
    }

    public void updateCategory(User actor, Long categoryId, String name, Long parentId) {
        UserService.requireBusinessAdmin(actor);
        if (categoryId == null || categoryDAO.findById(categoryId) == null) {
            throw new BusinessException("分类不存在");
        }
        if (categoryId.equals(parentId)) {
            throw new BusinessException("分类不能设置为自己的父分类");
        }
        if (parentId != null && categoryDAO.countChildren(categoryId) > 0) {
            throw new BusinessException("该一级分类仍有二级分类，不能改为二级分类");
        }
        Category category = newCategory(name, parentId);
        category.setCategoryId(categoryId);
        validateUniqueName(categoryId, category.getName(), parentId);
        categoryDAO.update(category);
        audit(actor, "更新分类 " + categoryId, "UPDATE_CATEGORY");
    }

    public void deleteCategory(User actor, Long categoryId) {
        UserService.requireBusinessAdmin(actor);
        if (categoryId == null || categoryDAO.findById(categoryId) == null) {
            throw new BusinessException("分类不存在");
        }
        if (categoryDAO.countChildren(categoryId) > 0 || categoryDAO.countItems(categoryId) > 0) {
            throw new BusinessException("该分类仍有二级分类或关联工单，不能删除");
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
        if (parentId == null) {
            return;
        }
        Category parent = categoryDAO.findById(parentId);
        if (parent == null) {
            throw new BusinessException("父分类不存在");
        }
        if (parent.getParentId() != null) {
            throw new BusinessException("只能选择一级分类作为父分类，系统最多支持两级分类");
        }
    }

    private void validateUniqueName(Long excludedCategoryId, String name, Long parentId) {
        if (categoryDAO.existsSiblingName(excludedCategoryId, parentId, name)) {
            throw new BusinessException("同一层级下已存在同名分类");
        }
    }

    private void audit(User actor, String message, String operation) {
        auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO", message, operation);
    }
}
