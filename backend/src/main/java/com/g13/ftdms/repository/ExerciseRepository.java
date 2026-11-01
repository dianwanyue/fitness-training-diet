package com.g13.ftdms.repository;

import com.g13.ftdms.entity.Exercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 动作库仓储。
 *
 * <p>可见性规则：公共动作（is_public=1）人人可见，自定义动作只有创建者本人可见。
 * 该规则写在查询里而不是业务层过滤，避免"查全表再筛"造成的性能与越权风险。</p>
 */
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * 分页检索可见动作。
     *
     * @param userId      当前用户（用于判断自定义动作归属）
     * @param keyword     名称关键字，为空表示不过滤
     * @param muscleGroup 肌群精确匹配，为空表示不过滤
     * @param pageable    分页参数
     */
    @Query("""
            select e from Exercise e
            where e.status = 'ACTIVE'
              and (e.isPublic = 1 or e.createdBy = :userId)
              and (:keyword is null or e.name like concat('%', :keyword, '%'))
              and (:muscleGroup is null or e.muscleGroup = :muscleGroup)
            order by e.isPublic desc, e.id asc
            """)
    Page<Exercise> search(@Param("userId") Long userId,
                          @Param("keyword") String keyword,
                          @Param("muscleGroup") String muscleGroup,
                          Pageable pageable);

    /** 按主键批量查询，用于把计划明细/训练组里的 exerciseId 换成名称 */
    List<Exercise> findByIdIn(Collection<Long> ids);
}
