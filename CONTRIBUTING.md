
# Contributing to the Jenkins mask-passwords-plugin

Thank you for your interest in contributing to the Jenkins "mask-password-plugin"! This guide will provide you with the necessary information to get started with contributing to the plugin.

## Getting Started 
Before you begin contributing to the plugin, you should familiarize yourself with the plugin's source code and build process. You can find the plugin's source code on GitHub at https://github.com/jenkinsci/mask-passwords-plugin. The plugin is built using Maven, so you will need to have Maven installed on your system to build the plugin.

## Newcomers
If you are a newcomer contributor and have any questions, please do not hesitate to ask in the [Newcomers Gitter channel](https://app.gitter.im/#/room/#jenkinsci_newcomer-contributors:gitter.im).

## Contributing Code
If you would like to contribute code to the plugin, please follow these steps:

Fork the plugin's repository on GitHub.
Clone your forked repository to your local machine.
Make your changes to the plugin's source code.
Test your changes to ensure that they do not introduce any new issues.
Commit your changes and push them to your forked repository.
Open a pull request from your forked repository to the main repository.
Please ensure that your pull request includes a clear description of the changes you have made and the reason for making them.

## Contributing Documentation
If you would like to contribute documentation to the plugin, please follow these steps:

Fork the plugin's repository on GitHub.
Clone your forked repository to your local machine.
Make your changes to the plugin's documentation.
Test your changes to ensure that they are accurate and informative.
Commit your changes and push them to your forked repository.
Open a pull request from your forked repository to the main repository.
Please ensure that your pull request includes a clear description of the changes you have made and the reason for making them.

## Run Locally
- Ensure Java 8 or 11 is available.
```console
  $ java -version	
  ```
    
    
    
 - Use the alternate Java 8.
 ```console
 $ export JAVA_HOME=`/usr/libexec/java_home -v 1.8`	
$ echo $JAVA_HOME	
/Library/Java/JavaVirtualMachines/jdk1.8.0_252.jdk/Contents/Home
```
- Ensure Maven > 3.6.0 is installed and included in the PATH environment variable.
```console
$ mvn --version	
```
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

Some plugins use multi module maven builds and you may need to change your hpi:run command to be run from the child directory, and build the other modules first.

``` console
$ mvn install -P quick-build
$ mvn -f plugin hpi:run
```


## Reporting Issues
 If you encounter any issues with the plugin, please report them on the plugin's issue tracker on GitHub at https://github.com/jenkinsci/mask-passwords-plugin/issues. When reporting an issue, please include as much information as possible, including a description of the issue, steps to reproduce the issue, and any relevant logs or error messages.



