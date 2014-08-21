package br.com.ingenieux.jenkins.plugins.awsebdeployment;

import com.amazonaws.regions.Regions;

public interface AWSEBDeploymentProvider {

	public String getRootObject();

	public String getKeyPrefix();

	public String getBucketName();

	public String getApplicationName();

	public String getVersionLabelFormat();

	public String getEnvironmentName();

	public AWSEBCredentials getCredentials();

	public Regions getAwsRegion();

	public String getExcludes();

	public String getIncludes();

}
