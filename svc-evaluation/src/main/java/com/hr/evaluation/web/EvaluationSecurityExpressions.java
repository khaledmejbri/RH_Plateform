package com.hr.evaluation.web;

public final class EvaluationSecurityExpressions {

	public static final String BACKOFFICE_RH = "hasAnyRole('RH','DIRECTION','ADMIN')";

	private EvaluationSecurityExpressions() {
	}
}
