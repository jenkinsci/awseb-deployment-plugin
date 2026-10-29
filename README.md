# AWS Elastic Beanstalk Deployment Plugin

[![Build](https://github.com/Simplis-fr/awseb-deployment-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/Simplis-fr/awseb-deployment-plugin/actions/workflows/build.yml)

Jenkins plugin that packages a file or directory, uploads it to S3 and deploys it to one or
more AWS Elastic Beanstalk environments (optionally with a zero-downtime CNAME swap).

This is a modernized fork of the unmaintained
[jenkinsci/awseb-deployment-plugin](https://github.com/jenkinsci/awseb-deployment-plugin)
(last release 0.3.19, 2017). The plugin ID is unchanged, so it replaces the original plugin
and keeps existing job configurations.

## Requirements

- Jenkins 2.555.3 or newer
- Java 25 on the controller **and** on the agents running the deployment step
- An AWS credential from the [AWS Credentials](https://plugins.jenkins.io/aws-credentials/)
  plugin, or no credential to use the default AWS provider chain (instance profile, env vars…)

## Installation

Download `awseb-deployment-plugin.hpi` from the latest
[release](https://github.com/Simplis-fr/awseb-deployment-plugin/releases/latest),
then in Jenkins: *Manage Jenkins → Plugins → Advanced settings → Deploy Plugin*.

## Releases

Every merge into `master` is tagged automatically with a CalVer version `vYYYY.MM.DD.N`
(`N` = that day's merge sequence) and published as a GitHub release with the `.hpi` built
at that version.

## Usage

Freestyle jobs: add the **AWS Elastic Beanstalk** build step.

Pipeline:

```groovy
node {
    awsebDeployment credentialId: 'aws-deploy', awsRegion: 'eu-west-3',
        applicationName: 'my-app', environmentName: 'my-app-prod',
        bucketName: '', keyPrefix: 'builds',
        versionLabelFormat: '${BUILD_TAG}', versionDescriptionFormat: '${GIT_COMMIT}',
        rootObject: 'target/app.zip', includes: '', excludes: '',
        zeroDowntime: false, sleepTime: 30, checkHealth: true, maxAttempts: 30
}
```

Leaving `bucketName` empty uses the account's default Elastic Beanstalk bucket for the region.
Several environments can be deployed at once with a comma-separated `environmentName`.

## Building

```sh
mvn clean verify
```

Requires JDK 25 and Maven 3.9+.
