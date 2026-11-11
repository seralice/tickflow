package com.seralice.tickflow.handler.handler;

import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.enums.MessageType;
import com.seralice.tickflow.common.enums.ShieldType;
import com.seralice.tickflow.support.utils.RedisUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 渠道处理器抽象基类（策略模式 + 模板方法模式）
 */
@Slf4j
public abstract class BaseHandler {

    @Autowired
    private HandlerHolder handlerHolder;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private JsonMapper jsonMapper;


    /**
     * 子类声明自己负责的渠道编码（如 40 对应邮件，900 对应 Mock）
     */
    public abstract Integer getChannelCode();

    /**
     * 策略自动注册：Spring 初始化 Bean 之后自动将自身注册到 HandlerHolder
     */
    @PostConstruct
    private void init() {
        handlerHolder.putHandler(getChannelCode(), this);
    }

    /**
     * 模板方法：统一调度入口
     * 后续的限流、前置打点、结果统计等公共逻辑均在此统一收口，然后触发具体子类的 handler
     */
    public boolean doHandler(TaskInfo taskInfo, boolean isRetry) {
        if (!redisUtils.tryMarkConsumed(taskInfo.getMessageId())) {
            log.info("[幂等] 消息已消费过，跳过重复投递！messageId: {}", taskInfo.getMessageId());
            return false;
        }
        try {
            if (isRetry) return handler(taskInfo); //重试直接跳过限流屏蔽策略

            // 关卡 1：夜间拦截
            if (isShield(taskInfo)) {
                if (Objects.equals(taskInfo.getShieldType(), ShieldType.NIGHT_SHIELD_NEXT_DAY.getCode())) {
                    LocalDateTime sendTime = nextMorning();
                    long dueMillis = sendTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    redisUtils.unmarkConsumed(taskInfo.getMessageId());
                    String json = jsonMapper.writeValueAsString(taskInfo);
                    redisUtils.addDelay(taskInfo.getMessageId(), json, dueMillis);
                    log.info("[夜间屏蔽] 消息已顺延发送，messageId: {}, 计划发送时间: {}",
                            taskInfo.getMessageId(), sendTime);
                }
                return false;
            }

            // 关卡 2：内容去重拦截（5 分钟内相同内容只发 1 次）
            if (isDuplicate(taskInfo)) {
                return false;
            }

            // 关卡 3：频次去重拦截（单用户单渠道每日上限，比如暂定每天最多 5 条）
            if (isFrequencyLimit(taskInfo)) {
                return false;
            }


            // 核心下发业务，交给具体子类（如 MockHandler、EmailHandler）去实现
            return handler(taskInfo);
        } catch (RuntimeException e) {
            redisUtils.unmarkConsumed(taskInfo.getMessageId());
            throw e;
        }

    }

    /**
     * 夜间被拦截的消息顺延到的发送时间：凌晨拦截发当天，晚上拦截发次日，均在夜间结束后 1 小时
     */
    private LocalDateTime nextMorning() {
        LocalDate date = LocalDate.now();
        int hour = LocalDateTime.now().getHour();
        if (hour >= ShieldType.NIGHT_END_HOUR) {
            date = date.plusDays(1);
        }
        return date.atTime(ShieldType.NIGHT_END_HOUR + 1, 0);
    }

    private boolean isFrequencyLimit(TaskInfo taskInfo) {
        // 验证码不过滤直接返回
        if (Objects.equals(MessageType.AUTH_CODE.getCode(), taskInfo.getMsgType())) {
            return false;
        }

        for (String receiver : taskInfo.getReceiver()) {
            boolean limit = redisUtils.isFrequencyDuplicate(taskInfo.getSendChannel(), receiver, 5);
            if (limit) {
                // 超限拦截
                log.info("[频次去重] 用户已达到今日发送上限，已拦截！messageId: {}, receiver: {}",
                        taskInfo.getMessageId(), receiver);
                return true;
            }

        }
        //兜底返回false;
        return false;
    }

    private boolean isDuplicate(TaskInfo taskInfo) {
        if (taskInfo.getReceiver() == null || taskInfo.getReceiver().isEmpty()) {
            return false;
        }
        String content = taskInfo.getContentModel().get("content");

        // 挨个检查集合里的每一个接收人！
        for (String receiver : taskInfo.getReceiver()) {
            boolean duplicate = redisUtils.isContentDuplicate(
                    taskInfo.getMessageTemplateId(),
                    receiver,   // 单个接收人，比如 userA
                    content,
                    300,
                    1
            );

            // 只要这个接收人在 5 分钟内收到过同样的内容，立刻拦截！
            if (duplicate) {
                log.info("[内容去重] 5分钟内对用户发送了相同文案，已拦截！messageId: {}, receiver: {}",
                        taskInfo.getMessageId(), receiver);
                return true;
            }
        }

        return false; // 没重复，放行！300,1);
    }

    /**
     * 具体渠道的真实下发逻辑，由各渠道子类实现
     *
     * @param taskInfo 发送任务信息
     * @return true 发送成功；false 发送失败
     */
    public abstract boolean handler(TaskInfo taskInfo);

    private boolean isShield(TaskInfo taskInfo) {
        // 1. 如果配置的是“夜间不屏蔽”（比如验证码、紧急告警），直接放行
        if (Objects.equals(ShieldType.NIGHT_NO_SHIELD.getCode(), taskInfo.getShieldType())) {
            return false; // 不需要屏蔽
        }

        // 2. 判断是不是夜间（时段由 ShieldType 统一定义）
        if (ShieldType.isNight(LocalDateTime.now())) {
            log.info("[夜间屏蔽] 消息处于夜间免打扰时段，已拦截！messageId: {}, shieldType: {}",
                    taskInfo.getMessageId(), taskInfo.getShieldType());
            return true;
        }

        return false;
    }


}
