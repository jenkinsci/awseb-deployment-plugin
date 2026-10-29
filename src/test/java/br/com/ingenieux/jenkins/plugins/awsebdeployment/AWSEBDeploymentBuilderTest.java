/*
 * Copyright 2011 ingenieux Labs
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package br.com.ingenieux.jenkins.plugins.awsebdeployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AWSEBDeploymentBuilderTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) {
        this.j = j;
    }

    private static AWSEBDeploymentBuilder builder(String credentialId, String rootObject) {
        return new AWSEBDeploymentBuilder(credentialId, "eu-west-1", "my-app", "my-env",
                "my-bucket", "builds", "${BUILD_NUMBER}", "Build ${BUILD_NUMBER}",
                rootObject, "**/*", "", false, 1, true, 2);
    }

    private void addAwsCredential(String id) throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new AWSCredentialsImpl(CredentialsScope.GLOBAL, id, "AKIAEXAMPLE", "secret", "test credential"));
        SystemCredentialsProvider.getInstance().save();
    }

    @Test
    void configRoundTrip() throws Exception {
        addAwsCredential("aws");
        FreeStyleProject project = j.createFreeStyleProject();
        AWSEBDeploymentBuilder before = builder("aws", "target/app.zip");
        project.getBuildersList().add(before);

        project = j.configRoundtrip(project);

        j.assertEqualDataBoundBeans(before, project.getBuildersList().get(AWSEBDeploymentBuilder.class));
    }

    @Test
    void pipelineStepStopsOnMissingRootObjectBeforeCallingAws() throws Exception {
        addAwsCredential("aws");
        WorkflowJob job = j.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  awsebDeployment credentialId: 'aws', awsRegion: 'eu-west-1', applicationName: 'my-app',\n"
                        + "    environmentName: 'my-env', bucketName: 'my-bucket', keyPrefix: 'builds',\n"
                        + "    versionLabelFormat: 'v${BUILD_NUMBER}', versionDescriptionFormat: '',\n"
                        + "    rootObject: 'does-not-exist.zip', includes: '', excludes: '',\n"
                        + "    zeroDowntime: false, sleepTime: 1, checkHealth: false, maxAttempts: 1\n"
                        + "}", true));

        WorkflowRun run = j.buildAndAssertStatus(Result.FAILURE, job);

        j.assertLogContains("AWSEB Deployment Plugin Version", run);
        j.assertLogContains("Root Object doesn't exist", run);
    }

    @Test
    void unknownCredentialFailsTheBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(builder("missing", "app.zip"));

        j.assertLogContains("missing", j.buildAndAssertStatus(Result.FAILURE, project));
    }

    @Test
    void credentialsListRequiresPermission() throws Exception {
        addAwsCredential("aws");
        FreeStyleProject project = j.createFreeStyleProject("job");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ).everywhere().to("reader")
                .grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ, Item.CONFIGURE).everywhere().to("dev"));

        AWSEBDeploymentBuilder.DescriptorImpl descriptor =
                j.jenkins.getDescriptorByType(AWSEBDeploymentBuilder.DescriptorImpl.class);

        try (ACLContext ignored = ACL.as2(User.getById("reader", true).impersonate2())) {
            ListBoxModel items = descriptor.doFillCredentialIdItems(project, "");
            assertEquals(1, items.size(), "only the current (empty) value is exposed");

            assertThrows(AccessDeniedException3.class,
                    () -> descriptor.doValidateCredentials(project, "aws", "eu-west-1"));
            assertThrows(AccessDeniedException3.class,
                    () -> descriptor.doValidateCoordinates(project, "aws", "eu-west-1", "my-app", "my-env"));
        }

        try (ACLContext ignored = ACL.as2(User.getById("dev", true).impersonate2())) {
            ListBoxModel items = descriptor.doFillCredentialIdItems(project, "");
            assertEquals("aws", items.get(items.size() - 1).value);
        }
    }
}
