gReview - The Bamboo Gerrit Integration Plugin
==============================================

This plugin polls Gerrit for changes submitted to the Gerrit review system. 
When a new change is submitted, gReview will checkout the change and verify
it builds. gReview will update the Gerrit change to reflect the correct score
for a successful or unsuccessful build.

Features
========

 * Gerrit Code Review Integration.
 * GitWeb Integration.
 * Builds and Verifies Changes Submitted to Gerrit.
 * Changes are built independently in the order they are submitted.
 * Change dependencies are resolved naturally through Gerrit PatchSets.
 * Change comments automatically updated with build results.
 * Gerrit Change Display Tab available in Build Summary.
 * [All the Features Provided By Bamboo](http://www.atlassian.com/software/bamboo/features/)

Requirements
============

 * [Bamboo 4.2+](http://www.atlassian.com/software/bamboo/download)
 * [Native Git 1.8.0+](http://git-scm.com/downloads)
 * [Gerrit Code Review (2.4+)](http://code.google.com/p/gerrit/downloads/list)

Install
=======

Bamboo
------

The Bamboo install guide can be found [here](https://confluence.atlassian.com/display/BAMBOO/Bamboo+installation+guide).

Native Git
----------

Native Git can be downloaded from the git website [here](http://git-scm.com/downloads).

Gerrit
------

A quick install is available [here](https://gerrit-review.googlesource.com/Documentation/install.html).

A Tomcat install guide is also [in the works](https://gerrit-review.googlesource.com/#/c/35010/).

gReview
-------

This plugin can be installed via the Universal Plugin Manager in Bamboo.

Usage
=======

Enable Native Git
-----------------

 * Login as admin.
 * Goto the Administration console by selecting "Administration" link at top.
 * Click on "Executables" link on the left under "Build Resources".
 * Click on "add an executable as a server capability".
 * Select "Git" as "Capability Type".
 * Provide path to "git.exe".
 * Click on "Add".

Setup Gerrit
------------

 * Login as admin.
 * Select 'Configure Plan' under 'Actions' in the dropdown menu on the right.
 * Select the 'Source Repositoryies' Tab.
 * [Select 'Add Repository'.](https://plus.google.com/u/0/photos/111679711947778743513/albums/5751797380554349169/5754399119605434834)

Enable Gerrit Verification
--------------------------

 * Login as admin.
 * Select 'Plan Configuration' under 'Actions' in the dropdown menu on the right.
 * Select the 'Stages' Tab.
 * Select the job you're interested in. Usually there is only one, 'Default Job'.
 * Under the job configuration, select the "Miscellaneous" tab.
 * Check ['Run Gerrit Verification after main build.'](https://lh4.googleusercontent.com/-oawKtjfHbbo/T9J9PgwUF9I/AAAAAAAAANU/C0kOBQYFkC8/s1303/gerritConfig2.JPG)

Viewing Gerrit Change Information
---------------------------------

Gerrit change information is available under the Build Summary [Gerrit Tab](https://lh6.googleusercontent.com/-k0Oy_CnsufA/T9J9PglbtTI/AAAAAAAAANM/vPKpt8nsgUY/s785/gerritTab.JPG)

Adding Gitweb
-------------

 * Login as admin.
 * Select 'Configure Plan' under 'Actions' in the dropdown menu on the right.
 * [Select the 'Source Repositoryies' Tab.](https://lh3.googleusercontent.com/-EbLti6gv3xo/UCKuvN1j7vI/AAAAAAAAAPQ/OaZ8MsXwGjs/s939/source-repositories.JPG)
 * Select an existing Gerrit Repository or add one.
 * Drop down 'Advanced Options' and select the GitWeb repository.
 * [Provide your details and save](https://lh5.googleusercontent.com/-wd7iOs6EmhQ/UCKvWNPlPJI/AAAAAAAAAPY/9LkFAJtksFI/s917/source-repositories2.JPG)
 
Troubleshooting
===============

Connection Issues
-----------------

Make sure you have Gerrit setup correctly with SSH identity key generated and 
host used in the SSH connection string added to known host. Detailed instructions 
are available in the [Gerrit documentation](https://gerrit-review.googlesource.com/Documentation/install-quick.html#usersetup).

**Note**: Some builds will still hang in native msysgit mode on Windows due to 
the following issue with the bamboo-git-plugin: [https://jira.atlassian.com/browse/BAM-11096](https://jira.atlassian.com/browse/BAM-11096).
        
You can work around this issue by manually adding your hostname, used in the 
repository configuration, to the msysgit/.ssh/known_hosts file. Example:

> 127.0.0.1 ssh-rsa XXXXB3NzaC1yc2EAAAADAQABBBBgQC699HzXHwr1H6OJeVlRo7h4r+3PY
> d0wNkqzl6EUAeU2iZjqFqQL2ZiNVqs2JrpTNadbgtXBNk9rhQIWajQZG9ZJG/OPxe+NOkbWQVev
> rcELsw5N2wxcJOWz+ey1tFv3VCtNCLUGgF7yIg0kZZVQ+HvAzLoMbiHs0haVmEjnLherSw==

NullPointerException Encountered after Upgrade
----------------------------------------------

If this is an upgrade, and you encouter a NullException when attempting to 
build, you may need to resave your repository settings.Release 1.2.3 changed 
the way the ssh private key is stored for multiple repositories. Resaving 
should resolve this issue.

Unable to Delete Repository
---------------------------

This is an old issue and has been fixed in new releases of Bamboo. Here's the 
[ticket](https://jira.atlassian.com/browse/BAM-11377).

Debugging
---------

If problems persist, you can turn on debugging in the Administration console to
help resolve and report issues. Steps to turn on debugging:

 * Goto Administration->System->Log Settings
 * Add com.houghtonassociates.bamboo.plugins=DEBUG
 
Check your Bamboo home directory for log output:

Ex: bamboo-home\logs\atlassian-bamboo.log

Bug Fixes and Enhancements
==========================
1.2.9 Updates
-------------

 * Issue #21 Gerrit Chain Results screen shows changes from other repositories
 * Issue #20 Edit Gerrit Repository' screen force to re-enter passphrase for private key
 * Issue #19 NPE on Bamboo agent after job is dispatched by gReview

1.2.8 Updates
-------------

 * Issue #16 4.2.X Support.
 
1.2.3 Updates
-------------

 * Issue #13 gReview doesn't uniquely store connections settings per plan.

1.2.2 Updates
-------------

 * Issue #12: NullPointer exception encountered in GerritService

1.2.1 Updates
-------------

 * Issue #11: Error message reported by failed build can break verification update.
 
1.2.0 Updates
-------------

 * Issue #10: Exclude Display of Commit Action Tag in Build Changes
 * Issue #9: Exception on failed build
 * Issue #8: Setting non standard port does not work
 * Issue #7: Include Build Results URL for Verified Changes in Gerrit
 * Issue #6: GitWeb Integration

1.1.3 Updates
-------------

 * Issue #5: Gerrit Tab Displays on Unrelated Plans

1.1.2 Updates
-------------

 * Issue #4:  Build Plan Fails When no Changes Open

1.1 Updates
-----------

 * Issue #1: Checkout Fails When Bamboo Configured with Native Git
 * Issue #2: Add Git Submodules Capability

How to build the Bamboo Gerrit Plugin
=====================================

Impatient way:
--------------

1. `mvn package' (Apache Maven 2.2.1 was used to develop this plugin)

More patient way:
-----------------

1. Download and install the [latest JDK](http://java.sun.com).
2. Download and install the [Atlassian Plugin SDK](http://confluence.atlassian.com/display/DEVNET/Setting+up+your+Plugin+Development+Environment).
3. Run `atlas-package -DskipTests' in the directory containing Bamboo Gerrit Plugin pom.xml.
4. Grab plugin JAR file from `./target/greview-<version>.jar'.

Full documentation on how to develop Atlassian Plugins is available 
at the [Atlassian Developer Site](http://confluence.atlassian.com/display/DEVNET/How+to+Build+an+Atlassian+Plugin).

How to deploy Git Plugin into existing Bamboo instance
======================================================

Full documentation on installing Atlassian Plugins is available at:
* http://confluence.atlassian.com/display/BAMBOO/Installing+a+new+Plugin
  (please note that gReview Plugin is 'Version 2' plugin)
  
Maintainer
==========

Jason Huntley
-------------

* jhuntley@houghtonassociates.com
* onepremise@gmail.com
* http://stackoverflow.com/users/1214542/jason-huntley
