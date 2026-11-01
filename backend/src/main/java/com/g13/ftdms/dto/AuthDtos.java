package com.g13.ftdms.dto;

import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 账户与鉴权相关的请求/响应载体（接口族 A）。
 *
 * <p>集中放在一个文件里，用嵌套 record 表达"同一接口族"的归属关系，
 * 避免为每个小对象单独建文件；外部引用形如 {@code AuthDtos.LoginRequest}。</p>
 *
 * <p>校验分工：字段格式由 Bean Validation 注解在进入 Service 前拦截，
 * 业务性校验（用户名是否已存在、口令是否正确）在 Service 内完成。</p>
 */
public final class AuthDtos {

    private AuthDtos() {
        // 仅作命名空间，禁止实例化
    }

    /**
     * A1 注册请求。
     *
     * @param username  登录用户名，4–20 位字母数字下划线
     * @param password  明文口令，8–32 位且含字母与数字
     * @param nickname  昵称，可空（缺省取用户名）
     * @param gender    性别，可空（缺省 UNKNOWN）
     * @param birthDate 出生日期，可空（格式 yyyy-MM-dd，不得晚于今天）
     * @param heightCm  身高（cm），可空，100–250
     */
    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = ValidationPatterns.USERNAME, message = "用户名需为 4–20 位字母、数字或下划线")
            String username,

            @NotBlank(message = "口令不能为空")
            @Pattern(regexp = ValidationPatterns.PASSWORD, message = "口令需为 8–32 位可见字符，且同时包含字母与数字")
            String password,

            @Pattern(regexp = ValidationPatterns.NICKNAME, message = "昵称需为 1–50 字，首尾不得为空白")
            String nickname,

            @Pattern(regexp = ValidationPatterns.GENDER, message = "性别取值需为 MALE / FEMALE / UNKNOWN")
            String gender,

            @Pattern(regexp = ValidationPatterns.DATE, message = "出生日期格式需为 yyyy-MM-dd")
            String birthDate,

            @jakarta.validation.constraints.DecimalMin(value = "100", message = "身高(cm) 需在 100–250 之间")
            @jakarta.validation.constraints.DecimalMax(value = "250", message = "身高(cm) 需在 100–250 之间")
            @jakarta.validation.constraints.Digits(integer = 3, fraction = 2, message = "身高最多 2 位小数")
            java.math.BigDecimal heightCm
    ) {
    }

    /**
     * A2 登录请求。
     *
     * @param username 登录用户名
     * @param password 明文口令
     */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "口令不能为空") String password
    ) {
    }

    /**
     * A1 注册响应。
     *
     * @param id        用户主键
     * @param username  登录用户名
     * @param nickname  昵称
     * @param role      角色（注册用户固定 USER）
     * @param createdAt 创建时间
     */
    public record RegisterResponse(Long id, String username, String nickname, String role,
                                   java.time.LocalDateTime createdAt) {
    }

    /**
     * A2 登录响应。
     *
     * @param token     访问令牌
     * @param tokenType 固定 Bearer
     * @param expiresIn 有效期（秒）
     * @param user      登录用户概要
     */
    public record LoginResponse(String token, String tokenType, long expiresIn, LoginUser user) {
    }

    /**
     * 登录用户概要。
     *
     * @param id       用户主键
     * @param username 用户名
     * @param nickname 昵称
     * @param role     角色
     */
    public record LoginUser(Long id, String username, String nickname, String role) {
    }

    /**
     * A6 修改口令请求。
     *
     * @param oldPassword 原口令
     * @param newPassword 新口令（不得与原口令相同）
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "原口令不能为空")
            @Pattern(regexp = ValidationPatterns.PASSWORD, message = "原口令格式不正确")
            String oldPassword,

            @NotBlank(message = "新口令不能为空")
            @Pattern(regexp = ValidationPatterns.PASSWORD, message = "新口令需为 8–32 位可见字符，且同时包含字母与数字")
            String newPassword
    ) {
    }
}
