[#--
 ~ Copyright 2012 Houghton Associates
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~    http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--]

[#-- @ftlvariable name="buildConfiguration" type="com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration" --]
[#-- @ftlvariable name="plan" type="com.atlassian.bamboo.plan.Plan" --]
[#-- @ftlvariable name="repository" type="com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter" --]

[@ui.bambooSection]
	[@ww.textfield labelKey='repository.gerrit.hostname' name='repository.gerrit.hostname' required='true' /]
	[@ww.textfield labelKey='repository.gerrit.port' name='repository.gerrit.port' required='true' value='29418' /]
	
	[@ww.textfield labelKey='repository.gerrit.username' name='repository.gerrit.username' required='true' /]
	
	[#if buildConfiguration.getString('repository.gerrit.ssh.key')?has_content]
        [@ww.checkbox labelKey='repository.gerrit.ssh.key.change' toggle='true' name='temporary.gerrit.ssh.key.change' /]
        [@ui.bambooSection dependsOn='temporary.gerrit.ssh.key.change' showOn='true']
            [@ww.file labelKey='repository.gerrit.ssh.key' name='temporary.gerrit.ssh.keyfile' /]
        [/@ui.bambooSection]
    [#else]
        [@ww.hidden name='temporary.gerrit.ssh.key.change' value='true' /]
        [@ww.file labelKey='repository.gerrit.ssh.key' name='temporary.gerrit.ssh.keyfile' /]
    [/#if]

    [#if buildConfiguration.getString('repository.gerrit.ssh.passphrase')?has_content]
        [@ww.checkbox labelKey='repository.passphrase.change' toggle='true' name='temporary.gerrit.ssh.passphrase.change' /]
        [@ui.bambooSection dependsOn='temporary.gerrit.ssh.passphrase.change' showOn='true']
            [@ww.password labelKey='repository.gerrit.ssh.passphrase' name='temporary.gerrit.ssh.passphrase' /]
        [/@ui.bambooSection]
    [#else]
        [@ww.hidden name='temporary.git.gerrit.passphrase.change' value="true" /]
        [@ww.password labelKey='repository.gerrit.ssh.passphrase' name='temporary.gerrit.ssh.passphrase' /]
    [/#if]
[/@ui.bambooSection]

[@ww.checkbox labelKey='repository.gerrit.useShallowClones' toggle='true' name='repository.gerrit.useShallowClones' /]
[@ui.bambooSection dependsOn='repository.gerrit.useShallowClones' showOn='true']
    [#if (plan.buildDefinition.branchIntegrationConfiguration.enabled)!false ]
        [@ui.messageBox type='info']
            [@ww.text name='repository.gerrit.messages.branchIntegration.shallowClonesWillBeDisabled'/]
        [/@ui.messageBox]
    [/#if]
[/@ui.bambooSection]