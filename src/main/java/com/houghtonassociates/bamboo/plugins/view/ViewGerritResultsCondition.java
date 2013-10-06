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

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.plan.IncorrectPlanTypeException;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import com.google.common.collect.Lists;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;

import java.util.List;
import java.util.Map;

/**
 * @author Jason Huntley
 * 
 */
public class ViewGerritResultsCondition implements Condition {

    private PlanManager planManager;

    @Override
    public void init(Map<String, String> params) throws PluginParseException {
    }

    /**
     * Setter for planManager
     *
     * @param planManager
     *            the planManager to set
     */
    @SuppressWarnings("unused")
    public void setPlanManager(PlanManager planManager) {
        this.planManager = planManager;
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        final String buildKey = (String) context.get("buildKey");
        Plan plan = planManager.getPlanByKey(PlanKeys.getPlanKey(buildKey));
        Repository repo = PlanHelper.getDefaultRepository(plan);

        if (repo instanceof GerritRepositoryAdapter) {
            return true;
        }

        return false;
    }
}
