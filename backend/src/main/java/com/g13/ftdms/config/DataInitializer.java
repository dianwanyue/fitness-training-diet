package com.g13.ftdms.config;

import com.g13.ftdms.entity.UserAccount;
import com.g13.ftdms.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动引导器：按配置决定是否创建一个演示用管理员账户。
 *
 * <p>为什么需要：管理接口（H 族）要求 ADMIN 角色，而注册接口固定创建 USER，
 * 没有管理员就无法维护动作库与食物库。提供此引导器后，"下载即跑"可完整演示。</p>
 *
 * <p>安全约束：
 * <ul>
 *   <li>只有库中不存在任何 ADMIN 时才创建，不会覆盖已有管理员；</li>
 *   <li>默认关闭（{@code app.bootstrap.enabled=false}），仅 h2 演示 profile 打开；</li>
 *   <li>启动日志明确提示"请立即修改初始口令"。</li>
 * </ul></p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppProperties properties;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppProperties properties, UserAccountRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = properties.getBootstrap();
        if (bootstrap == null || !bootstrap.isEnabled()) {
            return;
        }
        if (userRepository.findFirstByRole("ADMIN").isPresent()) {
            log.info("已存在管理员账户，跳过初始化");
            return;
        }

        UserAccount admin = new UserAccount();
        admin.setUsername(bootstrap.getAdminUsername());
        admin.setPasswordHash(passwordEncoder.encode(bootstrap.getAdminPassword()));
        admin.setNickname("系统管理员");
        admin.setRole("ADMIN");
        admin.setStatus("ACTIVE");
        admin.setGender("UNKNOWN");
        userRepository.save(admin);

        log.warn("已创建演示管理员 {}，初始口令为配置值，请登录后立即修改（PUT /api/v1/users/me/password）",
                bootstrap.getAdminUsername());
    }
}
