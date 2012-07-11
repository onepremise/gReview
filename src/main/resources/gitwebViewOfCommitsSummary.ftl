[#-- @ftlvariable name="maxChanges" type="java.lang.Integer" --]
[#-- @ftlvariable name="buildResultsSummary" type="com.atlassian.bamboo.resultsummary.ResultsSummary" --]
[#-- @ftlvariable name="repositoryChangeset" type="com.atlassian.bamboo.resultsummary.vcs.RepositoryChangeset" --]
[#-- @ftlvariable name="linkGenerator" type="com.houghtonassociates.bamboo.plugins.view.GitWebRepositoryViewer" --]

[#if repositoryChangeset.commits?has_content]
    [#assign commits = repositoryChangeset.commits.toArray()?sort_by("date")?reverse]
    <h3 class="repository-name">${repositoryChangeset.repositoryData.getName()?html}</h3>
    <ul>
        [#assign commitToUrls = linkGenerator.getWebRepositoryUrlForCommits(commits, repositoryData)! /]
        [#list commits as commit]
            [#if commit_index gte maxChanges && maxChanges gte 0]
                [#break]
            [/#if]
            <li>
                [@ui.displayUserGravatar userName=(commit.author.linkedUserName)! size='25' class="profileImage"/]
                <h3>
                    <a href="[@cp.displayAuthorOrProfileLink author=commit.author /]">[@ui.displayAuthorFullName author=commit.author /]</a>
                    <span class="revision-date">
                        [@ui.time datetime=commit.date]${commit.date?datetime?string}[/@ui.time]
                    </span>
                    [#if "Unknown" != commit.author.name && commitToUrls.containsKey(commit)]
                        [#assign commitUrl = commitToUrls.get(commit)! /]
                        [#assign guessedRevision = commit.guessChangeSetId()!("")]
                        [#if commitUrl?has_content && guessedRevision?has_content]
                            <a href="${commitUrl}" class="revision-id" title="[@ww.text name="webRepositoryViewer.gitweb.viewChangeset" /]">${guessedRevision}</a>
                        [#else ]
                            <span class="revision-id" title="[@ww.text name='webRepositoryViewer.gitweb.error.cantCreateUrl' /]">${commit.changeSetId!}</span>
                        [/#if]
                    [/#if]
                </h3>
                <p>[@ui.renderValidJiraIssues commit.comment buildResultsSummary  /]</p>
            </li>
        [/#list]
    </ul>
[/#if]