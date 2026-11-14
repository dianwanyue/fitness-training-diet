package com.g13.ftdms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.api.PageResult;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.config.AiProperties;
import com.g13.ftdms.dto.AiDtos;
import com.g13.ftdms.entity.AiConsent;
import com.g13.ftdms.entity.AiReport;
import com.g13.ftdms.repository.AiConsentRepository;
import com.g13.ftdms.repository.AiReportRepository;
import com.g13.ftdms.service.ai.AiContentGuard;
import com.g13.ftdms.service.ai.AiModelClient;
import com.g13.ftdms.service.ai.AiModelException;
import com.g13.ftdms.service.ai.AiSummary;
import com.g13.ftdms.service.ai.AiSummaryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 健身分析服务（接口族 G1–G7）。
 *
 * <p>设计原则与 SRS 第 3.3.1 节 FR-24～FR-29 一致：</p>
 * <ol>
 *   <li><b>显式授权</b>：G2 授权、G3 撤回；未授权或已撤回时 G4 返回 5001；</li>
 *   <li><b>先算后解释</b>：统计值由 {@link AiSummaryBuilder} 按 F1–F3 同一口径算出，
 *       模型只接收摘要、只负责组织语言；</li>
 *   <li><b>数据不足不硬编</b>：有效记录少于 {@code app.ai.min-valid-days} 天返回 5003，不落库；</li>
 *   <li><b>失败降级</b>：模型不可达、超时或输出为空时写入 DEGRADED，
 *       保留 summaryJson 供核对，正文为空，不编造结论。</li>
 * </ol>
 *
 * <p>生成方法刻意不加 {@code @Transactional}：模型调用可能持续数十秒，
 * 不应占用数据库连接与事务；各读写步骤由 Repository 自身保证一致性。</p>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    /** 首版唯一授权范围 */
    private static final String DEFAULT_SCOPE = "BODY_DIET_TRAINING_30D";
    private static final String GRANTED = "GRANTED";
    private static final String REVOKED = "REVOKED";

    /** 固定系统提示：限制模型只解释、不造数、不作医疗建议 */
    private static final String SYSTEM_PROMPT = """
            你是 FTDMS（健身训练与饮食管理系统）的分析助手。只能依据用户提供的统计摘要组织语言：
            1. 只使用摘要中已有的数字与事实，不得推测、补全或编造任何数据；
            2. 摘要中缺失的项目，明确写「该项暂无足够记录，无法判断」，不要用 0 代替；
            3. 输出严格分为五段，依次以【数据概况】【训练执行】【营养摄入】【体重趋势】【可采取的调整】开头；
            4. 每段 1–3 句，全文不超过 900 字；
            5. 仅提供一般健身与饮食参考，不作疾病诊断或治疗建议；
            6. 不使用 Markdown 表格、代码块或项目符号。
            """;

    private static final String DEGRADED_NOTICE =
            "模型服务暂不可用，本次仅保留统计值、未生成解释文本；请稍后重试。系统不会编造分析结论。本结果仅供一般健身参考。";

    private final AiConsentRepository consentRepository;
    private final AiReportRepository reportRepository;
    private final AiSummaryBuilder summaryBuilder;
    private final AiModelClient modelClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiService(AiConsentRepository consentRepository, AiReportRepository reportRepository,
                     AiSummaryBuilder summaryBuilder, AiModelClient modelClient,
                     AiProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.consentRepository = consentRepository;
        this.reportRepository = reportRepository;
        this.summaryBuilder = summaryBuilder;
        this.modelClient = modelClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * G1 查询授权状态。
     *
     * @param userId 当前用户
     * @return 最近一次授权记录；从未授权时 granted=false
     */
    @Transactional(readOnly = true)
    public AiDtos.ConsentResponse consent(Long userId) {
        return consentRepository.findFirstByUserIdOrderByIdDesc(userId)
                .map(c -> new AiDtos.ConsentResponse(GRANTED.equals(c.getStatus()),
                        c.getScope(), c.getGrantedAt(), c.getRevokedAt()))
                .orElseGet(() -> new AiDtos.ConsentResponse(false, DEFAULT_SCOPE, null, null));
    }

    /**
     * G2 授予授权。
     *
     * <p>同一用户已有有效授权时幂等返回原记录，不重复插入；
     * 撤回后再次授权则新增一条记录，保留完整的授权—撤回历史。</p>
     *
     * @param userId 当前用户
     * @param scope  授权范围（首版仅 BODY_DIET_TRAINING_30D）
     * @return 授权结果
     */
    @Transactional
    public AiDtos.ConsentGrantResponse grant(Long userId, String scope) {
        if (!DEFAULT_SCOPE.equals(scope)) {
            throw BizException.param("授权范围首版仅支持 " + DEFAULT_SCOPE);
        }
        AiConsent latest = consentRepository.findFirstByUserIdOrderByIdDesc(userId).orElse(null);
        if (latest != null && GRANTED.equals(latest.getStatus())) {
            return new AiDtos.ConsentGrantResponse(true, latest.getScope(), latest.getGrantedAt());
        }

        AiConsent consent = new AiConsent();
        consent.setUserId(userId);
        consent.setScope(scope);
        consent.setGrantedAt(LocalDateTime.now(clock));
        consent.setStatus(GRANTED);
        AiConsent saved = consentRepository.save(consent);
        log.info("AI 授权 userId={} consentId={}", userId, saved.getId());
        return new AiDtos.ConsentGrantResponse(true, saved.getScope(), saved.getGrantedAt());
    }

    /**
     * G3 撤回授权。
     *
     * @param userId 当前用户
     * @return 撤回时间
     * @throws BizException 5001 当前无有效授权
     */
    @Transactional
    public AiDtos.ConsentRevokeResponse revoke(Long userId) {
        AiConsent latest = consentRepository.findFirstByUserIdOrderByIdDesc(userId)
                .filter(c -> GRANTED.equals(c.getStatus()))
                .orElseThrow(() -> new BizException(ErrorCode.AI_NOT_AUTHORIZED, "当前无有效 AI 分析授权"));

        LocalDateTime now = LocalDateTime.now(clock);
        latest.setStatus(REVOKED);
        latest.setRevokedAt(now);
        consentRepository.save(latest);
        log.info("AI 授权撤回 userId={} consentId={}", userId, latest.getId());
        return new AiDtos.ConsentRevokeResponse(false, now);
    }

    /**
     * G4 生成分析报告。
     *
     * @param userId     当前用户
     * @param periodDays 分析区间（7/30，缺省 30）
     * @return 报告视图（含统计口径与免责提示）
     * @throws BizException 1001 区间非法；5001 未授权；5003 有效记录不足
     */
    public AiDtos.ReportResponse generate(Long userId, Integer periodDays) {
        int period = periodDays == null ? 30 : periodDays;
        if (period != 7 && period != 30) {
            throw BizException.param("分析区间只能是 7 或 30 天");
        }
        requireConsent(userId);

        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(period - 1L);
        AiSummary summary = summaryBuilder.build(userId, period, from, to);
        if (summary.validDays() < properties.getMinValidDays()) {
            throw new BizException(ErrorCode.AI_INSUFFICIENT_DATA,
                    "分析区间内有效记录少于 " + properties.getMinValidDays() + " 天，暂不生成报告");
        }

        String summaryJson = summaryBuilder.toJson(summary);
        String content = "";
        String status = "DEGRADED";
        String modelVersion = properties.getModel();

        try {
            AiModelClient.Result result = modelClient.chat(SYSTEM_PROMPT,
                    "统计摘要（JSON）：\n" + summaryJson + "\n请按系统提示生成五段式分析。");
            String cleaned = cleanContent(result.content());
            if (!cleaned.isBlank()) {
                content = cleaned;
                status = "SUCCESS";
                modelVersion = result.model();
                AiContentGuard.Review review = AiContentGuard.review(content, summary);
                if (!review.consistent()) {
                    log.warn("AI 输出与摘要不一致 userId={} missingSections={} missingValues={}",
                            userId, review.missingSections(), review.missingValues());
                }
            }
        } catch (AiModelException ex) {
            log.warn("AI 模型调用失败，按 DEGRADED 落库 userId={} reason={}", userId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("AI 生成出现未预期异常，按 DEGRADED 落库 userId={}", userId, ex);
        }

        AiReport report = new AiReport();
        report.setUserId(userId);
        report.setPeriodStart(from);
        report.setPeriodEnd(to);
        report.setSummaryJson(summaryJson);
        report.setContent(content);
        report.setModelName(properties.getModelName());
        report.setModelVersion(modelVersion);
        report.setStatus(status);
        AiReport saved = reportRepository.save(report);

        log.info("AI 报告生成 userId={} reportId={} status={} validDays={}",
                userId, saved.getId(), status, summary.validDays());
        return toResponse(saved);
    }

    /**
     * G5 分页查询历史报告。
     *
     * @param userId 当前用户
     * @param page   页码（1 起）
     * @param size   每页条数（1–100）
     * @return 报告摘要分页
     */
    @Transactional(readOnly = true)
    public PageResult<AiDtos.ReportSummary> list(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AiReport> result = reportRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        List<AiDtos.ReportSummary> list = result.getContent().stream()
                .map(r -> new AiDtos.ReportSummary(r.getId(), r.getPeriodStart(), r.getPeriodEnd(),
                        periodDays(r), r.getStatus(), validDays(r), r.getCreatedAt()))
                .toList();
        return PageResult.of(list, page, size, result.getTotalElements());
    }

    /**
     * G6 查询报告详情（含输入摘要）。
     *
     * @param userId 当前用户
     * @param id     报告编号
     * @return 详情
     * @throws BizException 1004 不存在或不属于本人
     */
    @Transactional(readOnly = true)
    public AiDtos.ReportDetail detail(Long userId, Long id) {
        AiReport report = reportRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "报告不存在或不属于本人"));
        return new AiDtos.ReportDetail(report.getId(), report.getPeriodStart(), report.getPeriodEnd(),
                periodDays(report), report.getStatus(), nullToEmpty(report.getContent()),
                parseSummary(report.getSummaryJson()), report.getModelName(), report.getModelVersion(),
                validDays(report), report.getCreatedAt(), noticeFor(report));
    }

    /**
     * G7 删除报告（物理删除，不可恢复）。
     *
     * @param userId 当前用户
     * @param id     报告编号
     * @throws BizException 1004 不存在或不属于本人
     */
    @Transactional
    public void delete(Long userId, Long id) {
        AiReport report = reportRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "报告不存在或不属于本人"));
        reportRepository.delete(report);
        log.info("AI 报告删除 userId={} reportId={}", userId, id);
    }

    /** 有效授权校验：没有授权或最近一次已撤回时返回 5001 */
    private void requireConsent(Long userId) {
        consentRepository.findFirstByUserIdOrderByIdDesc(userId)
                .filter(c -> GRANTED.equals(c.getStatus()))
                .orElseThrow(() -> new BizException(ErrorCode.AI_NOT_AUTHORIZED,
                        "未授权或已撤回 AI 分析授权，请先开启授权"));
    }

    /** 去掉模型可能带出的代码围栏，并按配置截断超长正文 */
    private String cleanContent(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine > 0) {
                text = text.substring(firstLine + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        int max = Math.max(properties.getMaxContentChars(), 100);
        if (text.length() > max) {
            text = text.substring(0, max).trim();
        }
        return text;
    }

    /** 成功报告的基础提示；输出核对不通过时追加“以统计值为准” */
    private String noticeFor(AiReport report) {
        if (!"SUCCESS".equals(report.getStatus())) {
            return DEGRADED_NOTICE;
        }
        String base = "本报告由后端统计值与本地模型生成，仅供一般健身参考，不构成医疗或营养处方。"
                + "分析区间 " + periodDays(report) + " 天，有效记录 " + validDays(report)
                + " 天；缺失日期未计入均值，完成率分母已剔除已取消计划。";
        AiContentGuard.Review review = AiContentGuard.review(report.getContent(),
                parseSummary(report.getSummaryJson()));
        if (!review.consistent()) {
            base = base + " 模型输出与统计摘要存在未对应项（"
                    + String.join("、", combine(review)) + "），请以统计值为准。";
        }
        return base;
    }

    /** 生成响应（G4） */
    private AiDtos.ReportResponse toResponse(AiReport report) {
        return new AiDtos.ReportResponse(report.getId(), report.getPeriodStart(), report.getPeriodEnd(),
                periodDays(report), report.getStatus(), nullToEmpty(report.getContent()),
                report.getModelName(), report.getModelVersion(), validDays(report),
                report.getCreatedAt(), noticeFor(report));
    }

    /** 区间天数由起止日期推导，不单独落库，避免与日期不一致 */
    private int periodDays(AiReport report) {
        return (int) ChronoUnit.DAYS.between(report.getPeriodStart(), report.getPeriodEnd()) + 1;
    }

    /** 有效天数取自输入摘要；摘要损坏时按 0 处理，不阻断列表查询 */
    private int validDays(AiReport report) {
        return parseSummary(report.getSummaryJson()).path("validDays").asInt(0);
    }

    /** 摘要字符串 → JSON 树；解析失败返回空对象，保证详情接口可读 */
    private JsonNode parseSummary(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            log.warn("AI 摘要 JSON 无法解析，返回空对象");
            return objectMapper.createObjectNode();
        }
    }

    /** 合并核对结果，用于 notice 提示 */
    private List<String> combine(AiContentGuard.Review review) {
        List<String> items = new ArrayList<>();
        review.missingSections().forEach(s -> items.add("缺少段落：" + s));
        items.addAll(review.missingValues());
        return items.isEmpty() ? List.of("存在未对应项") : items;
    }

    /** content 列可为空，但响应统一输出空字符串，避免前端处理 null */
    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
