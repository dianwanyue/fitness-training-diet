package com.g13.ftdms.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌失效名单（登出后使未过期的令牌立即不可用）。
 *
 * <p>实现选择：课程项目的单实例部署，用内存 Map 即可；多实例部署时应替换为 Redis
 * （把 {@link #revoke} / {@link #isRevoked} 换成 Redis 操作即可，调用方无需改动）。</p>
 *
 * <p>内存控制：只保存"令牌 → 过期时间"，并按分钟清理已自然过期的条目，
 * 因此名单大小上限 ≈ 有效期内登出的人数，不会无限增长。</p>
 */
@Component
public class TokenBlacklist {

    /** key=令牌原文，value=该令牌的过期时间戳（毫秒） */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    /**
     * 把令牌加入失效名单。
     *
     * @param token     令牌原文
     * @param expireAt  令牌自然过期时间（毫秒）；到达该时刻后条目可清除
     */
    public void revoke(String token, long expireAt) {
        revoked.put(token, expireAt);
    }

    /**
     * 判断令牌是否已被登出。
     *
     * @param token 令牌原文
     * @return true 表示已失效，应拒绝该请求
     */
    public boolean isRevoked(String token) {
        Long exp = revoked.get(token);
        if (exp == null) {
            return false;
        }
        if (exp <= System.currentTimeMillis()) {
            // 自然过期，顺手清掉，避免 Map 长期堆积
            revoked.remove(token);
            return false;
        }
        return true;
    }

    /** 每分钟清理一次已过期条目；fixedDelay 保证上次执行完才计时，不会堆积任务 */
    @Scheduled(fixedDelay = 60_000L)
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        revoked.entrySet().removeIf(e -> e.getValue() <= now);
    }

    /** 当前名单规模，仅用于日志与自测观察 */
    public int size() {
        return revoked.size();
    }
}
