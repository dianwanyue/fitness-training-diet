package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.BodyDtos;
import com.g13.ftdms.entity.BodyRecord;
import com.g13.ftdms.repository.BodyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 身体数据服务（接口族 E1–E3）。
 *
 * <p>规则：同一用户同一日期仅一条记录（数据库唯一约束），
 * 因此写入采用"先查后改"的 UPSERT 语义；查询区间缺失日期不返回、不补零。</p>
 */
@Service
public class BodyService {

    private static final Logger log = LoggerFactory.getLogger(BodyService.class);

    private final BodyRecordRepository bodyRepository;
    private final Clock clock;

    public BodyService(BodyRecordRepository bodyRepository, Clock clock) {
        this.bodyRepository = bodyRepository;
        this.clock = clock;
    }

    /**
     * 记录或更新体重。
     *
     * @param userId  当前用户
     * @param request 日期、体重与备注
     * @return 写入后的记录
     * @throws BizException 1001 日期越界
     */
    @Transactional
    public BodyDtos.BodyItem upsert(Long userId, BodyDtos.BodyUpsertRequest request) {
        LocalDate date = DateGuard.parseDate(request.recordDate(), "记录日期");
        DateGuard.requireRecent(date, "记录日期", clock);

        BodyRecord record = bodyRepository.findByUserIdAndRecordDate(userId, date)
                .orElseGet(BodyRecord::new);
        boolean isNew = record.getId() == null;
        record.setUserId(userId);
        record.setRecordDate(date);
        record.setWeightKg(request.weightKg());
        record.setNote(request.note());

        BodyRecord saved = bodyRepository.save(record);
        log.info("{}体重记录 userId={} date={} weight={}", isNew ? "新增" : "更新", userId, date, saved.getWeightKg());
        return new BodyDtos.BodyItem(saved.getId(), saved.getRecordDate(), saved.getWeightKg());
    }

    /**
     * 查询体重记录。
     *
     * @param userId 当前用户
     * @param from   起始日期（含）
     * @param to     结束日期（含）
     * @return 记录列表 + 有效天数 + 条数
     */
    @Transactional(readOnly = true)
    public BodyDtos.BodyListResponse list(Long userId, LocalDate from, LocalDate to) {
        DateGuard.requireRange(from, to);
        List<BodyDtos.BodyItem> items = bodyRepository
                .findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(userId, from, to)
                .stream()
                .map(r -> new BodyDtos.BodyItem(r.getId(), r.getRecordDate(), r.getWeightKg()))
                .toList();
        // 同日唯一，因此条数即有效天数；仍分别返回，便于前端标注"有效记录天数"口径
        return new BodyDtos.BodyListResponse(items, items.size(), items.size());
    }

    /**
     * 删除体重记录。
     *
     * @param userId 当前用户
     * @param id     记录编号
     * @throws BizException 1004 不存在或不属于本人
     */
    @Transactional
    public void delete(Long userId, Long id) {
        BodyRecord record = bodyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "体重记录不存在或不属于本人"));
        bodyRepository.delete(record);
        log.info("删除体重记录 userId={} id={}", userId, id);
    }
}
