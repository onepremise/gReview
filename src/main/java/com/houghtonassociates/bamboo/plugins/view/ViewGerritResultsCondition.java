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

import java.util.Map;

import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;

/**
 * @author Jason Huntley
 * 
 */
public class ViewGerritResultsCondition implements Condition {

    private CachedPlanManager cachedPlanManager;

    @Override
    public void init(Map<String, String> params) throws PluginParseException {

    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        final String buildKey = (String) context.get("buildKey");
        // final Build build = buildManager.getBuildByKey(buildKey);
        ImmutableChain plan = cachedPlanManager.getPlanByKey(PlanKeys.getPlanKey(buildKey), ImmutableChain.class);
        // final String sonarRuns = (String)
        // build.getBuildDefinition().getCustomConfiguration().get(SONAR_RUN);
        if(plan != null) {
            Repository repo = RepositoryHelper.getDefaultRepository(plan);

            if (repo instanceof GerritRepositoryAdapter) {
                return true;
            }
        }

        return false;
    }

    /**
     * Setter for planManager
     * 
     * @param planManager
     *            the planManager to set
     */
    public void setPlanManager(CachedPlanManager planManager) {
        this.cachedPlanManager = planManager;
    }
}
