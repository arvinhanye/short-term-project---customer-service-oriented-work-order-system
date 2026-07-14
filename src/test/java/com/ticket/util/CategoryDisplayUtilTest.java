package com.ticket.util;

import com.ticket.model.Category;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CategoryDisplayUtilTest {
    @Test
    void buildsConsistentTwoLevelPaths() {
        Map<Long, String> names = CategoryDisplayUtil.buildDisplayNames(List.of(
            category(1L, "账户问题", null),
            category(2L, "密码找回", 1L)
        ));

        Assertions.assertEquals("一级分类｜账户问题", names.get(1L));
        Assertions.assertEquals("账户问题 › 密码找回", names.get(2L));
    }

    @Test
    void marksLegacyThirdLevelDataAsInvalidAndKeepsFullPath() {
        Map<Long, String> names = CategoryDisplayUtil.buildDisplayNames(List.of(
            category(1L, "账户问题", null),
            category(2L, "密码服务", 1L),
            category(3L, "找回密码", 2L)
        ));

        Assertions.assertEquals("层级异常｜账户问题 › 密码服务 › 找回密码", names.get(3L));
    }

    private Category category(Long id, String name, Long parentId) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        category.setParentId(parentId);
        return category;
    }
}
