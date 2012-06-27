gReview - The Bamboo Gerrit Integration Plugin
==============================================

This plugin polls Gerrit for changes submitted to the Gerrit review system. 
When a new change is submitted, gReview will checkout the change and verify
it builds. gReview will update the Gerrit change to reflect the correct score
for a successful or unsuccessful build.

Requirements
============

 * [Bamboo 4.0.1+](http://www.atlassian.com/software/bamboo/download)
 * [Gerrit Code Review (2.4)](http://code.google.com/p/gerrit/downloads/list)

Install
=======

This plugin can be installed via the Universal Plugin Manager in Bamboo.

Troubleshooting
===============

Make sure you have Gerrit setup correctly with SSH identity key generated and 
host used in SSH connection string added to known host. Detailed instructions 
are available in the [Gerrit documentation](https://gerrit-review.googlesource.com/Documentation/install-quick.html#usersetup).

**Note**: Some builds will still hang in native msysgit mode on Windows due to 
the following issue with the bamboo-git-plugin: [https://jira.atlassian.com/browse/BAM-11096](https://jira.atlassian.com/browse/BAM-11096).
        
You can work around this issue by manually adding your hostname, used in the 
repository configuration, to the msysgit/.ssh/known_hosts file. Example:

> 127.0.0.1 ssh-rsa XXXXB3NzaC1yc2EAAAADAQABBBBgQC699HzXHwr1H6OJeVlRo7h4r+3PY
> d0wNkqzl6EUAeU2iZjqFqQL2ZiNVqs2JrpTNadbgtXBNk9rhQIWajQZG9ZJG/OPxe+NOkbWQVev
> rcELsw5N2wxcJOWz+ey1tFv3VCtNCLUGgF7yIg0kZZVQ+HvAzLoMbiHs0haVmEjnLherSw==
 

Bug Fixes and Enhancements
==========================

1.1.2 Updates
-------------

 *  Issue #4:  Build Plan Fails When no Changes Open

1.1 Updates
-----------

 * Issue #1: Checkout Fails When Bamboo Configured with Native Git
 * Issue #2:  Add Git Submodules Capability

How to build the Bamboo Gerrit Plugin
=====================================

Impatient way:
--------------

1. `mvn package' (Apache Maven 2.2.1 was used to develop this plugin)

More patient way:
-----------------

1. Download and install the [latest JDK](http://java.sun.com)
2. Download and install the [Atlassian Plugin SDK](http://confluence.atlassian.com/display/DEVNET/Setting+up+your+Plugin+Development+Environment)
3. Run `atlas-package -DskipTests' in the directory containing Bamboo Gerrit Plugin pom.xml.
4. Grab plugin JAR file from `./target/greview-<version>.jar'

Full documentation on how to develop Atlassian Plugins is available 
at the [Atlassian Developer Site](http://confluence.atlassian.com/display/DEVNET/How+to+Build+an+Atlassian+Plugin).

How to deploy Git Plugin into existing Bamboo instance
======================================================

Full documentation on how installing Atlassian Plugins is available at:
* http://confluence.atlassian.com/display/BAMBOO/Installing+a+new+Plugin
  (please note that Bamboo Git Plugin is 'Version 1' plugin)
  
Maintainer
==========

Jason Huntley
-------------

* jhuntley@houghtonassociates.com
* onepremise@gmail.com