package com.g13.ftdms.common.util;

import com.g13.ftdms.common.exception.BizException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 日期规则校验工具。
 *
 * <p>把"日期是否合法"的判断集中到一处，避免各 Service 各写一套区间逻辑。
 * 所有"今天"均取自注入的 {@link Clock}（业务时区），保证与服务器时区无关。</p>
 *
 * <p>与接口文档的对应关系：
 * <ul>
 *   <li>{@code R_RECENTDATE}：记录/计划日期落在 [今天−365, 今天+7]；</li>
 *   <li>区间查询：from ≤ to，且跨度 ≤ 366 天。</li>
 * </ul></p>
 */
public final class DateGuard {

    /** 允许补录的最大回溯天数 */
    public static final int MAX_BACK_DAYS = 365;
    /** 允许预排的最大天数 */
    public static final int MAX_AHEAD_DAYS = 7;
    /** 区间查询允许的最大跨度（含首尾） */
    public static final int MAX_RANGE_DAYS = 366;

    /**
     * 日期格式（与接口文档 R_DATE 一致）。
     *
     * <p>必须使用严格解析（STRICT + uuuu）：默认的 SMART 模式会把 2026-02-30
     * 自动"修正"为 2026-02-28，等于把用户输错的日期悄悄改掉，违反"非法输入要报错"的要求。
     * uuuu 表示按纪元年解析，是 STRICT 模式下 LocalDate 的强制写法（yyyy 会抛异常）。</p>
     */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    /** 日期时间格式（与接口文档 R_DATETIME 一致），同样使用严格解析 */
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    private DateGuard() {
    }

    /**
     * 解析日期字符串。
     *
     * <p>为什么不用 {@code LocalDate.parse} 直接解析：正则只能挡住"形状"，
     * 像 {@code 2026-02-30} 这样的非法日期会抛 {@link DateTimeParseException}，
     * 若不加包装将变成 9000（服务器错误），而它本质上是用户输入问题，应返回 1001。</p>
     *
     * @param text  形如 yyyy-MM-dd 的字符串
     * @param label 字段中文名，用于错误提示
     * @return 解析后的日期
     * @throws BizException 1001 格式正确但不是真实日期（如 2 月 30 日）
     */
    public static LocalDate parseDate(String text, String label) {
        try {
            return LocalDate.parse(text, DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw BizException.param(label + " 不是有效日期（请检查月份天数）");
        }
    }

    /**
     * 解析日期时间字符串。
     *
     * <p>注意 {@code LocalDateTime.parse} 默认要求 ISO 的 "T" 分隔符，
     * 而接口约定用空格分隔，因此必须显式带格式化器，否则一律解析失败。</p>
     *
     * @param text  形如 yyyy-MM-dd HH:mm:ss 的字符串
     * @param label 字段中文名
     * @return 解析后的日期时间
     * @throws BizException 1001 非法日期时间
     */
    public static LocalDateTime parseDateTime(String text, String label) {
        try {
            return LocalDateTime.parse(text, DATETIME_FMT);
        } catch (DateTimeParseException ex) {
            throw BizException.param(label + " 不是有效的日期时间（应为 yyyy-MM-dd HH:mm:ss）");
        }
    }

    /**
     * 校验"记录类日期"是否在允许区间内（补录 365 天、预排 7 天）。
     *
     * @param date  待校验日期
     * @param label 字段中文名，用于拼错误提示
     * @param clock 业务时钟
     * @throws BizException 越界时抛出 1001
     */
    public static void requireRecent(LocalDate date, String label, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate min = today.minusDays(MAX_BACK_DAYS);
        LocalDate max = today.plusDays(MAX_AHEAD_DAYS);
        if (date.isBefore(min) || date.isAfter(max)) {
            throw BizException.param(label + " 需在 " + min + " 至 " + max + " 之间（可补录 365 天、预排 7 天）");
        }
    }

    /**
     * 校验区间查询参数。
     *
     * @param from  起始日期（含）
     * @param to    结束日期（含）
     * @throws BizException 起止颠倒或跨度过大时抛出 1001
     */
    public static void requireRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw BizException.param("起始日期不得晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw BizException.param("查询区间不得超过 " + MAX_RANGE_DAYS + " 天");
        }
    }

    /**
     * 校验"不得晚于今天"（如出生日期）。
     *
     * @param date  待校验日期
     * @param label 字段中文名
     * @param clock 业务时钟
     */
    public static void requireNotFuture(LocalDate date, String label, Clock clock) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw BizException.param(label + " 不得晚于当天");
        }
    }
}
