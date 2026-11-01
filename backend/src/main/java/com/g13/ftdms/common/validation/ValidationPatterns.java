package com.g13.ftdms.common.validation;

import java.util.regex.Pattern;

/**
 * 字段校验规则常量（与《接口文档》V1.2 第 12 章「字段校验规则索引」逐条对应）。
 *
 * <p>设计意图：正则集中在一处维护，DTO 只引用常量，避免同一规则在不同 DTO 中被抄成多个版本；
 * 改动规则时只需改这里，并同步更新接口文档第 12 章与 FAR 附录 E。</p>
 *
 * <p>约定（与数据字典一致）：
 * <ul>
 *   <li>一律整串匹配（Java {@code @Pattern} 默认锚定，无需再写 ^ $ 也保持可读）；</li>
 *   <li>正则只保证"形状"，数值区间由 DTO 上的 @DecimalMin/@DecimalMax 再校验一次；</li>
 *   <li>日期类字段的区间（如 [今天−365, 今天+7]）在 Service 层用业务时区判断。</li>
 * </ul></p>
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
        // 纯常量类，禁止实例化
    }

    /** R_USERNAME：4–20 位字母、数字或下划线 */
    public static final String USERNAME = "^[A-Za-z0-9_]{4,20}$";

    /** R_PASSWORD：8–32 位可见 ASCII，须同时含字母与数字 */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,32}$";

    /** R_NICKNAME：1–50 字，允许中英文、数字、空格、下划线、连字符、间隔号，首尾不得为空白 */
    public static final String NICKNAME = "^(?!\\s)[\\u4e00-\\u9fa5A-Za-z0-9_\\-·\\s]{0,49}[^\\s]$";

    /** R_TEXT50：1–50 字（肌群、器械等短文本） */
    public static final String TEXT50 = "^(?!\\s)[\\u4e00-\\u9fa5A-Za-z0-9_\\-·\\s]{0,49}[^\\s]$";

    /** R_NAME100：1–100 字（动作名、食物名、计划标题） */
    public static final String NAME100 = "^(?!\\s)[\\u4e00-\\u9fa5A-Za-z0-9_\\-·（）()\\s]{0,99}[^\\s]$";

    /** R_TEXT100：1–100 字（品牌或规格） */
    public static final String TEXT100 = "^(?!\\s)[\\u4e00-\\u9fa5A-Za-z0-9_\\-·（）()\\s]{0,99}[^\\s]$";

    /** R_TEXT255：1–255 字（营养数据来源等） */
    public static final String TEXT255 = "^(?!\\s)[\\u4e00-\\u9fa5A-Za-z0-9_\\-·（）()，。、：；/＋+%.\\s]{0,254}[^\\s]$";

    /** R_NOTE：0–500 字，可为空，非空时首尾不得为空白 */
    public static final String NOTE = "^$|^(?!\\s)[\\s\\S]{0,499}[^\\s]$";

    /** R_DATE：yyyy-MM-dd（真实性由 LocalDate 解析保证） */
    public static final String DATE = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$";

    /** R_DATETIME：yyyy-MM-dd HH:mm:ss */
    public static final String DATETIME = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01]) ([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$";

    /** R_GENDER：MALE / FEMALE / UNKNOWN */
    public static final String GENDER = "^(MALE|FEMALE|UNKNOWN)$";

    /** R_ROLE：USER / ADMIN */
    public static final String ROLE = "^(USER|ADMIN)$";

    /** R_ACCSTATUS：ACTIVE / DISABLED */
    public static final String ACC_STATUS = "^(ACTIVE|DISABLED)$";

    /** R_PLANSTATUS：PLANNED / COMPLETED / CANCELLED */
    public static final String PLAN_STATUS = "^(PLANNED|COMPLETED|CANCELLED)$";

    /** R_MEAL：BREAKFAST / LUNCH / DINNER / SNACK */
    public static final String MEAL = "^(BREAKFAST|LUNCH|DINNER|SNACK)$";

    /** R_FOODSTATE：RAW / COOKED / BRAND */
    public static final String FOOD_STATE = "^(RAW|COOKED|BRAND)$";

    /** R_SCORE 类以外的排序号：0–99 */
    public static final String ORDER_NO = "^\\d{1,2}$";

    /** 预编译常用正则，供 Service 中做非注解校验时复用（避免每次 Pattern.compile） */
    public static final Pattern P_USERNAME = Pattern.compile(USERNAME);
    public static final Pattern P_PASSWORD = Pattern.compile(PASSWORD);
    public static final Pattern P_MEAL = Pattern.compile(MEAL);
    public static final Pattern P_FOOD_STATE = Pattern.compile(FOOD_STATE);
    public static final Pattern P_PLAN_STATUS = Pattern.compile(PLAN_STATUS);
    public static final Pattern P_GENDER = Pattern.compile(GENDER);
    public static final Pattern P_ACC_STATUS = Pattern.compile(ACC_STATUS);
}
