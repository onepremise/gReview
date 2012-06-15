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

[#-- @ftlvariable name="action" type="com.houghtonassociates.bamboo.plugins.view.ViewGerritChainResultsAction" --]
[#-- @ftlvariable name="resultsSummary" type="com.atlassian.bamboo.chains.ChainResultsSummary" --]

<html>
<head>
   <title>[@ui.header pageKey='webitems.build.submenu.gerrit' object='${plan.name} ${chainResultNumber}' title=true /]</title>
   <meta name="tab" content="gerrit" />
   <meta name="decorator" content="result">
</head>
<body>
   [@ui.header page='Gerrit Change Information' cssClass="headingGerrit"/]
   <HR>
   [#assign host = action.getHTTPHost() /]
   [#assign change = action.getChange() /]
   [#assign rev = action.getRevision() /]
   <ul>
   <li><B>URL:</B> <a href="${change.getUrl()}" target="_blank">${change.getUrl()}</a></li>
   <li><B>Project:</B> ${change.getProject()}</li>
   <li><B>Branch:</B> ${change.getBranch()}</li>
   <li><B>Change ID:</B> ${change.getId()}</li>
   <li><B>Revision:</B> ${rev}</li>
   <li><B>Subject:</B> ${change.getSubject()}</li>
   <li><B>Owner:</B> ${change.getOwnerName()}</li>
   <li><B>Owner Email:</B> ${change.getOwnerEmail()}</li>
   <li><B>Created On:</B> ${change.getCreatedOn()?datetime}</li>
   <li><B>Last Update:</B> ${change.getLastUpdate()?datetime}</li>
   <li><B>Verification Score:</B> ${change.getVerificationScore()}</li>
   <li><B>Review Score:</B> ${change.getReviewScore()}</li>
   <li><B>Status:</B> ${change.getStatus()}</li>
   </ul>
   [#include "viewCommonGerritResults.ftl"]
</body>
</html>