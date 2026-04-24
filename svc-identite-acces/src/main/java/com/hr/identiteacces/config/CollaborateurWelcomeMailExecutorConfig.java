package com.hr.identiteacces.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class CollaborateurWelcomeMailExecutorConfig {

	@Bean(name = "collaborateurWelcomeMailExecutor")
	public Executor collaborateurWelcomeMailExecutor() {
		ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
		ex.setCorePoolSize(1);
		ex.setMaxPoolSize(4);
		ex.setQueueCapacity(200);
		ex.setThreadNamePrefix("mail-collab-welcome-");
		ex.setDaemon(true);
		ex.initialize();
		return ex;
	}
}
