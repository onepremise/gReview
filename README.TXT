===============================================================================
gReview - The Bamboo Gerrit Integration Plugin
===============================================================================

This plugin polls Gerrit for changes submitted to the Gerrit review system. 
When a new change is submitted, gReview will checkout the change and verify
it builds.

===============================================================================
How to build the Bamboo Gerrit Plugin
===============================================================================

Impatient way:
1. `mvn package' (Apache Maven 2.2.1 was used to develop this plugin)

More patient way:
1. Download and install the latest JDK:
   * http://java.sun.com
2. Download and install the Atlassian Plugin SDK:
   * http://confluence.atlassian.com/display/DEVNET/Setting+up+your+Plugin+Development+Environment
3. Run `atlas-package -DskipTests' in the directory containing Bamboo Gerrit Plugin pom.xml.
4. Grab plugin JAR file from `./target/greview-<version>.jar'

Full documentation on how to develop Atlassian Plugin is available at:
* http://confluence.atlassian.com/display/DEVNET/How+to+Build+an+Atlassian+Plugin

===============================================================================
How to deploy Git Plugin into existing Bamboo instance
===============================================================================

Full documentation on how installing Atlassian Plugins is available at:
* http://confluence.atlassian.com/display/BAMBOO/Installing+a+new+Plugin
  (please note that Bamboo Git Plugin is 'Version 1' plugin)
  
===============================================================================
Maintainer
===============================================================================

Jason Huntley - jhuntley@houghtonassociates.com
                onepremise@gmail.com