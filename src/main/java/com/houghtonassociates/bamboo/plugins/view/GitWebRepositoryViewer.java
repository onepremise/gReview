/**
 * Copyright 2012 Houghton Associates
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.houghtonassociates.bamboo.plugins.view;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.repository.RepositoryData;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.vcs.RepositoryChangeset;
import com.atlassian.bamboo.util.UrlUtils;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.webrepository.AbstractWebRepositoryViewer;
import com.atlassian.bamboo.webrepository.CommitUrlProvider;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.util.concurrent.NotNull;
import com.atlassian.util.concurrent.Nullable;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;

/**
 * GitWeb Repository Browser Integration.
 * 
 * @author Jason Huntley
 * 
 */
public class GitWebRepositoryViewer extends AbstractWebRepositoryViewer
    implements CommitUrlProvider {

    private static final String FULL_COMMIT_VIEW_TEMPLATE =
        "gitwebViewOfCommits.ftl";
    private static final String SUMMARY_COMMIT_VIEW_TEMPLATE =
        "gitwebViewOfCommitsSummary.ftl";

    protected static final String GITWEB_REPOSITORY_URL =
        "webRepository.gitwebRepositoryViewer.webRepositoryUrl";
    protected static final String GITWEB_REPOSITORY_NAME =
        "webRepository.gitwebRepositoryViewer.webRepositoryRepoName";
    protected static final String GITWEB_REPOSITORY_PATH =
        "webRepository.gitwebRepositoryViewer.webRepositoryPath";

    private static final String GITWEB_PROJCT = "?p=";
    private static final String GITWEB_ACTION_COMMIT_HIST = ";a=commit;h=";
    private static final String GITWEB_ACTION_COMMIT_DIFF = ";a=commitdiff;h=";
    private static final String GITWEB_ACTION_HISTORY = ";a=history;";
    private static final String GITWEB_ACTION_BLOB = ";a=blob";
    private static final String GITWEB_FILE = ";f=";
    private static final String GITWEB_FILE_HIST = ";h=";
    private static final String GITWEB_FILE_BHIST = ";hb=";

    private static final String BAMBOO_REV = "revision=";

    private static final String GIT_COMMIT_ACTION = "/COMMIT_MSG";

    private String webRepositoryUrl;
    private String webRepositoryPath;
    private String webRepositoryRepoName;

    private transient CustomVariableContext customVariableContext;

    private final Logger logger = Logger
        .getLogger(GitWebRepositoryViewer.class);

    private boolean gerritOnline = true;

    // For speeding up consecutive searches in a session
    private Map<String, String> changeIDtoRev = new HashMap<String, String>();

    @Override
    public void populateFromParams(@NotNull ActionParametersMap params) {
        setWebRepositoryUrl(params.getString(GITWEB_REPOSITORY_URL));
        setWebRepositoryRepoName(params.getString(GITWEB_REPOSITORY_NAME));
        setWebRepositoryPath(params.getString(GITWEB_REPOSITORY_PATH));
    }

    @Override
    public void populateFromConfig(@NotNull HierarchicalConfiguration config) {
        setWebRepositoryUrl(config.getString(GITWEB_REPOSITORY_URL));
        setWebRepositoryRepoName(config.getString(GITWEB_REPOSITORY_NAME));
        setWebRepositoryPath(config.getString(GITWEB_REPOSITORY_PATH));
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration() {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(GITWEB_REPOSITORY_NAME,
            getWebRepositoryRepoName());
        configuration.setProperty(GITWEB_REPOSITORY_PATH,
            getWebRepositoryPath());
        configuration.setProperty(GITWEB_REPOSITORY_URL,
            com.atlassian.bamboo.util.UrlUtils
                .correctlyFormatUrl(getWebRepositoryUrl()));
        return configuration;
    }

    @NotNull
    @Override
    public ErrorCollection
                    validate(@NotNull BuildConfiguration buildConfiguration) {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        String webRepositoryUrl =
            buildConfiguration.getString(GITWEB_REPOSITORY_URL);
        if (StringUtils.isBlank(webRepositoryUrl)) {
            errorCollection.addError(GITWEB_REPOSITORY_URL,
                "Please specify the url for your GITWEB instance.");
        }

        webRepositoryUrl =
            customVariableContext.substituteString(webRepositoryUrl);
        if (!StringUtils.isBlank(webRepositoryUrl)
            && !com.opensymphony.util.UrlUtils
                .verifyHierachicalURI(webRepositoryUrl)) {
            errorCollection.addError(GITWEB_REPOSITORY_URL,
                "This is not a valid url");
        }

        String webRepositoryName =
            buildConfiguration.getString(GITWEB_REPOSITORY_NAME);
        if (StringUtils.isBlank(webRepositoryName)) {
            errorCollection
                .addError(GITWEB_REPOSITORY_NAME,
                    "Please specify the name of the repository on your GITWEB instance.");
        }

        return errorCollection;
    }

    private boolean isChangeID(String id) {
        // I0000000000000000000000000000000000000000
        if (id.startsWith("I") && id.substring(1).length() == 40) {
            return true;
        }

        return false;
    }

    private String resolveIfChangeID(RepositoryData rd, String id) {
        String newRevision = id;
        if (rd.getRepository() instanceof GerritRepositoryAdapter) {
            GerritRepositoryAdapter gra =
                (GerritRepositoryAdapter) rd.getRepository();

            GerritChangeVO change = null;

            if (isChangeID(id)) {
                if (gerritOnline) {
                    try {
                        // For speeding up consecutive searches in a session
                        newRevision = changeIDtoRev.get(id);

                        if (newRevision == null || newRevision.isEmpty()) {
                            logger.info("Resolving ID...");
                            change = gra.getGerritDAO().getChangeByID(id);
                            newRevision = change.getLastRevision();
                            changeIDtoRev.put(id, newRevision);
                        } else {
                            logger.info("Using previously resolved revision.");
                        }
                    } catch (RepositoryException e) {
                        logger.error("Failed to load change ID!");
                        logger.error(e.getMessage());
                        gerritOnline = false;
                        return null;
                    }
                } else {
                    logger.error("Gerrit is Offline!");
                    return null;
                }
            }
        }

        return newRevision;
    }

    /**
     * Build the GitWeb URL for browsing source
     * 
     * @param rd
     *            - Repository Data
     * @param rev
     *            - hash id
     * 
     * @return
     * @throws RepositoryException
     */
    private String buildWebURL(RepositoryData rd, String rev) {
        String url = null;

        if (isChangeID(rev)) {
            rev = resolveIfChangeID(rd, rev);

            if (rev != null)
                url = buildDefaultGitWebURL(rev);
        } else {
            url = buildDefaultGitWebURL(rev);
        }

        return url;
    }

    private String buildDefaultGitWebURL(String rev) {
        if (rev == null)
            return null;

        return buildProjectURL() + GITWEB_ACTION_COMMIT_HIST + rev;
    }

    private String buildProjectURL() {
        StringBuilder result = new StringBuilder();
        if (StringUtils.isBlank(webRepositoryUrl)) {
            logger
                .warn("Web url is not defined. Can not generate web repository urls for file.");
            return null;
        }

        String substitutedWebUrl =
            customVariableContext.substituteString(webRepositoryUrl);
        if (substitutedWebUrl == null) {
            logger.warn("Variable substitution failed for web url '"
                + webRepositoryUrl + "', using original url.");
            substitutedWebUrl = webRepositoryUrl;
        }

        result.append(UrlUtils.appendSlashIfDoesntExist(substitutedWebUrl));

        String substitutedRepoName =
            customVariableContext.substituteString(webRepositoryRepoName);
        if (substitutedRepoName == null) {
            logger.warn("Variable substitution failed for web name '"
                + webRepositoryRepoName + "', using original name.");
            substitutedRepoName = webRepositoryRepoName;
        }

        result.append(GITWEB_PROJCT + substitutedRepoName);

        return result.toString();
    }

    public String
                    getHtmlForCommitsFull(ResultsSummary resultsSummary,
                                          RepositoryChangeset repositoryChangeset,
                                          RepositoryDefinition repositoryData) {
        final Map<String, Object> context = new HashMap<String, Object>();

        context.put("buildResultsSummary", resultsSummary);
        context.put("repositoryChangeset", repositoryChangeset);
        context.put("repositoryData", repositoryData);
        context.put("linkGenerator", this);

        return templateRenderer.render(FULL_COMMIT_VIEW_TEMPLATE, context);
    }

    public String
                    getHtmlForCommitsSummary(ResultsSummary resultsSummary,
                                             RepositoryChangeset repositoryChangeset,
                                             RepositoryDefinition repositoryData,
                                             int maxChanges) {
        final Map<String, Object> context = new HashMap<String, Object>();

        context.put("buildResultsSummary", resultsSummary);
        context.put("repositoryChangeset", repositoryChangeset);
        context.put("repositoryData", repositoryData);
        context.put("linkGenerator", this);
        context.put("maxChanges", maxChanges);

        String result =
            templateRenderer.render(SUMMARY_COMMIT_VIEW_TEMPLATE, context);

        return result;
    }

    public String
                    getHtmlForCommitsSummary(ResultsSummary resultsSummary,
                                             RepositoryChangeset repositoryChangeset,
                                             RepositoryDefinition repositoryDefinition) {
        final Map<String, Object> context = new HashMap<String, Object>();

        context.put("buildResultsSummary", resultsSummary);
        context.put("repositoryChangeset", repositoryChangeset);
        context.put("repositoryData", (RepositoryData) repositoryDefinition);
        context.put("linkGenerator", this);
        context.put("maxChanges", 2);

        return templateRenderer.render(SUMMARY_COMMIT_VIEW_TEMPLATE, context);
    }

    /**
     * Generates the revision string for the build summary and changes tab
     * Example: Revision 102856bbc6439fcb9ed35a20469b2f0a48eff99e
     * 
     * @param commits
     * @param repositoryData
     * @return
     */
    public Map<Commit, String>
                    getWebRepositoryUrlForCommits(Collection<Commit> commits,
                                                  RepositoryData repositoryData) {
        Map<Commit, String> commitsToUrls = new HashMap<Commit, String>();

        if (webRepositoryUrl == null) {
            logger
                .warn("Web url is not defined. Can not generate web repository urls for file.");
        } else {
            for (Commit commit : commits) {
                String result = "";
                final String cs = commit.guessChangeSetId();

                if (cs != null) {
                    result = buildWebURL(repositoryData, cs);
                    commitsToUrls.put(commit, result);
                }
            }
        }

        return commitsToUrls;
    }

    @Override
    public String getWebRepositoryUrlForCommit(Commit commit,
                                               RepositoryData repositoryData) {
        Map<Commit, String> commitsToUrls =
            getWebRepositoryUrlForCommits(Arrays.asList(commit), repositoryData);
        return commitsToUrls.isEmpty() ? null : commitsToUrls.get(commit);
    }

    /**
     * Pulls back version url for each file listed under changes.
     * 
     * @param revisions
     * @param repositoryData
     * @return
     */
    public Map<String, String>
                    getWebRepositoryUrlForRevisions(Collection<String> revisions,
                                                    RepositoryData repositoryData) {
        Map<String, String> commitsToUrls = new HashMap<String, String>();

        if (webRepositoryUrl == null) {
            logger
                .warn("Web url is not defined. Can not generate web repository urls for file.");
        } else {
            for (String revision : revisions) {
                String newRev = new String(revision);
                String result = "";

                // Sometimes format comes back as CommitFile.toString(), not
                // sure why Bamboo is doing this.
                // com.atlassian.bamboo.commit.CommitFileImpl@9d6dbc1[name=src/com/houghtonassociates/DemoGitMatt.java,revision=102856bbc6439fcb9ed35a20469b2f0a48eff99e]

                if (revision != null) {
                    if (revision.length() > 40) {
                        int index = revision.lastIndexOf(BAMBOO_REV);
                        newRev =
                            revision.substring(index + BAMBOO_REV.length(),
                                revision.length() - 1);

                    }

                    result = buildWebURL(repositoryData, newRev);
                    commitsToUrls.put(revision, result);
                }
            }
        }

        return commitsToUrls;
    }

    @Override
    public String getWebRepositoryUrlForRevision(String revisionId,
                                                 RepositoryData repositoryData) {
        Map<String, String> revisionsToUrls =
            getWebRepositoryUrlForRevisions(Arrays.asList(revisionId),
                repositoryData);
        return revisionsToUrls.isEmpty() ? null : revisionsToUrls
            .get(revisionId);
    }

    /**
     * Pulls back gitweb file urls for each file listed under changes.
     * 
     * @param revisions
     * @param repositoryData
     * @return
     */
    @Nullable
    public String getWebRepositoryUrlForFile(@NotNull CommitFile file,
                                             RepositoryData repositoryData) {
        StringBuilder result = new StringBuilder();
        String revision = file.getRevision();

        if (file.getName().equals(GIT_COMMIT_ACTION))
            return null;

        revision = this.resolveIfChangeID(repositoryData, revision);

        if (revision == null)
            return null;

        if (StringUtils.isBlank(webRepositoryUrl)) {
            logger
                .warn("Web url is not defined. Can not generate web repository urls for file.");
            return null;
        }

        result.append(buildProjectURL());

        String substitutedPathToRemove =
            customVariableContext.substituteString(webRepositoryPath);
        String fileName = file.getName();
        if (substitutedPathToRemove != null) {
            substitutedPathToRemove =
                UrlUtils.stripLeadingSlashes(substitutedPathToRemove);
            fileName = UrlUtils.stripLeadingSlashes(fileName);
            if (fileName.startsWith(substitutedPathToRemove)) {
                fileName = fileName.substring(substitutedPathToRemove.length());
            }
        }

        fileName = UrlUtils.stripLeadingSlashes(fileName);

        result.append(GITWEB_ACTION_BLOB + GITWEB_FILE);
        result.append(fileName);

        result.append(GITWEB_FILE_BHIST);
        result.append(revision);

        return result.toString();
    }

    @Nullable
    public String getWebRepositoryUrlForDiff(CommitFile file,
                                             RepositoryData repositoryData) {
        StringBuilder result = new StringBuilder();
        String revision = file.getRevision();

        if (file.getName().equals(GIT_COMMIT_ACTION))
            return null;

        revision = this.resolveIfChangeID(repositoryData, revision);

        if (revision == null)
            return null;

        if (file.isRevisionKnown()) {
            revision = resolveIfChangeID(repositoryData, revision);

            if (revision != null) {
                result.append(this.buildProjectURL()
                    + GITWEB_ACTION_COMMIT_DIFF + revision);
            }
        }

        return result.toString();
    }

    public String getWebRepositoryUrl() {
        return webRepositoryUrl;
    }

    public void setWebRepositoryUrl(@Nullable String webRepositoryUrl) {
        this.webRepositoryUrl =
            webRepositoryUrl == null ? null : webRepositoryUrl.trim();
    }

    public String getWebRepositoryPath() {
        return webRepositoryPath;
    }

    public void setWebRepositoryPath(@Nullable String webRepositoryPath) {
        this.webRepositoryPath =
            webRepositoryPath == null ? null : webRepositoryPath.trim();
    }

    public String getWebRepositoryRepoName() {
        return webRepositoryRepoName;
    }

    public void
                    setWebRepositoryRepoName(@Nullable String webRepositoryRepoName) {
        this.webRepositoryRepoName =
            webRepositoryRepoName == null ? null : webRepositoryRepoName.trim();
    }

    public void
                    setCustomVariableContext(final CustomVariableContext customVariableContext) {
        this.customVariableContext = customVariableContext;
    }
}
