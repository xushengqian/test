package com.ivr.system.service;

import com.ivr.system.handler.impl.ActionNodeHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 动作处理器注册表
 * 管理自定义动作处理器
 */
@Slf4j
@Component
public class ActionHandlerRegistry {

    private final Map<String, ActionNodeHandler.ActionHandler> handlers = new HashMap<>();

    @PostConstruct
    public void init() {
        registerDefaultHandlers();
    }

    /**
     * 注册默认处理器
     */
    private void registerDefaultHandlers() {
        registerHandler("log", (session, params) -> {
            String message = params != null ? String.valueOf(params.get("message")) : "No message";
            log.info("IVR Action Log - Session [{}]: {}", session.getSessionId(), message);
            return com.ivr.system.model.IvrResult.success();
        });

        registerHandler("setVariable", (session, params) -> {
            if (params != null) {
                String name = (String) params.get("name");
                Object value = params.get("value");
                if (name != null) {
                    session.setVariable(name, value);
                }
            }
            return com.ivr.system.model.IvrResult.success();
        });

        registerHandler("httpRequest", (session, params) -> {
            log.info("HTTP request action - Session [{}]", session.getSessionId());
            return com.ivr.system.model.IvrResult.success();
        });

        log.info("Registered {} default action handlers", handlers.size());
    }

    /**
     * 注册处理器
     *
     * @param name    处理器名称
     * @param handler 处理器实现
     */
    public void registerHandler(String name, ActionNodeHandler.ActionHandler handler) {
        handlers.put(name, handler);
        log.debug("Registered action handler: {}", name);
    }

    /**
     * 获取处理器
     *
     * @param name 处理器名称
     * @return 处理器实例
     */
    public ActionNodeHandler.ActionHandler getHandler(String name) {
        return handlers.get(name);
    }

    /**
     * 检查处理器是否存在
     *
     * @param name 处理器名称
     * @return 是否存在
     */
    public boolean hasHandler(String name) {
        return handlers.containsKey(name);
    }

    /**
     * 移除处理器
     *
     * @param name 处理器名称
     */
    public void removeHandler(String name) {
        handlers.remove(name);
        log.debug("Removed action handler: {}", name);
    }

    /**
     * 获取所有处理器名称
     */
    public java.util.Set<String> getHandlerNames() {
        return handlers.keySet();
    }
}
