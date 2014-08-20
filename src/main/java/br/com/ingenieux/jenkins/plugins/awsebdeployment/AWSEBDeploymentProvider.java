package br.com.ingenieux.jenkins.plugins.awsebdeployment;

import com.amazonaws.regions.Regions;

public interface AWSEBDeploymentProvider {

	String getRootObject();

	String getKeyPrefix();

	String getBucketName();

	String getApplicationName();

	String getVersionLabelFormat();

	String getEnvironmentName();

	String getAwsAccessKeyId();

	Regions getAwsRegion();

	String getAwsSecretSharedKey();

	String getExcludes();

	String getIncludes();

}
