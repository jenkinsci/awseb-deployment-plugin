package br.com.ingenieux.jenkins.plugins.awsebdeployment;

import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;

import hudson.model.ModelObject;

public class AWSEBCredentials implements ModelObject {

    private final String name;
    private final String awsAccessKeyId;
    private final String awsSecretSharedKey;

    public String getName() {
        return name;
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public String getAwsSecretSharedKey() {
        return awsSecretSharedKey;
    }

    public AWSEBCredentials() {
        name = null;
        awsAccessKeyId = null;
        awsSecretSharedKey = null;
    }

    @DataBoundConstructor
    public AWSEBCredentials(String name, String awsAccessKeyId, String awsSecretSharedKey) {
        this.name = name;
        this.awsAccessKeyId = awsAccessKeyId;
        this.awsSecretSharedKey = awsSecretSharedKey;
    }

	public String getDisplayName() {
		return getName();
	}
	
	public AWSCredentialsProvider getAwsCredentials() {
		AWSCredentialsProvider credentials = new AWSCredentialsProviderChain(
				new StaticCredentialsProvider(new BasicAWSCredentials(
						getAwsAccessKeyId(),
						getAwsSecretSharedKey())));
		return credentials;
	}
}
