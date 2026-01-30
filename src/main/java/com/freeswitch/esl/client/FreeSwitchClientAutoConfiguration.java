package com.freeswitch.esl.client;

import com.freeswitch.esl.config.FreeSwitchConfig;
import com.freeswitch.esl.event.EslEventHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FreeSWITCH 客户端自动配置类
 */
@Configuration
@EnableConfigurationProperties(FreeSwitchConfig.class)
public class FreeSwitchClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EslEventHandler eslEventHandler() {
        return new EslEventHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public FreeSwitchClient freeSwitchClient(FreeSwitchConfig config, EslEventHandler eventHandler) {
        return new FreeSwitchClient(config, eventHandler);
    }
}
