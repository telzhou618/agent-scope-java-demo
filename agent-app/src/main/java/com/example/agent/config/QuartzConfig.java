package com.example.agent.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz：让 Job 实现类可以使用 @Autowired 注入 Spring Bean
 */
@Configuration
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer jobFactoryCustomizer(ApplicationContext applicationContext) {
        return factoryBean -> factoryBean.setJobFactory(new AutowiringSpringBeanJobFactory(applicationContext));
    }

    private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {

        private final AutowireCapableBeanFactory beanFactory;

        AutowiringSpringBeanJobFactory(ApplicationContext applicationContext) {
            this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        }
    }
}
