package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.api.PageResult;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.dto.CatalogDtos;
import com.g13.ftdms.entity.Food;
import com.g13.ftdms.repository.FoodRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 食物库服务（接口族 D1–D2，并为饮食记录提供食物校验与快照计算依据）。
 *
 * <p>可见性规则与动作库一致：公共食物 + 本人自建食物；已停用的食物不出现在列表中，
 * 但历史饮食记录通过快照字段仍可正确展示。</p>
 */
@Service
public class FoodService {

    private final FoodRepository foodRepository;

    public FoodService(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    /**
     * 分页查询可见食物。
     *
     * @param userId  当前用户
     * @param keyword 名称关键字，空串视为不过滤
     * @param state   生熟状态，空串视为不过滤
     * @param page    页码（1 起）
     * @param size    每页条数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<CatalogDtos.FoodItem> page(Long userId, String keyword, String state, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        Page<Food> result = foodRepository.search(userId, normalize(keyword), normalize(state), pageRequest);
        return PageResult.of(result, f -> new CatalogDtos.FoodItem(
                f.getId(), f.getName(), f.getKcalPer100g(), f.getState(), f.getIsPublic()));
    }

    /**
     * 查询食物详情。
     *
     * @param userId 当前用户
     * @param id     食物编号
     * @return 完整营养信息
     * @throws BizException 1004 不存在、已停用或不可见
     */
    @Transactional(readOnly = true)
    public CatalogDtos.FoodDetail detail(Long userId, Long id) {
        return toDetail(loadVisible(userId, id));
    }

    /**
     * 内部能力：加载并校验"可用于新增饮食记录"的食物。
     *
     * @param userId 当前用户
     * @param foodId 食物编号
     * @return 食物实体
     * @throws BizException 1004 不存在、已停用或不可见
     */
    @Transactional(readOnly = true)
    public Food loadVisible(Long userId, Long foodId) {
        Food food = foodRepository.findById(foodId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "食物不存在"));
        boolean isPublic = food.getIsPublic() != null && food.getIsPublic() == 1;
        if (!isPublic && !userId.equals(food.getCreatedBy())) {
            // 与他人私有食物同等处理为"不存在"，不泄露该编号是否存在
            throw new BizException(ErrorCode.NOT_FOUND, "食物不存在");
        }
        if (!"ACTIVE".equals(food.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "食物已停用");
        }
        return food;
    }

    /**
     * 内部能力：按编号加载食物（不限状态），供修改历史饮食记录时重算快照。
     *
     * @param foodId 食物编号
     * @return 食物实体
     * @throws BizException 1004 食物已被物理删除
     */
    @Transactional(readOnly = true)
    public Food loadForRecalculate(Long foodId) {
        return foodRepository.findById(foodId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "记录关联的食物已不存在，无法重算营养值"));
    }

    /**
     * 内部能力：按编号取食物名称（不判断状态，供历史记录展示）。
     *
     * @param foodId 食物编号
     * @return 食物名称
     * @throws BizException 1004 食物已被物理删除
     */
    @Transactional(readOnly = true)
    public String nameOf(Long foodId) {
        return foodRepository.findById(foodId)
                .map(Food::getName)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "食物不存在"));
    }

    /** 实体 → 详情视图 */
    private CatalogDtos.FoodDetail toDetail(Food f) {
        return new CatalogDtos.FoodDetail(f.getId(), f.getName(), f.getBrand(), f.getKcalPer100g(),
                f.getProteinPer100g(), f.getCarbPer100g(), f.getFatPer100g(), f.getState(), f.getSource());
    }

    /** 空串归一化为 null */
    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
