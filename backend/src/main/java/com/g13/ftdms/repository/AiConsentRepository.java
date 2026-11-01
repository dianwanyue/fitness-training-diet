package com.g13.ftdms.repository;

import com.g13.ftdms.entity.AiConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * AI 分析授权仓储（数据层已就位，接口在 W7 实现）。
 *
 * <p>取"最近一条有效授权"作为判断依据：授权可撤回，撤回后新增一条 REVOKED 记录，
 * 因此按 id 倒序取第一条即可得到当前有效状态。</p>
 */
public interface AiConsentRepository extends JpaRepository<AiConsent, Long> {

    /** 当前用户的授权状态（最近一条） */
    Optional<AiConsent> findFirstByUserIdOrderByIdDesc(Long userId);

    /** 授权/撤回历史，按时间倒序 */
    List<AiConsent> findByUserIdOrderByIdDesc(Long userId);

    /** 是否存在生效中的授权 */
    boolean existsByUserIdAndStatus(Long userId, String status);
}
