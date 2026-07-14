package com.ticket.service;

import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Category;
import com.ticket.model.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {
    @Test
    void onlyAdminsCanManageCategories() {
        FakeCategoryDAO dao = new FakeCategoryDAO();
        CategoryService service = new CategoryService(dao, new NoopAuditLogService());

        Assertions.assertThrows(BusinessException.class,
            () -> service.createCategory(user("USER"), "账户", null));
        Assertions.assertEquals(0, dao.categories.size());
    }

    @Test
    void activeUsersCanReadCategoriesButDisabledUsersCannot() {
        FakeCategoryDAO dao = new FakeCategoryDAO();
        dao.add(category(1L, "账户", null));
        CategoryService service = new CategoryService(dao, new NoopAuditLogService());

        Assertions.assertEquals(1, service.listAvailableCategories(user("USER")).size());
        User disabledUser = user("USER");
        disabledUser.setStatus(0);
        Assertions.assertThrows(BusinessException.class, () -> service.listAvailableCategories(disabledUser));
    }

    @Test
    void rejectsMissingParentsAndThirdLevelCategories() {
        FakeCategoryDAO dao = new FakeCategoryDAO();
        dao.add(category(1L, "一级", null));
        dao.add(category(2L, "二级", 1L));
        CategoryService service = new CategoryService(dao, new NoopAuditLogService());

        Assertions.assertThrows(BusinessException.class,
            () -> service.createCategory(user("ADMIN"), "无效", 99L));
        Assertions.assertThrows(BusinessException.class,
            () -> service.updateCategory(user("ADMIN"), 1L, "一级", 2L));
        Assertions.assertThrows(BusinessException.class,
            () -> service.createCategory(user("ADMIN"), "三级", 2L));
    }

    @Test
    void preventsFirstLevelCategoryWithChildrenFromBeingDemoted() {
        FakeCategoryDAO dao = new FakeCategoryDAO();
        dao.add(category(1L, "业务问题", null));
        dao.add(category(2L, "退款", 1L));
        dao.add(category(3L, "账户问题", null));
        CategoryService service = new CategoryService(dao, new NoopAuditLogService());

        Assertions.assertThrows(BusinessException.class,
            () -> service.updateCategory(user("ADMIN"), 1L, "业务问题", 3L));
        Assertions.assertNull(dao.findById(1L).getParentId());
    }

    @Test
    void allowsLeafCategoryToMoveBetweenFirstLevelCategories() {
        FakeCategoryDAO dao = new FakeCategoryDAO();
        dao.add(category(1L, "业务问题", null));
        dao.add(category(2L, "退款", 1L));
        dao.add(category(3L, "账户问题", null));
        CategoryService service = new CategoryService(dao, new NoopAuditLogService());

        service.updateCategory(user("ADMIN"), 2L, "退款", 3L);

        Assertions.assertEquals(3L, dao.findById(2L).getParentId());
    }

    @Test
    void preventsDeletionWhenCategoryIsStillInUse() {
        FakeCategoryDAO dao = new FakeCategoryDAO();
        dao.add(category(1L, "一级", null));
        dao.itemCounts.put(1L, 1);
        CategoryService service = new CategoryService(dao, new NoopAuditLogService());

        Assertions.assertThrows(BusinessException.class, () -> service.deleteCategory(user("ADMIN"), 1L));
        Assertions.assertNotNull(dao.findById(1L));
    }

    private User user(String role) {
        User user = new User();
        user.setUserId(1L);
        user.setRole(role);
        user.setStatus(1);
        return user;
    }

    private Category category(Long id, String name, Long parentId) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        category.setParentId(parentId);
        return category;
    }

    private static class NoopAuditLogService extends AuditLogService {
        @Override
        public void write(String userId, String logType, String level, String message, String operation) {
            // Intentionally empty: service behavior is tested without external logging infrastructure.
        }
    }

    private static class FakeCategoryDAO extends CategoryDAO {
        private final Map<Long, Category> categories = new HashMap<>();
        private final Map<Long, Integer> itemCounts = new HashMap<>();
        private long nextId = 10;

        void add(Category category) {
            categories.put(category.getCategoryId(), category);
        }

        @Override public List<Category> findAll() { return List.copyOf(categories.values()); }
        @Override public Category findById(Long categoryId) { return categories.get(categoryId); }
        @Override public long insert(Category category) {
            category.setCategoryId(nextId++);
            categories.put(category.getCategoryId(), category);
            return category.getCategoryId();
        }
        @Override public void update(Category category) { categories.put(category.getCategoryId(), category); }
        @Override public void delete(Long categoryId) { categories.remove(categoryId); }
        @Override public int countChildren(Long categoryId) {
            return (int) categories.values().stream().filter(c -> categoryId.equals(c.getParentId())).count();
        }
        @Override public int countItems(Long categoryId) { return itemCounts.getOrDefault(categoryId, 0); }
    }
}
