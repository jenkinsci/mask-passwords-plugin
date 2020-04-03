# Version history (archive)

## New releases

See [GitHub Releases](https://github.com/jenkinsci/mask-passwords-plugin/releases)

## Version 2.12.0 

Release Date: (Jun 01, 2018)

-   [![(plus)](images/add.svg) PR
    \#18](https://github.com/jenkinsci/mask-passwords-plugin/pull/18) -
    Mask Passwords Console Log filter can be now applied to all Run
    types  
    -   It should allow filtering Pipeline jobs
        once [JENKINS-45693](http://issues.jenkins-ci.org/browse/JENKINS-45693)
        is implemented
-   ![(info)](images/information.svg) Update
    minimal core requirement to 1.625.3

## Version 2.11.0

Release Date: (Mar 13, 2018)

-   ![(info)](images/information.svg) Update
    minimal core requirement to 1.625.3
-   ![(info)](images/information.svg) Developer:
    Update Plugin POm to the latest version

## Version 2.10.1

Release Date: (Apr 11, 2017)

-   ![(error)](images/error.svg) Prevent
    NullPointerException when loading configurations from the disk
    ([JENKINS-43504](https://issues.jenkins-ci.org/browse/JENKINS-43504))

## Version 2.10

Release Date: (Apr 08, 2017)

-   ![(plus)](images/add.svg) Rework
    the Parameter Definition processing engine, improve the reliability
    of Sensitive parameter discovery
-   ![(error)](images/error.svg) Fix
    a number of issues with parameter masking reported to the plugin.
    Full list will be published later

## Version 2.9

Release Date: (30/11/2016)

-   ![(plus)](images/add.svg)
    Add option to mask output strings by a regular expression, also with
    a global setting ([PR
    \#6](https://github.com/jenkinsci/mask-passwords-plugin/pull/6))
-   ![(error)](images/error.svg)
    Properly invoke flush/close operations for the logger in
    MaskPasswordOutputStream ([PR
    \#8](https://github.com/jenkinsci/mask-passwords-plugin/pull/8))
-   ![(error)](images/error.svg)
    Fix issues reported by FindBugs
-   ![(info)](images/information.svg)
    Update to the new Parent POM

## Version 2.8

Release Date: (18/10/2015)

-   ![(plus)](images/add.svg)
    Implement SimpleBuildWrapper in order to support Workflow project
    type
    ([JENKINS-27392](https://issues.jenkins-ci.org/browse/JENKINS-27392))

## Version 2.7.4

Release Date: (29/07/2015)

-   ![(error)](images/error.svg)
    Password parameters were insensitive
-   ![(error)](images/error.svg)
    "Mask passwords" build wrapper was generating insensitive
    environment variables

Fixed issues (to be investigated and updated):

-   Masking of global password parameters in EnvInject
    ([JENKINS-25821](https://issues.jenkins-ci.org/browse/JENKINS-25821))
-   Masked Passwords are shown as input parameters in Build pipeline
    plugin
    ([JENKINS-16516](https://issues.jenkins-ci.org/browse/JENKINS-16516))

## Version 2.7.3

Release Date: (29/04/2015)

-   Fixed
    [JENKINS-12161](https://issues.jenkins-ci.org/browse/JENKINS-12161):
    EnvInject vars could have been not masked because of plugins loading order
-   Fixed
    [JENKINS-14687](https://issues.jenkins-ci.org/browse/JENKINS-14687):
    password exposed unencrypted in HTML source

## Version 2.7.2

Release Date: (12/07/2011)

-   Fixed
    [JENKINS-11934](https://issues.jenkins-ci.org/browse/JENKINS-11934):
    Once a job config was submitted, new/updated global passwords were
    not masked
-   Implemented
    [JENKINS-11924](https://issues.jenkins-ci.org/browse/JENKINS-11924):
    Improved global passwords-related labels

## Version 2.7.1

Release Date: (10/27/2011)

-   Fixed
    [JENKINS-11514](https://issues.jenkins-ci.org/browse/JENKINS-11514):
    When migrating from an older version of the plugin,
    `NullPointerException`s were preventing the jobs using Mask
    Passwords to load
-   Fixed
    [JENKINS-11515](https://issues.jenkins-ci.org/browse/JENKINS-11515):
    Mask Passwords global config was not actually saved when no global
    passwords were defined

## Version 2.7

Release Date: (10/20/2011)

-   Implemented
    [JENKINS-11399](https://issues.jenkins-ci.org/browse/JENKINS-11399):
    It is now possible to define name/password pairs in Jenkins' main
    configuration screen (**Manage Hudson** \> **Configure System**)

## Version 2.6.1

Release Date: (05/26/2011)

-   Fixed a bug which was emptying the console output if there was no
    password to actually mask

## Version 2.6

Release Date: (04/29/2011)

-   Added a new type of build parameter: **Non-Stored Password
    Parameter**
-   Blank passwords are no more masked, avoiding overcrowding the
    console with stars

## Version 2.5

Release Date: (03/11/2011)

-   New configuration screen (in **Manage Hudson** \> **Configure
    System**) allowing to select which build parameters have to be
    masked (**Password Parameter** are selected by default)
-   Fixed a bug which was preventing to mask passwords containing
    regular expressions' meta-characters or escape sequences

## Version 2.0

Release Date: (02/23/2011)

-   Builds' **Password Parameter**s are now automatically masked.

## Version 1.0

Release Date: (09/01/2010)

-   Initial release
