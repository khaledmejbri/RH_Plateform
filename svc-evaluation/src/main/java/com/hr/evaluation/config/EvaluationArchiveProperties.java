package com.hr.evaluation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "evaluation.archive")
public class EvaluationArchiveProperties {

	private String localRoot = "target/evaluations-archive";
	private String s3Bucket = "rh-evaluations";
	private String s3Prefix = "evaluations";

	public String getLocalRoot() { return localRoot; }
	public void setLocalRoot(String localRoot) { this.localRoot = localRoot; }
	public String getS3Bucket() { return s3Bucket; }
	public void setS3Bucket(String s3Bucket) { this.s3Bucket = s3Bucket; }
	public String getS3Prefix() { return s3Prefix; }
	public void setS3Prefix(String s3Prefix) { this.s3Prefix = s3Prefix; }
}
