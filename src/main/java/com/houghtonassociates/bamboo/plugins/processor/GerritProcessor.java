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
package com.houghtonassociates.bamboo.plugins.processor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.atlassian.bamboo.build.CustomBuildProcessor;
import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.AdministrationConfigurationManager;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BaseConfigurableBuildPlugin;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;
import com.houghtonassociates.bamboo.plugins.utils.I18NUtils;
import com.opensymphony.xwork.TextProvider;

/**
 * Post processor which updates Gerrit after build completes
 */
public class GerritProcessor extends BaseConfigurableBuildPlugin implements CustomBuildProcessor {

    private final Logger logger = Logger.getLogger(GerritProcessor.class);

	// dependencies
    private TextProvider textProvider = null;
    private AdministrationConfigurationManager administrationConfigurationManager;

    private Map<String, String> customConfiguration = null;
    private static final String GERRIT_RUN = "custom.gerrit.run";


    @Override
    public void init(BuildContext buildContext) {
        super.init(buildContext);

        final List<RepositoryDefinition> repositories = buildContext.getRepositoryDefinitions();

        I18NUtils.updateTextProvider(textProvider, "repository.gerrit.name");

        this.customConfiguration = buildContext.getBuildDefinition().getCustomConfiguration();
    }

    public synchronized void setTextProvider(TextProvider textProvider) {
		this.textProvider = textProvider;
		
		if (textProvider !=  null) {
			I18NUtils.updateTextProvider(this.textProvider, "repository.gerrit.name");
		}
    }

    public void setAdministrationConfigurationManager(AdministrationConfigurationManager administrationConfigurationManager) {
        this.administrationConfigurationManager = administrationConfigurationManager;
    }

    @Override
    public void prepareConfigObject(BuildConfiguration buildConfiguration) {
        super.prepareConfigObject(buildConfiguration);
    }

    @Override
    public ErrorCollection validate(BuildConfiguration buildConfiguration) {
        return super.validate(buildConfiguration);
    }

    @Override
    protected void populateContextForView(Map<String, Object> context, Plan plan) {
        super.populateContextForView(context, plan);
    }

    @Override
    protected void populateContextForEdit(Map<String, Object> context, BuildConfiguration buildConfiguration,
			Plan plan) {
        super.populateContextForEdit(context, buildConfiguration, plan);

    }

    private String buildStatusString(CurrentBuildResult results) {
        AdministrationConfiguration config = administrationConfigurationManager.getAdministrationConfiguration();

        String resultsUrl = config.getBaseUrl() + "/browse/" + buildContext.getPlanResultKey().toString();

        List<String> errors = results.getBuildErrors();

        if (!results.getBuildState().equals(BuildState.SUCCESS)) {
            if (errors != null && errors.size() > 0) {
                return textProvider.getText("processor.gerrit.messages.build.custom", Arrays.asList(errors.toString(),
						resultsUrl));
            } else {
                return textProvider.getText("processor.gerrit.messages.build.failed", Arrays.asList(resultsUrl));
            }
        }

        return textProvider.getText("processor.gerrit.messages.build.sucess", Arrays.asList(resultsUrl));
    }

    @Override
    public BuildContext call() throws InterruptedException, Exception {
        final String buildPlanKey = buildContext.getPlanKey();
        final CurrentBuildResult results = buildContext.getBuildResult();
        final Boolean runVerification = Boolean.parseBoolean(customConfiguration.get(GERRIT_RUN));

        logger.info("Run verification: " + runVerification);

        if (runVerification) {
            final List<RepositoryDefinition> repositories = buildContext.getRepositoryDefinitions();

            for (RepositoryDefinition rd : repositories) {
                if (rd.getRepository() instanceof GerritRepositoryAdapter) {
                    logger.info("Updating Change Verification...");
                    updateChangeVerification(rd, buildPlanKey, results);
                }
            }
        }

        return buildContext;
    }

    private void updateChangeVerification(RepositoryDefinition rd, String buildPlanKey, CurrentBuildResult results)
			throws RepositoryException {
        final GerritRepositoryAdapter gra = (GerritRepositoryAdapter) rd.getRepository();
        final String revision = buildContext.getBuildChanges().getVcsRevisionKey(rd.getId());
        final GerritService service = new GerritService(gra.getHostname(), gra.getPort(), gra.getGerritAuthentication());
        final GerritChangeVO change = service.getChangeByRevision(revision);

        if (change == null) {
            logger.error(textProvider.getText("repository.gerrit.messages.error.retrieve"));
            return;
        } else if (change.isMerged()) {
            logger.info(textProvider.getText( "processor.gerrit.messages.build.verified.merged",
					Arrays.asList(change.getId())));
            return;
        }

        if ((results.getBuildReturnCode() == 0) && results.getBuildState().equals(BuildState.SUCCESS)) {
            if (service.verifyChange(true, change.getNumber(), change.getCurrentPatchSet().getNumber(),
					buildStatusString(results))) {

                logger.info(textProvider .getText("processor.gerrit.messages.build.verified.pos"));
			} else {
                logger.error(textProvider.getText("processor.gerrit.messages.build.verified.failed",
						Arrays.asList(change.getId())));
            }
        } else if (service.verifyChange(false, change.getNumber(), change.getCurrentPatchSet().getNumber(),
				buildStatusString(results))) {
            logger.info(textProvider .getText("processor.gerrit.messages.build.verified.neg"));
        } else {
            logger.error(textProvider.getText( "processor.gerrit.messages.build.verified.failed",
					Arrays.asList(change.getId())));
        }
    }
}
