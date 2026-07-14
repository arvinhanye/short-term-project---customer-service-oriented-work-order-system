package com.ticket.util;

import com.ticket.model.Category;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds consistent, unambiguous category labels for every workbench. */
public final class CategoryDisplayUtil {
    private CategoryDisplayUtil() {
    }

    public static Map<Long, String> buildDisplayNames(List<Category> categories) {
        Map<Long, Category> categoryById = new LinkedHashMap<>();
        for (Category category : categories) {
            categoryById.put(category.getCategoryId(), category);
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Category category : categories) {
            result.put(category.getCategoryId(), buildDisplayName(category, categoryById));
        }
        return result;
    }

    private static String buildDisplayName(Category category, Map<Long, Category> categoryById) {
        List<String> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Category current = category;
        boolean invalid = false;
        while (current != null) {
            Long currentId = current.getCategoryId();
            if (currentId == null || !visited.add(currentId)) {
                invalid = true;
                break;
            }
            path.add(current.getName());
            if (current.getParentId() == null) {
                break;
            }
            current = categoryById.get(current.getParentId());
            if (current == null) {
                path.add("未知父分类");
                invalid = true;
            }
        }
        Collections.reverse(path);
        invalid = invalid || path.size() > 2;
        String joinedPath = String.join(" › ", path);
        if (invalid) {
            return "层级异常｜" + joinedPath;
        }
        return path.size() == 1 ? "一级分类｜" + joinedPath : joinedPath;
    }
}
