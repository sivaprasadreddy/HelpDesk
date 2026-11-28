package dev.sivalabs.helpdesk.staff;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The categories each staff member handles (staff_categories). */
@Repository
class StaffCategoryRepository {
    private final JdbcClient jdbcClient;

    StaffCategoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Category ids by staff id; staff without categories are left out. */
    Map<Long, Set<Long>> findCategoryIds(Collection<Long> staffIds) {
        if (staffIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> result = new HashMap<>();
        jdbcClient
                .sql("select user_id, category_id from staff_categories where user_id in (:ids)")
                .param("ids", staffIds)
                .query((rs, n) -> result.computeIfAbsent(rs.getLong("user_id"), k -> new HashSet<>())
                        .add(rs.getLong("category_id")))
                .list();
        return result;
    }

    /** Ids of staff who handle the category, active or not, in ascending order. */
    List<Long> findStaffIdsByCategoryId(Long categoryId) {
        return jdbcClient
                .sql("select user_id from staff_categories where category_id = :categoryId order by user_id")
                .param("categoryId", categoryId)
                .query(Long.class)
                .list();
    }

    void replaceCategoryIds(Long staffId, Set<Long> categoryIds) {
        jdbcClient
                .sql("delete from staff_categories where user_id = :id")
                .param("id", staffId)
                .update();
        for (var categoryId : categoryIds) {
            jdbcClient
                    .sql("insert into staff_categories(user_id, category_id) values (:id, :categoryId)")
                    .param("id", staffId)
                    .param("categoryId", categoryId)
                    .update();
        }
    }
}
