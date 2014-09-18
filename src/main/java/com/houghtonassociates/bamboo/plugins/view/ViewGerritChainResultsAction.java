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

import java.util.List;

import org.apache.log4j.Logger;

import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.vcs.RepositoryChangeset;
import com.atlassian.bamboo.ww2.actions.chains.ViewChainResult;
import com.atlassian.bamboo.ww2.aware.permissions.PlanReadSecurityAware;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;

/**
 * @author Jason Huntley
 * 
 */
public class ViewGerritChainResultsAction extends ViewChainResult implements
    PlanReadSecurityAware {

    private static final long serialVersionUID = 1L;
    private GerritChangeVO changeVO = null;
    private GerritService gerritService = null;
    private static final Logger log = Logger
        .getLogger(ViewGerritChainResultsAction.class);
    private static final String GERRIT_REPOSITORY_PLUGIN_KEY =
        "com.houghtonassociates.bamboo.plugins.gReview:gerrit";

    public ViewGerritChainResultsAction() {
        super();

        changeVO = new GerritChangeVO();
    }

    public GerritRepositoryAdapter getRepository() {
        GerritRepositoryAdapter repository = null;

        Repository repo =
            PlanHelper.getDefaultRepository(this.getImmutablePlan());

        if (repo instanceof GerritRepositoryAdapter) {
            repository = (GerritRepositoryAdapter) repo;
        }

        return repository;
    }

    public GerritService getGerritService() throws RepositoryException {
        if (gerritService == null) {
            Repository repo =
                PlanHelper.getDefaultRepository(this.getImmutablePlan());

            if (repo instanceof GerritRepositoryAdapter) {
                GerritRepositoryAdapter gra = getRepository();
                gerritService = gra.getGerritDAO();
            }
        }

        return gerritService;
    }

    @Override
    public String doExecute() throws Exception {
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
        return super.doExecute();
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
