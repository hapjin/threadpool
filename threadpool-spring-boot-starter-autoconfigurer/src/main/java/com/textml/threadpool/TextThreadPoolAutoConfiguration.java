package com.textml.threadpool;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author psj
 * @date 2019/11/25
 */
@Configuration
@ConditionalOnClass(TextThreadPool.class)
@EnableConfigurationProperties({TextThreadPoolConfig.class})
public class TextThreadPoolAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TextThreadPool threadPool() {
        //todo 使用 TextThreadPoolConfig 构造线程池对象
        return new TextThreadPool();
    }

    @Bean
    @ConditionalOnMissingBean
    public TextThreadPoolConfig threadPoolConfig() {
        //根据线程池配置解析成 TextThreadPool 构造器 所需的对象
        return new TextThreadPoolConfig();
    }
}
