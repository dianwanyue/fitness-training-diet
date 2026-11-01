package com.g13.ftdms.service;

import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.UserDtos;
import com.g13.ftdms.entity.UserAccount;
import com.g13.ftdms.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 用户资料服务（接口族 A4–A6）。
 *
 * <p>所有方法都以 {@code userId} 为入参，该值来自令牌解析结果，
 * 不接受任何客户端传入的用户编号——这是"只能操作本人数据"的落地方式。</p>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * 查询当前用户资料。
     *
     * @param userId 当前用户编号（来自令牌）
     * @return 资料视图（不含口令散列）
     * @throws BizException 1002 用户不存在（令牌有效但账户已被物理删除的极端情况）
     */
    @Transactional(readOnly = true)
    public UserDtos.ProfileResponse me(Long userId) {
        UserAccount user = load(userId);
        return new UserDtos.ProfileResponse(user.getId(), user.getUsername(), user.getNickname(),
                user.getGender(), user.getBirthDate(), user.getHeightCm(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }

    /**
     * 修改当前用户资料。
     *
     * <p>字段语义：{@code null} 表示不修改；空字符串表示清除该字段。
     * （接口文档写的是"显式传 null 表示清除"，但 JSON 中"未传"与"传 null"在反序列化后不可区分，
     * 因此本项目约定用空字符串表达清除，并在说明文档中注明。）</p>
     *
     * @param userId  当前用户编号
     * @param request 修改请求
     * @return 修改后的关键字段
     * @throws BizException 1001 出生日期越界
     */
    @Transactional
    public UserDtos.UpdateProfileResponse updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        UserAccount user = load(userId);

        if (request.nickname() != null) {
            user.setNickname(request.nickname().isBlank() ? null : request.nickname().trim());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.birthDate() != null) {
            if (request.birthDate().isBlank()) {
                user.setBirthDate(null);
            } else {
                LocalDate birth = DateGuard.parseDate(request.birthDate(), "出生日期");
                DateGuard.requireNotFuture(birth, "出生日期", clock);
                user.setBirthDate(birth);
            }
        }
        if (request.heightCm() != null) {
            user.setHeightCm(request.heightCm());
        }

        UserAccount saved = userRepository.save(user);
        log.info("资料已更新 userId={}", saved.getId());
        return new UserDtos.UpdateProfileResponse(saved.getId(), saved.getNickname(), saved.getGender(),
                saved.getBirthDate(), saved.getHeightCm());
    }

    /**
     * 修改登录口令。
     *
     * @param userId      当前用户编号
     * @param request     原口令与新口令
     * @throws BizException 2001 原口令错误；1001 新旧口令相同
     */
    @Transactional
    public void changePassword(Long userId, com.g13.ftdms.dto.AuthDtos.ChangePasswordRequest request) {
        UserAccount user = load(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(com.g13.ftdms.common.api.ErrorCode.BAD_CREDENTIALS, "原口令错误");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw BizException.param("新口令不得与原口令相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("口令已修改 userId={}", userId);
    }

    /**
     * 按编号加载用户，不存在时抛 1002。
     *
     * <p>之所以用 1002 而不是 1004：令牌里的 userId 找不到，说明账户已被删除，
     * 属于"登录态失效"，前端应跳转登录页而不是提示"资源不存在"。</p>
     */
    private UserAccount load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(com.g13.ftdms.common.api.ErrorCode.UNAUTHORIZED, "账户不存在或已注销"));
    }
}
