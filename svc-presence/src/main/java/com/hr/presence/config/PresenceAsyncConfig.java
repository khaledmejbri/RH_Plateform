package com.hr.presence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Exécuteur Virtual Threads Java 21 dédié au traitement post-commit des notifications pointage au RO.
 */
@Configuration
public class PresenceAsyncConfig {

	@Bean(name = "pointageNotificationVirtualExecutor", destroyMethod = "close")
	public ExecutorService pointageNotificationVirtualExecutor() {
		ThreadFactory factory = Thread.ofVirtual()
				.name("presence-notif-vt-", 0)
				.factory();
		return Executors.newThreadPerTaskExecutor(factory);
	}
}
