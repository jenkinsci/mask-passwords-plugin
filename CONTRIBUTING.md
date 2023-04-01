
# Contributing to the Jenkins mask-passwords-plugin

Thank you for your interest in contributing to the Jenkins "mask-passworda-plugin"! This guide will provide you with the necessary information to get started with contributing to the plugin.

## Getting Started 
Before you begin contributing to the plugin, you should familiarize yourself with the plugin's source code and build process. You can find the plugin's source code on GitHub at https://github.com/jenkinsci/mask-passwords-plugin. The plugin is built using Maven, so you will need to have Maven installed on your system to build the plugin.

## Newcomers
If you are a newcomer contributor and have any questions, please do not hesitate to ask in the [Newcomers Gitter channel](https://app.gitter.im/#/room/#jenkinsci_newcomer-contributors:gitter.im).

## Contributing Code
If you would like to contribute code to the plugin, please follow these steps:

* Fork the plugin's repository on GitHub.
* Clone your forked repository to your local machine.
* Make your changes to the plugin's source code.
* Test your changes to ensure that they do not introduce any new issues.
* Commit your changes and push them to your forked repository.
* Open a pull request from your forked repository to the main repository. Please ensure that your pull request includes a clear description of the changes you have made and the reason for making them.


## Run Locally
* Ensure Java 11 or 17 is available.
```console
  $ java -version	
  openjdk version "11.0.18" 2023-01-17
  OpenJDK Runtime Environment Temurin-11.0.18+10 (build 11.0.18+10)
  OpenJDK 64-Bit Server VM Temurin-11.0.18+10 (build 11.0.18+10, mixed mode)
    
    
    
- Ensure Maven > 3.8.4 or newer is installed and included in the PATH environment variable.
```console
$ mvn --version
Apache Maven 3.9.1 (2e178502fcdbffc201671fb2537d0cb4b4cc58f8)
Maven home: /opt/apache-maven-3.9.1
Java version: 11.0.18, vendor: Eclipse Adoptium, runtime: /opt/jdk-11
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "4.18.0-425.13.1.el8_7.x86_64", arch: "amd64", family: "unix"
## CLI
- Use the following command
```console
$ mvn hpi:run	
```
```console
...	
INFO: Jenkins is fully up and running
```
- Open http://localhost:8080/jenkins/ to test the plugin locally.

## Reporting Issues
 If you encounter any issues with the plugin, please report them on the [Jira issue tracker](https://www.jenkins.io/participate/report-issue/redirect/#15761) . When reporting an issue, please include as much information as possible, including a description of the issue, steps to reproduce the issue, and any relevant logs or error messages.  ["How to report an issue"](https://www.jenkins.io/participate/report-issue/) provides more details on the information needed for a better bug report.



