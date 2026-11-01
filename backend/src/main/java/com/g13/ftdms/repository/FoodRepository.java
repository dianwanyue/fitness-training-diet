package com.g13.ftdms.repository;

import com.g13.ftdms.entity.Food;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 食物库仓储。
 *
 * <p>与动作库一致：公共食物 + 本人自建食物可见，且只返回 ACTIVE 条目；
 * 已停用的食物不会出现在搜索结果里，但历史饮食记录仍可通过快照字段还原。</p>
 */
public interface FoodRepository extends JpaRepository<Food, Long> {

    /**
     * 分页检索可见食物。
     *
     * @param userId 当前用户
     * @param keyword 名称关键字，为空表示不过滤
     * @param state  生熟状态，为空表示不过滤
     * @param pageable 分页参数
     */
    @Query("""
            select f from Food f
            where f.status = 'ACTIVE'
              and (f.isPublic = 1 or f.createdBy = :userId)
              and (:keyword is null or f.name like concat('%', :keyword, '%'))
              and (:state is null or f.state = :state)
            order by f.isPublic desc, f.id asc
            """)
    Page<Food> search(@Param("userId") Long userId,
                      @Param("keyword") String keyword,
                      @Param("state") String state,
                      Pageable pageable);
}
