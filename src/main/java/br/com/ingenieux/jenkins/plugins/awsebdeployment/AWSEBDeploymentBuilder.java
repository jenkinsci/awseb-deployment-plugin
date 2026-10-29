/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package br.com.ingenieux.jenkins.plugins.awsebdeployment;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;

/**
 * AWS Elastic Beanstalk Deployment
 */
@SuppressWarnings({"unchecked", "deprecation"})
public class AWSEBDeploymentBuilder extends Builder implements SimpleBuildStep {
    /**
     * Credentials name
     */
    @Getter
    private String credentialId;

    /**
     * AWS Region
     */
    @Getter
    private String awsRegion;

    /**
     * Application Name
     */
    @Getter
    private String applicationName;

    /**
     * Environment Name
     */
    @Getter
    private String environmentName;

    /**
     * Bucket Name
     */
    @Getter
    private String bucketName;

    /**
     * Key Format
     */
    @Getter
    private String keyPrefix;

    /**
     * Version Label
     */
    @Getter
    private String versionLabelFormat;

    /**
     * Version Description
     */
    @Getter
    private String versionDescriptionFormat;

    /**
     * Root Object
     */
    @Getter
    private String rootObject;

    /**
     * Includes
     */
    @Getter
    private String includes;

    /**
     * Excludes
     */
    @Getter
    private String excludes;

    /**
     * Zero Downtime
     */
    @Getter
    private boolean zeroDowntime;

    /**
     * Deploy Sleep Time
     */
    @Getter
    private Integer sleepTime;


    /**
     * Check Health
     */
    @Getter
    private boolean checkHealth;

    /**
     * Max Number Of Attempts
     */
    @Getter
    private Integer maxAttempts;


    @DataBoundConstructor
    public AWSEBDeploymentBuilder(String credentialId, String awsRegion, String applicationName,
                                  String environmentName, String bucketName, String keyPrefix,
                                  String versionLabelFormat, String versionDescriptionFormat,
                                  String rootObject, String includes, String excludes,
                                  boolean zeroDowntime, Integer sleepTime, boolean checkHealth,
                                  Integer maxAttempts) {
        this.credentialId = credentialId;
        this.awsRegion = awsRegion;
        this.applicationName = applicationName;
        this.environmentName = environmentName;
        this.bucketName = bucketName;
        this.keyPrefix = keyPrefix;
        this.versionLabelFormat = versionLabelFormat;
        this.versionDescriptionFormat = versionDescriptionFormat;
        this.rootObject = rootObject;
        this.includes = includes;
        this.excludes = excludes;
        this.zeroDowntime = zeroDowntime;
        this.sleepTime = sleepTime;
        this.checkHealth = checkHealth;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath ws, Launcher launcher,
                           TaskListener listener) throws IOException {
        try {
            new DeployerRunner(build, ws, launcher, listener, this).perform();

            return;
        } catch (Exception exc) {
            throw new IOException("Deployment Failure", exc);
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @SuppressWarnings("rawtypes")
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public AWSEBDeploymentConfig asConfig() {
        return new AWSEBDeploymentConfig(
                credentialId,
                awsRegion,
                applicationName,
                environmentName,
                bucketName,
                keyPrefix,
                versionLabelFormat,
                versionDescriptionFormat,
                rootObject,
                includes,
                excludes,
                zeroDowntime,
                sleepTime,
                checkHealth,
                maxAttempts,
                null);
    }

    @Extension
    @Symbol("awsebDeployment")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {

            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "AWS Elastic Beanstalk";
        }

        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item owner,
                                                    @QueryParameter String credentialId) {
            StandardListBoxModel result = new StandardListBoxModel();

            if (owner == null ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                              : !owner.hasPermission(Item.EXTENDED_READ)) {
                return result.includeCurrentValue(credentialId);
            }

            return result
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM2, owner, AmazonWebServicesCredentials.class)
                    .includeCurrentValue(credentialId);
        }

        /**
         * Validation buttons call AWS with the configured credentials: only allow job configurers
         */
        private static void checkConfigurePermission(Item item) {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                item.checkPermission(Item.CONFIGURE);
            }
        }

        public FormValidation doCheckAwsRegion(@QueryParameter String value) {
            value = Util.fixNull(value);
            if (value.contains("$")) {
                return FormValidation.warning("Validation skipped due to parameter usage ('$')");
            }

            if (!value.matches("^\\p{Alpha}{2}-(?:gov-)?\\p{Alpha}{4,}-\\d$")) {
                return FormValidation.error("Doesn't look like a region, like {place}-{cardinal}-{number}");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckApplicationName(@QueryParameter String value) {
            value = Util.fixNull(value);
            if (value.contains("$")) {
                return FormValidation.warning("Validation skipped due to parameter usage ('$')");
            }

            int valueLen = value.length();
            if (valueLen == 0 || valueLen > 100) {
                return FormValidation.error("Application Names must have between 1-100 characters");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckEnvironmentName(@QueryParameter String value) {
            value = Util.fixNull(value);
            if (value.contains("$")) {
                return FormValidation.warning("Validation skipped due to parameter usage ('$')");
            }

            if (value.contains(",")) {
                if (!value.matches("^[\\p{Alpha}[\\p{Alnum}\\-]{0,39}]+(,\\p{Space}*[\\p{Alpha}[\\p{Alnum}\\-]{0,39}]+)*$") || value.endsWith("-")) {
                    return FormValidation.error(
                            "Doesn't look like properly comma separated environment names. Each must be from 4 to 40 characters in length. The name can contain only letters, numbers, and hyphens. It cannot start or end with a hyphen");
                }
            } else {
                if (!value.matches("^\\p{Alpha}[\\p{Alnum}\\-]{0,39}$") || value.endsWith("-")) {
                    return FormValidation.error(
                            "Doesn't look like an environment name. Must be from 4 to 40 characters in length. The name can contain only letters, numbers, and hyphens. It cannot start or end with a hyphen");
                }
            }

            return FormValidation.ok();
        }

        @POST
        public FormValidation doValidateCredentials(
                @AncestorInPath Item item,
                @QueryParameter("credentialId") final String credentialId,
                @QueryParameter final String awsRegion) {
            checkConfigurePermission(item);

            for (String value : Arrays.asList(credentialId, awsRegion)) {
                if (Util.fixNull(value).contains("$")) {
                    return FormValidation.warning("Validation skipped due to parameter usage ('$')");
                }
            }

            StringWriter stringWriter = new StringWriter();
            PrintWriter w = new PrintWriter(stringWriter, true);

            try {
                w.printf("<ul>%n");

                w.printf("<li>Building Client (credentialId: '%s', region: '%s')</li>%n",
                        Util.escape(credentialId), Util.escape(awsRegion));

                AWSClientFactory factory = AWSClientFactory.getClientFactory(credentialId, awsRegion, item);

                AmazonS3 amazonS3 = factory.getService(AmazonS3Client.class);
                String s3Endpoint = factory.getEndpointFor((AmazonS3Client) amazonS3);

                w.printf("<li>Testing Amazon S3 Service (endpoint: %s)</li>%n", Util.escape(s3Endpoint));

                w.printf("<li>Buckets Found: %d</li>%n", amazonS3.listBuckets().size());

                AWSElasticBeanstalk
                        awsElasticBeanstalk =
                        factory.getService(AWSElasticBeanstalkClient.class);

                String
                        awsEBEndpoint =
                        factory.getEndpointFor((AWSElasticBeanstalkClient) awsElasticBeanstalk);

                w.printf("<li>Testing AWS Elastic Beanstalk Service (endpoint: %s)</li>%n",
                        Util.escape(awsEBEndpoint));

                List<String> applicationList = awsElasticBeanstalk.describeApplications().getApplications()
                        .stream()
                        .map(ApplicationDescription::getApplicationName)
                        .collect(Collectors.toList());

                w.printf("<li>Applications Found: %d (%s)</li>%n", applicationList.size(),
                        Util.escape(StringUtils.join(applicationList, ", ")));

                w.printf("</ul>%n");

                return FormValidation.okWithMarkup(stringWriter.toString());
            } catch (Exception exc) {
                return FormValidation.error(exc, "Failure");
            }
        }

        @POST
        public FormValidation doValidateCoordinates(@AncestorInPath Item item,
                                                    @QueryParameter("credentialId") String credentialId,
                                                    @QueryParameter("awsRegion") String awsRegion,
                                                    @QueryParameter("applicationName") String applicationName,
                                                    @QueryParameter("environmentName") String environmentName)
                throws Exception {
            checkConfigurePermission(item);

            for (String value : Arrays
                    .asList(credentialId, awsRegion, applicationName, environmentName)) {
                if (Util.fixNull(value).contains("$")) {
                    return FormValidation.warning("Validation skipped due to parameter usage ('$')");
                }
            }

            List<String> environmentNames = Arrays.asList(Util.fixNull(environmentName).replaceAll("\\s", "").split(","));

            AWSClientFactory clientFactory = AWSClientFactory.getClientFactory(credentialId, awsRegion, item);

            AWSElasticBeanstalk
                    awsElasticBeanstalk =
                    clientFactory.getService(AWSElasticBeanstalkClient.class);

            DescribeEnvironmentsResult
                    describeEnvironmentsResult =
                    awsElasticBeanstalk.describeEnvironments(
                            new DescribeEnvironmentsRequest().withApplicationName(applicationName)
                                    .withIncludeDeleted(false)
                                    .withEnvironmentNames(environmentNames));

            // Validate multiple environments & display IDs
            if (describeEnvironmentsResult.getEnvironments().size() > 1) {
                List<String> environmentIds = new ArrayList<>();
                Integer size = describeEnvironmentsResult.getEnvironments().size();
                for (Integer i = 0; i < size; i++) {
                    environmentIds.add(describeEnvironmentsResult.getEnvironments().get(i).getEnvironmentId());
                }

                return FormValidation.ok("Multiple environments found (environmentIDs: %s)", StringUtils.join(environmentIds, ", "));
            }

            // Validate single environment & display ID
            if (describeEnvironmentsResult.getEnvironments().size() == 1) {
                String
                        environmentId =
                        describeEnvironmentsResult.getEnvironments().get(0).getEnvironmentId();
                return FormValidation.ok("Environment found (environmentId: %s)", environmentId);
            }

            return FormValidation.error("Environment not found");
        }

        public FormValidation doValidateUpload(@QueryParameter("applicationName") String applicationName,
                                               @QueryParameter("bucketName") String bucketName,
                                               @QueryParameter("keyPrefix") String keyPrefix,
                                               @QueryParameter("versionLabelFormat") String versionLabelFormat) {

            String objectKey = Utils.formatPath("%s/%s-%s.zip",
                                                defaultIfBlank(keyPrefix, "<ERROR: MISSING KEY PREFIX>"),
                                                defaultIfBlank(applicationName, "<ERROR: MISSING APPLICATION NAME>"),
                                                defaultIfBlank(versionLabelFormat, "<ERROR: MISSING VERSION LABEL FORMAT>"));

            String targetPath = String.format("s3://%s/%s",
                                              defaultIfBlank(bucketName, "[default account bucket for region]"),
                                              objectKey);

            final String resultingMessage = format("Your object will be uploaded to S3 as: <code>%s</code> (<i>note replacements will apply</i>)", Util.escape(targetPath));

            return FormValidation.okWithMarkup(resultingMessage);
        }
    }

}
