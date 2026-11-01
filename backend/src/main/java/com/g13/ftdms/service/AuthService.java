package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.AuthDtos;
import com.g13.ftdms.entity.UserAccount;
import com.g13.ftdms.repository.UserAccountRepository;
import com.g13.ftdms.security.JwtService;
import com.g13.ftdms.security.TokenBlacklist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 账户与鉴权服务（接口族 A1–A3）。
 *
 * <p>职责边界：只处理"身份"相关逻辑（注册、登录、登出），
 * 不涉及资料修改（在 {@link UserService}）与权限判定（在 Security 层）。</p>
 *
 * <p>安全要点：
 * <ol>
 *   <li>口令仅接受一次明文，立即用 BCrypt 散列，任何路径都不回显；</li>
 *   <li>登录失败统一返回 2001，不区分"用户不存在"与"口令错误"，避免账号枚举；</li>
 *   <li>停用账户返回 2002，这是《接口文档》明确要求的行为；</li>
 *   <li>登出把令牌写入失效名单，直到其自然过期。</li>
 * </ol></p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final Clock clock;

    public AuthService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TokenBlacklist tokenBlacklist, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
        this.clock = clock;
    }

    /**
     * 注册普通用户。
     *
     * @param request 注册请求（字段格式已由 Bean Validation 校验）
     * @return 新用户概要
     * @throws BizException 1006 用户名已存在；1001 出生日期越界
     */
    @Transactional
    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        String username = request.username().trim();
        // 先查重再插入：并发下仍可能撞唯一索引，由全局异常处理兜底转 1006
        if (userRepository.existsByUsername(username)) {
            throw new BizException(ErrorCode.DUPLICATE, "用户名已存在");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setNickname(isBlank(request.nickname()) ? username : request.nickname().trim());
        user.setGender(isBlank(request.gender()) ? "UNKNOWN" : request.gender());
        user.setHeightCm(request.heightCm());
        if (!isBlank(request.birthDate())) {
            LocalDate birth = DateGuard.parseDate(request.birthDate(), "出生日期");
            DateGuard.requireNotFuture(birth, "出生日期", clock);
            user.setBirthDate(birth);
        }

        UserAccount saved = userRepository.save(user);
        log.info("注册成功 userId={} username={}", saved.getId(), saved.getUsername());
        return new AuthDtos.RegisterResponse(saved.getId(), saved.getUsername(), saved.getNickname(),
                saved.getRole(), saved.getCreatedAt());
    }

    /**
     * 登录并签发令牌。
     *
     * @param request 登录请求
     * @return 令牌与用户概要
     * @throws BizException 2001 账号或口令错误；2002 账号已停用
     */
    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        UserAccount user = userRepository.findByUsername(request.username().trim())
                // 用户不存在与口令错误返回同一码，防止通过响应差异枚举账号
                .orElseThrow(() -> new BizException(ErrorCode.BAD_CREDENTIALS, null));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("登录失败（口令错误） username={}", user.getUsername());
            throw new BizException(ErrorCode.BAD_CREDENTIALS, null);
        }
        if ("DISABLED".equals(user.getStatus())) {
            log.warn("登录被拒（账户停用） username={}", user.getUsername());
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, null);
        }

        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRole());
        log.info("登录成功 userId={}", user.getId());
        return new AuthDtos.LoginResponse(token, "Bearer", jwtService.expireSeconds(),
                new AuthDtos.LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole()));
    }

    /**
     * 登出：把当前令牌加入失效名单。
     *
     * @param token 原始令牌（不含 Bearer 前缀）
     */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            // 走到这里说明请求已通过认证，令牌必然存在；保守处理，直接返回成功
            return;
        }
        tokenBlacklist.revoke(token, jwtService.expiryOf(token));
        log.info("用户登出，令牌已加入失效名单，当前名单规模={}", tokenBlacklist.size());
    }

    /** 判断字符串是否为 null 或空白 */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
