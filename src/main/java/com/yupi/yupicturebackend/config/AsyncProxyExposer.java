package com.yupi.yupicturebackend.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * 异步代理暴露配置
 * 使 @Async 注解的方法能够通过 AopContext.currentProxy() 获取代理对象
 */
@Component
public class AsyncProxyExposer implements BeanFactoryPostProcessor {
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 获取异步注解处理器的Bean定义
        BeanDefinition bd = beanFactory.getBeanDefinition(
            "org.springframework.scheduling.annotation.internalAsyncAnnotationProcessor");
        // 设置exposeProxy为true，允许通过AopContext访问代理对象
        bd.getPropertyValues().add("exposeProxy", true);
    }
}
