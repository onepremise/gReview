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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.atlassian.bamboo.resultsummary.ResultSummaryPredicates;
import com.atlassian.bamboo.storage.StorageLocationService;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.build.ChainResultsAction;
import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.build.logger.BuildLogUtils;
import com.atlassian.bamboo.chains.ChainFilteredTestResults;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.ChainStageResult;
import com.atlassian.bamboo.chains.ContinuableStageHelper;
import com.atlassian.bamboo.comment.Comment;
import com.atlassian.bamboo.comment.CommentService;
import com.atlassian.bamboo.deployments.projects.DeploymentProjectStatusForResultSummary;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.filter.Pager;
import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.StageIdentifier;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.tests.FilteredTestResults;
import com.atlassian.bamboo.resultsummary.tests.TestClassResultDescriptor;
import com.atlassian.bamboo.resultsummary.vcs.RepositoryChangeset;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.ww2.aware.permissions.PlanReadSecurityAware;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;

import static com.houghtonassociates.bamboo.plugins.view.RepositoryHelper.getDefaultRepository;

/**
 * @author Jason Huntley
 * 
 */
public class ViewGerritChainResultsAction extends ChainResultsAction implements PlanReadSecurityAware {

    private static final long serialVersionUID = 1L;
    private GerritChangeVO changeVO = null;
    private GerritService gerritService = null;
    private static final Logger log = Logger
        .getLogger(ViewGerritChainResultsAction.class);
    private static final String GERRIT_REPOSITORY_PLUGIN_KEY =
        "com.houghtonassociates.bamboo.plugins.gReview:gerrit";
    
    // ------------------------------------------------------------------------------------------------------- Constants
    private static final int DEFAULT_DISPLAY_LINES = 25;
    private static final String BAMBOO_MAX_DISPLAY_LINES = "BAMBOO-MAX-DISPLAY-LINES";
    // ------------------------------------------------------------------------------------------------- Type Properties

    private FilteredTestResults<TestClassResultDescriptor> filteredTestResults;

    private boolean commentMode;
    private int linesToDisplay;
    private String jobResultKeyForLogDisplay;
    private List<DeploymentProjectStatusForResultSummary> relatedDeployments;
    private Map<Long, List<Comment>> commentsByEntity;

    // ---------------------------------------------------------------------------------------------------- Dependencies

    private DeploymentProjectService deploymentProjectService;
    private CommentService commentService;
    private StorageLocationService storageLocationService;

    public ViewGerritChainResultsAction() {
        super();

        changeVO = new GerritChangeVO();
    }
    
    // ---------------------------------------------------------------------------------------------------- Constructors
    // ----------------------------------------------------------------------------------------------- Interface Methods
    // -------------------------------------------------------------------------------------------------- Action Methods
    @Nullable
    public Job getJobForKey(String planKey) {
        return planManager.getPlanByKey(planKey, Job.class);
    }
    
    public void updateChangeVO() throws RepositoryException {
        final String revision = this.getRevision();

        if (revision == null) {
            changeVO = new GerritChangeVO();
        } else {
            final GerritChangeVO change =
                getGerritService().getChangeByRevision(revision);

            if (change == null) {
                log.error(this.getTextProvider().getText(
                    "repository.gerrit.messages.error.retrieve"));
                changeVO = new GerritChangeVO();
            } else {
                changeVO = change;
            }
        }
    }

    @Override
    public String doExecute() throws Exception {
    	updateChangeVO();
        
        if (getImmutableChain() == null || getImmutableChain().isMarkedForDeletion()) {
            addActionError(getText("chain.error.noChain", Lists.newArrayList(getPlanKey())));
            return ERROR;
        } else if (getChainResult() == null) {
            if (getChainResultNumber() > 0) {
                PlanResultKey planResultKey = PlanKeys.getPlanResultKey(getImmutableChain().getPlanKey(), getChainResultNumber());
                ChainResultsSummary chainResult = resultsSummaryManager.getResultsSummary(planResultKey, ChainResultsSummary.class);
                if (chainResult == null) {
                    addActionError(getText("chain.error.noChainResult", Lists.newArrayList(getPlanKey() + "-" + getChainResultNumber())));
                    return ERROR;
                } else {
                    setChainResult(chainResult);
                }
            } else {
                addActionError(getText("chain.error.noChainResult", Lists.newArrayList(getPlanKey() + "-" + getChainResultNumber())));
                return ERROR;
            }
        }

        // Load / save lines
        if (linesToDisplay <= 0) {
            linesToDisplay = NumberUtils.toInt(cookieCutter.getValueFromCookie(BAMBOO_MAX_DISPLAY_LINES), DEFAULT_DISPLAY_LINES);
        }
        
        if (linesToDisplay <= 0) {
            linesToDisplay = DEFAULT_DISPLAY_LINES;
        }
        
        cookieCutter.saveValueInCookie(BAMBOO_MAX_DISPLAY_LINES, String.valueOf(linesToDisplay));

        commentsByEntity = commentService.getAllCommentsForPlanResult(getChainResult());

        return SUCCESS;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    public FilteredTestResults<TestClassResultDescriptor> getFilteredTestResults() {
        if (filteredTestResults == null) {
            filteredTestResults = ChainFilteredTestResults.newInstance(testsManager, testQuarantineManager, getChainResult(), 0, getDefaultPageSizeForTests());
        }
        return filteredTestResults;
    }

    public boolean isLogAccessible(BuildResultsSummary jobResults) {
        if (jobResults != null) {
            File logFile = new File(BuildLogUtils.getLogFileDirectory(jobResults.getPlanKey()), BuildLogUtils.getLogFileName(jobResults.getPlanKey(), jobResults.getBuildNumber()));
            return logFile.canRead();
        }
        return false;
    }


    public List<ResultsSummary> getJobResultSummaries() {
        return getChainResult().getOrderedJobResultSummaries();
    }

    public boolean hasSharedArtifacts(ChainResultsSummary chainResultsSummary) {
       return !chainResultsSummary.getArtifactLinks().isEmpty();
    }

    @Nullable
    public BuildAgent getAgent(final long agentId) {
        return agentManager.getAgent(agentId);
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    protected int getDefaultPageSizeForTests() {
        return Pager.DEFAULT_PAGE_SIZE;
    }

    public boolean isCommentMode() {
        return commentMode;
    }

    public void setCommentMode(boolean commentMode) {
        this.commentMode = commentMode;
    }

    public int getLinesToDisplay() {
        return linesToDisplay;
    }

    public void setLinesToDisplay(int linesToDisplay) {
        this.linesToDisplay = linesToDisplay;
    }

    public StageIdentifier getStageToRestart() {
        return ContinuableStageHelper.getStageToRestart(getChainResult());
    }

    public String getJobResultKeyForLogDisplay() {
        if (jobResultKeyForLogDisplay == null && getChainResult().isInProgress()) {
            for (ChainStageResult stageResult : getChainResult().getStageResults()) {
                try{
                    jobResultKeyForLogDisplay = Iterables.find(stageResult.getBuildResults(),
                            ResultSummaryPredicates::isInProgress).getBuildResultKey();
                    return jobResultKeyForLogDisplay;
                } catch (NoSuchElementException e) {
                }
            }
        }

        return jobResultKeyForLogDisplay;
    }


    public List<DeploymentProjectStatusForResultSummary> getRelatedDeployments() {
        if (relatedDeployments == null) {
            relatedDeployments = deploymentProjectService.getDeploymentProjectsWithStatusesRelatedToPlanResult(getChainResult());
        }
        return relatedDeployments;
    }

    public Map<Long, List<Comment>> getCommentsByEntityId() {
        return commentsByEntity;
    }

    public void setDeploymentProjectService(DeploymentProjectService deploymentProjectService) {
        this.deploymentProjectService = deploymentProjectService;
    }
    
    public void setCommentService(final CommentService commentService) {
        this.commentService = commentService;
    }

    public void setStorageLocationService(StorageLocationService storageLocationService) {
        this.storageLocationService = storageLocationService;
    }

    public GerritRepositoryAdapter getRepository() {
        GerritRepositoryAdapter repository = null;

        Repository repo =
                getDefaultRepository(this.getImmutableChain());

        if (repo instanceof GerritRepositoryAdapter) {
            repository = (GerritRepositoryAdapter) repo;
        }

        return repository;
    }



    public GerritService getGerritService() throws RepositoryException {
        if (gerritService == null) {
            Repository repo =
                    getDefaultRepository(this.getImmutableChain());

            if (repo instanceof GerritRepositoryAdapter) {
                GerritRepositoryAdapter gra = getRepository();
                gerritService = gra.getGerritDAO();
            }
        }

        return gerritService;
    }

    public String getHTTPHost() {
        return getRepository().getHostname();
    }

    public GerritChangeVO getChange() {
        return changeVO;
    }

    public String getChangeID() {
        return changeVO.getId();
    }

    public String getRevision() {
        ResultsSummary rs = this.getResultsSummary();
        final List<RepositoryChangeset> changesets =
            rs.getRepositoryChangesets();
        // this.getChainResult().getRepositoryChangesets();
        for (RepositoryChangeset changeset : changesets) {
            if (changeset.getRepositoryData().getPluginKey()
                .equals(GERRIT_REPOSITORY_PLUGIN_KEY))
                return changeset.getChangesetId();
        }
        return null;
    }
}
