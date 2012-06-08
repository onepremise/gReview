/**
 * 
 */
package com.google.gerrit.bamboo.plugins.view;

import java.util.List;
import java.util.Map;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.plan.IncorrectPlanTypeException;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import com.google.common.collect.Lists;

/**
 * @author Jason Huntley
 *
 */
public class ViewGerritResultsCondition implements Condition {
	private PlanManager planManager;
	
	@Override
	public void init(Map<String, String> params) throws PluginParseException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean shouldDisplay(Map<String, Object> context) {
		final String buildKey = (String) context.get("buildKey");
        //final Build build = buildManager.getBuildByKey(buildKey);
        //final String sonarRuns = (String) build.getBuildDefinition().getCustomConfiguration().get(SONAR_RUN);
		return true;
	}

    /**
     * Internal method to get a {@link List} of {@link Job} objects by a given Key
     * 
     * @param key the key to get the {@link Job} objects by, can be a plan key of a build key
     * @return the {@link List} of {@link Job} objects
     */
    private List<Job> getAllJobsByKey(String key) {
            List<Job> jobs = Lists.newArrayList();
            try {
                    Chain plan = planManager.getPlanByKey(key, Chain.class);
                    jobs.addAll(plan.getAllJobs());
            } catch (IncorrectPlanTypeException e) {
                    // Oke it was a build key and not a plan key
                    jobs.add(planManager.getPlanByKey(key, Job.class));
            }
            return jobs;
    }

    /**
     * Setter for planManager
     * 
     * @param planManager the planManager to set
     */
    public void setPlanManager(PlanManager planManager) {
            this.planManager = planManager;
    }
}
