package br.com.ingenieux.jenkins.plugins.awsebdeployment;

/*
 * #%L
 * AWS Elastic Beanstalk Deployment Plugin
 * %%
 * Copyright (C) 2013 ingenieux Labs
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.CopyOnWriteList;
import hudson.util.ListBoxModel;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.amazonaws.regions.Regions;

/**
 * AWS Elastic Beanstalk Deployment
 */
public class AWSEBDeploymentPublisher extends Recorder implements AWSEBDeploymentProvider{
	@DataBoundConstructor
	public AWSEBDeploymentPublisher(Regions awsRegion,
			String applicationName, String environmentName, String bucketName,
			String keyPrefix, String versionLabelFormat, String rootObject,
			String includes, String excludes, String credentials) {
		super();
		this.awsRegion = awsRegion;
		this.applicationName = applicationName;
		this.environmentName = environmentName;
		this.bucketName = bucketName;
		this.keyPrefix = keyPrefix;
		this.versionLabelFormat = versionLabelFormat;
		this.rootObject = rootObject;
		this.includes = includes;
		this.excludes = excludes;
		this.credentials = DESC.getCredentialsByName(credentials);
	}
	
	private final static DescriptorImpl DESC = new DescriptorImpl();

	/**
	 * AWS Region
	 */
	private final Regions awsRegion;

	public Regions getAwsRegion() {
		return awsRegion;
	}

	
	/**
	 * Application Name
	 */
	private final String applicationName;

	public String getApplicationName() {
		return applicationName;
	}


	/**
	 * Environment Name
	 */
	private final String environmentName;

	public String getEnvironmentName() {
		return environmentName;
	}


	/**
	 * Bucket Name
	 */
	private final String bucketName;

	public String getBucketName() {
		return bucketName;
	}


	/**
	 * Key Format
	 */
	private final String keyPrefix;

	public String getKeyPrefix() {
		return keyPrefix;
	}


	private final String versionLabelFormat;

	public String getVersionLabelFormat() {
		return versionLabelFormat;
	}


	private final String rootObject;

	public String getRootObject() {
		return rootObject;
	}


	private final String includes;

	public String getIncludes() {
		return includes;
	}


	private String excludes;

	public String getExcludes() {
		return excludes;
	}

	
	/**
	 * Credentials Name from the global config
	 */
	private final AWSEBCredentials credentials;
	
	public AWSEBCredentials getCredentials() {
		return credentials;
	}
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		try {
			Deployer deployer = new Deployer(this, build, launcher, listener);

			deployer.perform();

			return true;
		} catch (Exception exc) {
			throw new RuntimeException(exc);
		}
	}
	
	

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
	@Override
    public DescriptorImpl getDescriptor() {
        return DESC;
    }
	
	public static DescriptorImpl getDesc() {
		return DESC;
	}

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Deploy into AWS Elastic Beanstalk";
        }
        
        private final CopyOnWriteList<AWSEBCredentials> credentials = new CopyOnWriteList<AWSEBCredentials>();

        public AWSEBCredentials[] getCredentials() {
            return credentials.toArray(new AWSEBCredentials[0]);
        }
        
        public DescriptorImpl() {
            load();
        }


        @Override
        public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
            credentials.replaceBy(req.bindParametersToList(AWSEBCredentials.class, "credential."));
            save();
            return true;
        }
        
        public AWSEBCredentials getCredentialsByName(String credentialsName) {
        	AWSEBCredentials[] credentials = getCredentials();

            if (credentialsName == null && credentials.length > 0)
                // default
                return credentials[0];

            for (AWSEBCredentials credential : credentials) {
                if (credential.getName().equals(credentialsName))
                    return credential;
            }
            
            return null;
        }
        

        public ListBoxModel doFillCredentialsItems() {
        	ListBoxModel items = new ListBoxModel();
        	for (AWSEBCredentials credentials : AWSEBDeploymentPublisher.getDesc().getCredentials()) {
        		items.add(credentials, credentials.getName());
        	}

            return items;
        }

    }
}
