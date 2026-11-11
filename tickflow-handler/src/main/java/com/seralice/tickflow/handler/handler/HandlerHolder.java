package com.seralice.tickflow.handler.handler;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道处理器路由中心（策略模式注册表）
 */
@Component
public class HandlerHolder {

    /**
     * 维护 channelCode -> BaseHandler 映射
     */
    private final Map<Integer, BaseHandler> handlers = new ConcurrentHashMap<>();

    public void putHandler(Integer channelCode, BaseHandler handler) {
        handlers.put(channelCode, handler);
    }

    public BaseHandler route(Integer channelCode) {
        return handlers.get(channelCode);
    }
}
