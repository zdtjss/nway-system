package com.nway.system.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;

public class SpringContext implements ApplicationContextAware {

    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContext.ctx = applicationContext;
    }

    public static <T> T getBean(Class<T> type) {
        return ctx.getBean(type);
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        return ctx.getBeansOfType(type);
    }

}
