This plugin allows masking passwords that may appear in the console

The current version of this plugin may not be safe to use. Please review
the following warnings before use:

-   [Plain text passwords shown in global configuration form
    fields](https://jenkins.io/security/advisory/2019-08-07/#SECURITY-157)

**This plugin is up for adoption.** Want to help improve this plugin?
[Click here to learn
more](https://wiki.jenkins.io/display/JENKINS/Adopt+a+Plugin "Adopt a Plugin")!

# About this plugin

This plugin allows masking passwords that may appear in the console,
including the ones defined as build parameters. This often happens, for
example, when you use build steps which *can't* handle passwords
properly. Take a look at the following example.

## Before

Consider you're using an **Invoke Ant** build step to run an Ant target.
This target requires a password to achieve its goal. You would end up
having a job configuration like this:

![](docs/images/config-before.png)

Of course, you could have created a variable to store the password and
use this variable in the build step configuration so that it doesn't
appear as plain text. But you would still end with a console output like
this:

![](docs/images/console-before.png)

## After

When activating the **Mask passwords** option in a job, the builds'
**Password Parameters** (or any other type of build parameters selected
for masking in **Manage Hudson** \> **Configure System**) are
automatically masked from the console. Furthermore, you can also safely
define a list of static passwords to be masked (you can also define a
list of static password shared by all jobs in Jenkins' main
configuration screen). As such, the passwords don't appear anymore as
plain text in the job configuration (plus it is ciphered in the job
configuration file):

![](docs/images/config-after.png)

Once done, new builds will have the passwords masked from the console
output:

![](docs/images/console-after.png)

# User guide

First, go to Jenkins' main configuration screen (**Manage Hudson** \>
**Configure System**) and select, in the **Mask Passwords -
Configuration** section, which kind of build parameters have to be
automatically masked from the console output:

![](docs/images/global-settings.png)

Notice that, as of version 2.7, you can also define global passwords
(defined as pairs of name/password) that can be accessed across all
jobs.

Then, for a specific job, activate the **Mask passwords** option in the
**Build Environment** section to mask passwords from the console:

1.  All the password parameters defined for the job will be
    automatically hidden.
2.  For each other kind of password (that is, static ones) that may
    appear in the console output, add an entry (by clicking on the
    **Add** button) and set the **Password** field.  
    You may additionally set the **Name** field. If you do so, the
    password will then be available as a standard variable. It is then
    possible to refer to this password using this variable rather than
    keying it in a field which is not ciphered. Take a look at the
    screenshots above for an example.

