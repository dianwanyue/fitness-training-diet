package com.g13.ftdms.common.util;

import com.g13.ftdms.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 日期规则单元测试。
 *
 * <p>固定时钟为 2026-11-01（Asia/Shanghai），使"今天"成为确定值，
 * 从而可以精确断言区间边界（今天是 11-01，则可补录到 2025-11-01、预排到 2026-11-08）。</p>
 */
class DateGuardTest {

    /** 固定时钟：2026-11-01 12:00 +08:00 */
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-11-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    @DisplayName("严格解析：2026-02-30 应报错，而不是被修正为 2 月 28 日")
    void rejectsInvalidCalendarDate() {
        BizException ex = assertThrows(BizException.class,
                () -> DateGuard.parseDate("2026-02-30", "记录日期"));
        assertEquals(1001, ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("合法日期解析成功")
    void parsesValidDate() {
        assertEquals(LocalDate.of(2026, 2, 28), DateGuard.parseDate("2026-02-28", "记录日期"));
    }

    @Test
    @DisplayName("记录日期区间：今天−365 与今天+7 为边界，越界报错")
    void recentDateBoundary() {
        assertDoesNotThrow(() -> DateGuard.requireRecent(LocalDate.of(2025, 11, 1), "记录日期", clock));
        assertDoesNotThrow(() -> DateGuard.requireRecent(LocalDate.of(2026, 11, 8), "记录日期", clock));
        assertThrows(BizException.class,
                () -> DateGuard.requireRecent(LocalDate.of(2025, 10, 31), "记录日期", clock));
        assertThrows(BizException.class,
                () -> DateGuard.requireRecent(LocalDate.of(2026, 11, 9), "记录日期", clock));
    }

    @Test
    @DisplayName("区间查询：起止颠倒与跨度超 366 天均报错")
    void rangeValidation() {
        assertDoesNotThrow(() -> DateGuard.requireRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertThrows(BizException.class,
                () -> DateGuard.requireRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)));
        assertThrows(BizException.class,
                () -> DateGuard.requireRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 1)));
    }

    @Test
    @DisplayName("日期时间必须用空格分隔，用 T 应报错")
    void datetimeFormat() {
        assertDoesNotThrow(() -> DateGuard.parseDateTime("2026-11-01 18:30:00", "训练时间"));
        assertThrows(BizException.class,
                () -> DateGuard.parseDateTime("2026-11-01T18:30:00", "训练时间"));
    }
}
