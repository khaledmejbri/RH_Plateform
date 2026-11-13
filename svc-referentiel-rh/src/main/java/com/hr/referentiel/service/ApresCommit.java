package com.hr.referentiel.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Exécute une action (notification, événement) après le commit de la transaction en cours, ou tout de suite sans transaction. */
public final class ApresCommit {

	private ApresCommit() {
	}

	public static void executer(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}
}
