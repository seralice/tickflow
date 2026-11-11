package com.seralice.tickflow.support.utils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Redis 高性能原子去重工具类（基于 Lua 脚本与滑动窗口）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> deduplicationScript;

    private DefaultRedisScript<Long> frequencyScript;

    private DefaultRedisScript<List> claimScript;

    private static final String DEDUP_PREFIX = "tickflow:dedup:content:";
    private static final String IDEM_PREFIX = "tickflow:idem:";
    private static final String DELAY_KEY = "tickflow:delay";
    private static final String DELAY_BODY_KEY = "tickflow:delay:body";
    private static final String DELAY_PROCESSING_KEY = "tickflow:delay:processing";

    @PostConstruct
    public void init() {
        deduplicationScript = new DefaultRedisScript<>();
        deduplicationScript.setLocation(new ClassPathResource("lua/deduplication.lua"));
        deduplicationScript.setResultType(Long.class);
        frequencyScript = new DefaultRedisScript<>();
        frequencyScript.setLocation(new ClassPathResource("lua/frequency.lua"));
        frequencyScript.setResultType(Long.class);
        claimScript = new DefaultRedisScript<>();
        claimScript.setLocation(new ClassPathResource("lua/claim.lua"));
        claimScript.setResultType(List.class);
    }

    /**
     * 延迟发送写Redis数据
     */
    public void addDelay(String messageId, String message, long dueMillis) {
        stringRedisTemplate.opsForHash().put(DELAY_BODY_KEY, messageId, message);
        stringRedisTemplate.opsForZSet().add(DELAY_KEY, messageId, dueMillis);
    }

    /** 认领到期的延迟消息（租约），返回 messageId 列表 */
    public List<String> claimDelay(long now, long leaseUntil, int limit) {
        List<String> ids = stringRedisTemplate.execute(
                claimScript,
                List.of(DELAY_KEY, DELAY_PROCESSING_KEY),
                String.valueOf(now), String.valueOf(leaseUntil), String.valueOf(limit));
        return ids == null ? List.of() : ids;
    }

    /** 取延迟消息内容 */
    public String getDelayBody(String messageId) {
        return (String) stringRedisTemplate.opsForHash().get(DELAY_BODY_KEY, messageId);
    }

    /** 投递成功后删除：先 ZREM 再 HDEL */
    public void removeDelay(String messageId) {
        stringRedisTemplate.opsForZSet().remove(DELAY_PROCESSING_KEY, messageId);
        stringRedisTemplate.opsForHash().delete(DELAY_BODY_KEY, messageId);
    }

    /** 取消等待中的延迟消息，与调度线程认领竞争同一个 ZREM，只有一方能成功 */
    public boolean cancelDelay(String messageId) {
        Long removed = stringRedisTemplate.opsForZSet().remove(DELAY_KEY, messageId);
        if (removed == null || removed == 0) {
            return false;
        }
        stringRedisTemplate.opsForHash().delete(DELAY_BODY_KEY, messageId);
        return true;
    }


    /**
     * 内容去重检查（基于 ZSet 滑动窗口）
     *
     * @param templateId    消息模板 ID
     * @param receiver      接收者
     * @param content       具体渲染后的文案
     * @param windowSeconds 滑动窗口大小（秒），例如 300 秒（5分钟）
     * @param maxLimit      窗口内允许发送的最大次数（例如 1 次）
     * @return true 代表重复，需要拦截；false 代表未重复，已记录并放行
     */
    public boolean isContentDuplicate(Long templateId, String receiver, String content, long windowSeconds, long maxLimit) {
        try {
            // 1. 生成唯一去重 Key：tickflow:dedup:content:MD5(...)
            String rawText = templateId + "_" + receiver + "_" + (content != null ? content : "");
            String md5 = md5(rawText);
            String redisKey = DEDUP_PREFIX + md5;

            // 2. 组装参数
            long now = System.currentTimeMillis();
            long windowMillis = windowSeconds * 1000L;
            String member = UUID.randomUUID().toString().replace("-", "");
            long expireSeconds = windowSeconds * 2; // Key 过期时间设为窗口的 2 倍，确保自动回收

            // 3. 执行 Lua 脚本
            Long result = stringRedisTemplate.execute(
                    deduplicationScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(maxLimit),
                    member,
                    String.valueOf(expireSeconds)
            );

            // 返回 1 代表重复被拦截
            return result != null && result == 1L;

        } catch (Exception e) {
            log.error("[RedisUtils] 执行去重脚本异常，默认降级放行！key: {}_{}", templateId, receiver, e);
            // 降级原则：缓存异常不能阻断正常消息发送
            return false;
        }
    }

    /**
     * 频次去重检查（基于 Redis INCR 原子计数器与自然日过期）
     *
     * @param channel   渠道编码
     * @param receiver  接收者
     * @param maxLimit  当天允许发送的最大上限次数
     * @return true 代表已达每日上限，需要拦截；false 代表未超限，放行
     */
    public boolean isFrequencyDuplicate(Integer channel, String receiver, int maxLimit) {
        try {
            String key = "tickflow:dedup:frequency:" + channel + ":" + receiver;
            // 计算过期时间
            long seconds = java.time.Duration.between(
                    java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now().with(java.time.LocalTime.MAX)
            ).toSeconds();

            Long count = stringRedisTemplate.execute(
                    frequencyScript,
                    Collections.singletonList(key),
                    String.valueOf(Math.max(seconds, 60))
            );

            return count != null && count > maxLimit;
        } catch (Exception e) {
            log.error("[RedisUtils] 执行频次计数异常，默认降级放行！channel: {}, receiver: {}", channel, receiver, e);
            return false;
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
    /**
     * 标记消息已消费（SETNX），用于防止 Kafka 重复投递导致重复发送
     *
     * @return true 首次消费，放行；false 已消费过，需要跳过
     */
    public boolean tryMarkConsumed(String messageId) {
        try {
            String redisKey = IDEM_PREFIX + messageId;
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1",Duration.ofDays(1));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.error("[RedisUtils] Redis暂时不可用，默认放行 messageId: {}", messageId, e);
            return true;
        }
    }

    public void unmarkConsumed(String messageId) {
        try {
            String redisKey = IDEM_PREFIX + messageId;
            stringRedisTemplate.delete(redisKey);
        } catch (Exception e){
            log.error("[RedisUtils] 删除幂等标记失败，重试可能被误拦！messageId: {}", messageId, e);
        }
    }
}
