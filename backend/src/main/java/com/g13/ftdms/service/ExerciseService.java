package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.api.PageResult;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.dto.CatalogDtos;
import com.g13.ftdms.entity.Exercise;
import com.g13.ftdms.repository.ExerciseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作库服务（接口族 C1，并为计划/训练提供动作校验能力）。
 *
 * <p>该服务的两个角色：
 * <ol>
 *   <li>面向用户的只读查询（分页检索可见动作）；</li>
 *   <li>面向其它服务的内部能力（批量校验动作是否存在、是否可用、是否可见）。</li>
 * </ol>
 * 把"动作是否可用"的判断收在这里，可保证计划与训练记录使用同一套规则。</p>
 */
@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;

    public ExerciseService(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * 分页查询可见动作。
     *
     * @param userId      当前用户
     * @param keyword     名称关键字，空串视为不过滤
     * @param muscleGroup 肌群，空串视为不过滤
     * @param page        页码（1 起）
     * @param size        每页条数（1–100）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<CatalogDtos.ExerciseItem> page(Long userId, String keyword, String muscleGroup,
                                                     int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Exercise> result = exerciseRepository.search(userId, normalize(keyword), normalize(muscleGroup), pageRequest);
        return PageResult.of(result, e -> new CatalogDtos.ExerciseItem(
                e.getId(), e.getName(), e.getMuscleGroup(), e.getEquipment(), e.getIsPublic()));
    }

    /**
     * 内部能力：批量加载并在必要时校验动作可用性。
     *
     * @param ids    动作编号集合
     * @param userId 当前用户（用于判断自定义动作归属）
     * @return 编号 → 实体
     * @throws BizException 1004 存在编号不存在、已停用或不属于本人的动作
     */
    @Transactional(readOnly = true)
    public Map<Long, Exercise> loadUsable(Collection<Long> ids, Long userId) {
        List<Exercise> list = exerciseRepository.findByIdIn(ids);
        Map<Long, Exercise> map = new HashMap<>();
        for (Exercise e : list) {
            // 可见性：公共动作人人可用；自定义动作只有创建者本人可用
            boolean isPublic = e.getIsPublic() != null && e.getIsPublic() == 1;
            boolean visible = isPublic || userId.equals(e.getCreatedBy());
            boolean active = "ACTIVE".equals(e.getStatus());
            if (visible && active) {
                map.put(e.getId(), e);
            }
        }
        if (map.size() != ids.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "存在不可用的动作（不存在、已停用或不属于本人）");
        }
        return map;
    }

    /** 空串归一化为 null，避免查询条件里出现 like '%%' 的无效比较 */
    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * 内部能力：批量取动作名称（不做可见性校验，仅用于历史数据的展示装配）。
     *
     * <p>为什么不做校验：计划明细与训练组里保存的是历史引用，即使动作后来被停用，
     * 详情页仍应显示名称而不是报错；名称取不到时由调用方给出占位文案。</p>
     *
     * @param ids 动作编号集合
     * @return 编号 → 名称
     */
    @Transactional(readOnly = true)
    public Map<Long, String> nameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Exercise e : exerciseRepository.findByIdIn(ids)) {
            map.put(e.getId(), e.getName());
        }
        return map;
    }
}
