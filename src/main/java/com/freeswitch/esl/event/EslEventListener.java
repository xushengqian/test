package com.freeswitch.esl.event;

/**
 * ESL 事件监听器接口
 * 
 * 实现此接口以接收 FreeSWITCH 事件通知
 */
public interface EslEventListener {

    /**
     * 接收事件回调
     *
     * @param event ESL 事件对象
     */
    void onEvent(EslEvent event);

    /**
     * 获取监听的事件类型
     * 返回 null 或空数组表示监听所有事件
     *
     * @return 事件类型数组
     */
    default String[] getEventTypes() {
        return null;
    }

    /**
     * 事件过滤器
     * 返回 true 表示处理该事件，false 表示忽略
     *
     * @param event ESL 事件对象
     * @return 是否处理该事件
     */
    default boolean filter(EslEvent event) {
        return true;
    }

    /**
     * 获取监听器优先级
     * 数值越小优先级越高
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}
