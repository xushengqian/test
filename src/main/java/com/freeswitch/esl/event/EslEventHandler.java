package com.freeswitch.esl.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ESL 事件处理器
 * 
 * 负责管理事件监听器并分发事件
 */
@Component
public class EslEventHandler {

    private static final Logger log = LoggerFactory.getLogger(EslEventHandler.class);

    /**
     * 全局事件监听器列表
     */
    private final List<EslEventListener> globalListeners = new CopyOnWriteArrayList<>();

    /**
     * 按事件类型分组的监听器映射
     */
    private final Map<String, List<EslEventListener>> eventListeners = new ConcurrentHashMap<>();

    /**
     * 按 UUID 分组的监听器映射
     */
    private final Map<String, List<EslEventListener>> uuidListeners = new ConcurrentHashMap<>();

    /**
     * 事件处理线程池
     */
    private final ExecutorService eventExecutor;

    /**
     * 是否异步处理事件
     */
    private boolean asyncProcessing = true;

    public EslEventHandler() {
        this.eventExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                r -> {
                    Thread t = new Thread(r, "esl-event-handler");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /**
     * 注册全局事件监听器
     *
     * @param listener 事件监听器
     */
    public void addGlobalListener(EslEventListener listener) {
        globalListeners.add(listener);
        globalListeners.sort(Comparator.comparingInt(EslEventListener::getPriority));
        log.info("Added global event listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * 移除全局事件监听器
     *
     * @param listener 事件监听器
     */
    public void removeGlobalListener(EslEventListener listener) {
        globalListeners.remove(listener);
        log.info("Removed global event listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * 注册特定事件类型的监听器
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     */
    public void addEventListener(String eventType, EslEventListener listener) {
        eventListeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(listener);
        log.info("Added event listener for type [{}]: {}", eventType, listener.getClass().getSimpleName());
    }

    /**
     * 移除特定事件类型的监听器
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     */
    public void removeEventListener(String eventType, EslEventListener listener) {
        List<EslEventListener> listeners = eventListeners.get(eventType);
        if (listeners != null) {
            listeners.remove(listener);
            log.info("Removed event listener for type [{}]: {}", eventType, listener.getClass().getSimpleName());
        }
    }

    /**
     * 注册特定 UUID 的监听器
     *
     * @param uuid     Channel UUID
     * @param listener 事件监听器
     */
    public void addUuidListener(String uuid, EslEventListener listener) {
        uuidListeners.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(listener);
        log.debug("Added UUID listener for [{}]: {}", uuid, listener.getClass().getSimpleName());
    }

    /**
     * 移除特定 UUID 的监听器
     *
     * @param uuid     Channel UUID
     * @param listener 事件监听器
     */
    public void removeUuidListener(String uuid, EslEventListener listener) {
        List<EslEventListener> listeners = uuidListeners.get(uuid);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * 移除指定 UUID 的所有监听器
     *
     * @param uuid Channel UUID
     */
    public void removeAllUuidListeners(String uuid) {
        uuidListeners.remove(uuid);
        log.debug("Removed all UUID listeners for [{}]", uuid);
    }

    /**
     * 处理 ESL 事件
     *
     * @param event ESL 事件
     */
    public void handleEvent(EslEvent event) {
        if (event == null) {
            return;
        }

        if (asyncProcessing) {
            eventExecutor.submit(() -> doHandleEvent(event));
        } else {
            doHandleEvent(event);
        }
    }

    /**
     * 实际处理事件的方法
     *
     * @param event ESL 事件
     */
    private void doHandleEvent(EslEvent event) {
        List<EslEventListener> listeners = collectListeners(event);

        for (EslEventListener listener : listeners) {
            try {
                if (listener.filter(event)) {
                    listener.onEvent(event);
                }
            } catch (Exception e) {
                log.error("Error handling event [{}] in listener [{}]: {}",
                        event.getEventName(),
                        listener.getClass().getSimpleName(),
                        e.getMessage(), e);
            }
        }
    }

    /**
     * 收集匹配的监听器
     *
     * @param event ESL 事件
     * @return 监听器列表
     */
    private List<EslEventListener> collectListeners(EslEvent event) {
        List<EslEventListener> result = new ArrayList<>();

        // 添加全局监听器
        result.addAll(globalListeners);

        // 添加事件类型监听器
        String eventName = event.getEventName();
        if (eventName != null) {
            List<EslEventListener> typeListeners = eventListeners.get(eventName);
            if (typeListeners != null) {
                result.addAll(typeListeners);
            }
        }

        // 添加 UUID 监听器
        String uuid = event.getUniqueId();
        if (uuid != null) {
            List<EslEventListener> uuidListenerList = uuidListeners.get(uuid);
            if (uuidListenerList != null) {
                result.addAll(uuidListenerList);
            }
        }

        // 按优先级排序
        result.sort(Comparator.comparingInt(EslEventListener::getPriority));

        return result;
    }

    /**
     * 设置是否异步处理事件
     *
     * @param asyncProcessing 是否异步
     */
    public void setAsyncProcessing(boolean asyncProcessing) {
        this.asyncProcessing = asyncProcessing;
    }

    /**
     * 关闭事件处理器
     */
    public void shutdown() {
        eventExecutor.shutdown();
        globalListeners.clear();
        eventListeners.clear();
        uuidListeners.clear();
        log.info("ESL event handler shutdown completed");
    }
}
