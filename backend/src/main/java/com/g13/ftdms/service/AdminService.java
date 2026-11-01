package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.dto.AdminDtos;
import com.g13.ftdms.dto.CatalogDtos;
import com.g13.ftdms.entity.Exercise;
import com.g13.ftdms.entity.Food;
import com.g13.ftdms.entity.UserAccount;
import com.g13.ftdms.repository.ExerciseRepository;
import com.g13.ftdms.repository.FoodRepository;
import com.g13.ftdms.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 后台管理服务（接口族 H1–H5）。
 *
 * <p>调用前提：请求已经过 Security 层校验（{@code /api/v1/admin/**} 要求 ADMIN 角色），
 * 因此本服务不再重复判断角色，只处理业务规则；但会额外做"不得停用自己"的自我保护。</p>
 *
 * <p>为什么用停用而不是删除：动作/食物被历史记录引用（plan_item、diet_record），
 * 物理删除会让历史数据失去参照；停用后不再出现在可选择列表中，历史仍可读。</p>
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final ExerciseRepository exerciseRepository;
    private final FoodRepository foodRepository;
    private final UserAccountRepository userRepository;

    public AdminService(ExerciseRepository exerciseRepository, FoodRepository foodRepository,
                        UserAccountRepository userRepository) {
        this.exerciseRepository = exerciseRepository;
        this.foodRepository = foodRepository;
        this.userRepository = userRepository;
    }

    /**
     * H1 新增公共动作。
     *
     * @param request 动作名称、肌群、器械
     * @return 新增结果
     */
    @Transactional
    public CatalogDtos.AdminChangeResponse createExercise(CatalogDtos.AdminExerciseCreateRequest request) {
        Exercise exercise = new Exercise();
        exercise.setName(request.name().trim());
        exercise.setMuscleGroup(trimToNull(request.muscleGroup()));
        exercise.setEquipment(trimToNull(request.equipment()));
        exercise.setIsPublic(1);
        exercise.setStatus("ACTIVE");
        Exercise saved = exerciseRepository.save(exercise);
        log.info("新增公共动作 id={} name={}", saved.getId(), saved.getName());
        return new CatalogDtos.AdminChangeResponse(saved.getId(), saved.getName(), saved.getStatus());
    }

    /**
     * H2 修改或停用动作。
     *
     * @param id      动作编号
     * @param request 待修改字段（null 表示不修改）
     * @return 修改后的状态
     * @throws BizException 1004 动作不存在
     */
    @Transactional
    public CatalogDtos.AdminChangeResponse updateExercise(Long id, CatalogDtos.AdminExerciseUpdateRequest request) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "动作不存在"));
        if (request.name() != null) {
            exercise.setName(request.name().trim());
        }
        if (request.muscleGroup() != null) {
            exercise.setMuscleGroup(trimToNull(request.muscleGroup()));
        }
        if (request.equipment() != null) {
            exercise.setEquipment(trimToNull(request.equipment()));
        }
        if (request.status() != null) {
            exercise.setStatus(request.status());
        }
        Exercise saved = exerciseRepository.save(exercise);
        log.info("修改公共动作 id={} status={}", saved.getId(), saved.getStatus());
        return new CatalogDtos.AdminChangeResponse(saved.getId(), saved.getName(), saved.getStatus());
    }

    /**
     * H3 新增公共食物。
     *
     * @param request 食物营养信息（source 必填）
     * @return 新增结果
     */
    @Transactional
    public CatalogDtos.AdminChangeResponse createFood(CatalogDtos.AdminFoodCreateRequest request) {
        Food food = new Food();
        food.setName(request.name().trim());
        food.setBrand(trimToNull(request.brand()));
        food.setKcalPer100g(nz(request.kcalPer100g()));
        food.setProteinPer100g(nz(request.proteinPer100g()));
        food.setCarbPer100g(nz(request.carbPer100g()));
        food.setFatPer100g(nz(request.fatPer100g()));
        food.setState(request.state());
        food.setSource(request.source().trim());
        food.setIsPublic(1);
        food.setStatus("ACTIVE");
        Food saved = foodRepository.save(food);
        log.info("新增公共食物 id={} name={} source={}", saved.getId(), saved.getName(), saved.getSource());
        return new CatalogDtos.AdminChangeResponse(saved.getId(), saved.getName(), saved.getStatus());
    }

    /**
     * H4 修改或停用食物。
     *
     * <p>注意：修改营养值不会回溯历史饮食记录——历史记录保存的是当时的快照，
     * 这正是"快照"字段存在的意义。</p>
     *
     * @param id      食物编号
     * @param request 待修改字段（null 表示不修改）
     * @return 修改后的状态
     * @throws BizException 1004 食物不存在
     */
    @Transactional
    public CatalogDtos.AdminChangeResponse updateFood(Long id, CatalogDtos.AdminFoodUpdateRequest request) {
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "食物不存在"));
        if (request.kcalPer100g() != null) {
            food.setKcalPer100g(request.kcalPer100g());
        }
        if (request.proteinPer100g() != null) {
            food.setProteinPer100g(request.proteinPer100g());
        }
        if (request.carbPer100g() != null) {
            food.setCarbPer100g(request.carbPer100g());
        }
        if (request.fatPer100g() != null) {
            food.setFatPer100g(request.fatPer100g());
        }
        if (request.source() != null) {
            food.setSource(request.source().trim());
        }
        if (request.status() != null) {
            food.setStatus(request.status());
        }
        Food saved = foodRepository.save(food);
        log.info("修改公共食物 id={} status={}", saved.getId(), saved.getStatus());
        return new CatalogDtos.AdminChangeResponse(saved.getId(), saved.getName(), saved.getStatus());
    }

    /**
     * H5 变更账户状态。
     *
     * @param operatorId 操作人（管理员）编号，用于阻止"停用自己"
     * @param userId     目标用户编号
     * @param status     ACTIVE / DISABLED
     * @return 变更结果
     * @throws BizException 1004 用户不存在；1005 停用自己
     */
    @Transactional
    public AdminDtos.UserStatusResponse changeUserStatus(Long operatorId, Long userId, String status) {
        if (operatorId.equals(userId) && "DISABLED".equals(status)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "不能停用当前登录的管理员账户");
        }
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
        user.setStatus(status);
        UserAccount saved = userRepository.save(user);
        log.info("账户状态变更 operatorId={} userId={} status={}", operatorId, userId, status);
        return new AdminDtos.UserStatusResponse(saved.getId(), saved.getStatus());
    }

    /** 空白字符串归一化为 null */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** null 营养值按 0 处理 */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
