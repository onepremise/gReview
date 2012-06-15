/**
 * 
 */
package com.houghtonassociates.bamboo.plugins.view;

import java.util.List;

import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryDataEntity;
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
public class ViewGerritChainResultsAction extends ViewChainResult implements PlanReadSecurityAware {
	private static final long serialVersionUID = 1L;
	private GerritChangeVO changeVO=null;
	private GerritService gerritService=null;
	private GerritRepositoryAdapter repository=null;
	
	public GerritRepositoryAdapter getRepository() {
		if (repository==null) {
			Repository repo=PlanHelper.getDefaultRepository(this.getPlan());
			
			if (repo instanceof GerritRepositoryAdapter) {
				repository=(GerritRepositoryAdapter)repo;
			}
		}
		
		return repository;
	}
	
    public GerritService getGerritService() {
    	if (gerritService==null) {
    		Repository repo=PlanHelper.getDefaultRepository(this.getPlan());
    		
    		if (repo instanceof GerritRepositoryAdapter) {
    			GerritRepositoryAdapter gra=getRepository();
    			gerritService=new GerritService(gra.getHostname(), gra.getPort(),  gra.getGerritAuthentication());
    		}
    	}
    	
    	return gerritService;
    }

	@Override
	public String doExecute() throws Exception {
		changeVO=getGerritService().getChangeByRevision(this.getRevision());
		return super.doExecute();
	}
	
	public String getHTTPHost () {
		return getRepository().getHostname();
	}
	
	public GerritChangeVO getChange() {
		return changeVO;
	}
	
	public String getChangeID() {
		return changeVO.getId();
	}
	
	public String getRevision() {
		List<RepositoryChangeset> changeset=this.getResultsSummary().getRepositoryChangesets();
		RepositoryChangeset rcs=changeset.get(0);
		String revision=rcs.getChangesetId();
		return revision;
	}
}
