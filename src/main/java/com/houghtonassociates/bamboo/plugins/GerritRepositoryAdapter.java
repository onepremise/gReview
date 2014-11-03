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
package com.houghtonassociates.bamboo.plugins;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.author.AuthorCachingFacade;
import com.atlassian.bamboo.bandana.BambooBandanaContext;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.build.strategy.BuildStrategy;
import com.atlassian.bamboo.build.strategy.TriggeredBuildStrategy;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.plan.PlanExecutionManager;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.TopLevelPlan;
import com.atlassian.bamboo.plan.branch.ChainBranch;
import com.atlassian.bamboo.plan.branch.ChainBranchManager;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.project.Project;
import com.atlassian.bamboo.repository.AbstractStandaloneRepository;
import com.atlassian.bamboo.repository.AdvancedConfigurationAwareRepository;
import com.atlassian.bamboo.repository.BranchInformationProvider;
import com.atlassian.bamboo.repository.BranchMergingAwareRepository;
import com.atlassian.bamboo.repository.BranchingAwareRepository;
import com.atlassian.bamboo.repository.CustomVariableProviderRepository;
import com.atlassian.bamboo.repository.PushCapableRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryDataEntity;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.repository.RepositoryDefinitionManager;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.remote.RemoteBuildDirectoryManager;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.v2.build.repository.RequirementsAwareRepository;
import com.atlassian.bamboo.v2.events.ChangeDetectionRequiredEvent;
import com.atlassian.bamboo.v2.ww2.build.TriggerRemoteBuild;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.variable.VariableDefinitionManager;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.bandana.DefaultBandanaManager;
import com.atlassian.bandana.impl.MemoryBandanaPersister;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.sal.api.message.I18nResolver;
import com.google.common.collect.ImmutableList;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.PatchSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritConfig;
import com.houghtonassociates.bamboo.plugins.dao.GerritProcessListener;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;
import com.houghtonassociates.bamboo.plugins.dao.jgit.JGitRepository;
import com.opensymphony.xwork2.TextProvider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;

/**
 * This class allows bamboo to use Gerrit as if it were a repository.
 */
public class GerritRepositoryAdapter extends AbstractStandaloneRepository
    implements AdvancedConfigurationAwareRepository, PushCapableRepository,
    BranchingAwareRepository, BranchMergingAwareRepository,
    BranchInformationProvider, GerritConnectionConfig2,
    CustomVariableProviderRepository, CustomSourceDirectoryAwareRepository,
    RequirementsAwareRepository, GerritProcessListener {

    private static final long serialVersionUID = -3518800283574344591L;

    private static final String REPOSITORY_URL = "repositoryUrl";
    private static final String REPOSITORY_USERNAME = "username";

    @Deprecated
    private static final String REPOSITORY_GERRIT_PLAN_KEY = "planKey";

    private static final String REPOSITORY_GERRIT_PROJECT_KEY = "projectKey";
    private static final String REPOSITORY_GERRIT_PROJECT_NAME = "projectName";
    private static final String REPOSITORY_GERRIT_CHAIN_KEY = "chainKey";
    private static final String REPOSITORY_GERRIT_CHAIN_NAME = "chainName";
    private static final String REPOSITORY_GERRIT_CHAIN_DESC =
        "chainDescription";

    private static final String REPOSITORY_GERRIT_REPOSITORY_HOSTNAME =
        "repository.gerrit.hostname";
    private static final String REPOSITORY_GERRIT_REPOSITORY_PORT =
        "repository.gerrit.port";
    private static final String REPOSITORY_GERRIT_PROJECT =
        "repository.gerrit.project";

    private static final String REPOSITORY_GERRIT_BRANCH =
        "repository.gerrit.branch";
    private static final String REPOSITORY_GERRIT_DEFAULT_BRANCH =
        "repository.gerrit.default.branch";
    private static final String REPOSITORY_GERRIT_CUSTOM_BRANCH =
        "repository.gerrit.custom.branch";

    private static final String REPOSITORY_GERRIT_USERNAME =
        "repository.gerrit.username";
    private static final String REPOSITORY_GERRIT_EMAIL =
        "repository.gerrit.email";
    private static final String SHARED_CREDENTIALS = "SHARED_CREDENTIALS";
    private static final String REPOSITORY_GIT_SHAREDCREDENTIALS_ID =
        "repository.gerrit.sharedCrendentials";
    private static final String REPOSITORY_GIT_AUTHENTICATION_TYPE =
        "repository.gerrit.authenticationType";
    private static final String REPOSITORY_GERRIT_SSH_KEY =
        "repository.gerrit.ssh.key";
    private static final String REPOSITORY_GERRIT_SSH_KEY_FILE =
        "repository.gerrit.ssh.keyfile";
    private static final String REPOSITORY_GERRIT_CONFIG_DIR =
        "repository.gerrit.config.dir";
    private static final String REPOSITORY_GERRIT_SSH_PASSPHRASE =
        "repository.gerrit.ssh.passphrase";
    private static final String TEMPORARY_GERRIT_SSH_PASSPHRASE =
        "temporary.gerrit.ssh.passphrase";
    private static final String TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE =
        "temporary.gerrit.ssh.passphrase.change";
    private static final String TEMPORARY_GERRIT_SSH_KEY_FROM_FILE =
        "temporary.gerrit.ssh.keyfile";
    private static final String TEMPORARY_GERRIT_SSH_KEY_CHANGE =
        "temporary.gerrit.ssh.key.change";

    private static final String REPOSITORY_GERRIT_USE_SHALLOW_CLONES =
        "repository.gerrit.useShallowClones";
    private static final String REPOSITORY_GERRIT_USE_SUBMODULES =
        "repository.gerrit.useSubmodules";
    private static final String REPOSITORY_GERRIT_COMMAND_TIMEOUT =
        "repository.gerrit.commandTimeout";
    private static final String REPOSITORY_GERRIT_VERBOSE_LOGS =
        "repository.gerrit.verbose.logs";
    private static final int DEFAULT_COMMAND_TIMEOUT_IN_MINUTES = 180;

    private static final String GIT_COMMIT_ACTION = "/COMMIT_MSG";

    protected static boolean USE_SHALLOW_CLONES = new SystemProperty(false,
        "atlassian.bamboo.git.useShallowClones",
        "ATLASSIAN_BAMBOO_GIT_USE_SHALLOW_CLONES").getValue(true);

    private static final String REPOSITORY_GERRIT_CHANGE_ID =
        "repository.gerrit.change.id";
    private static final String REPOSITORY_GERRIT_CHANGE_NUMBER =
        "repository.gerrit.change.number";
    private static final String REPOSITORY_GERRIT_REVISION_NUMBER =
        "repository.gerrit.revision.number";

    private static final Logger log = Logger
        .getLogger(GerritRepositoryAdapter.class);

    private static final VcsBranch ALL_BRANCH = new VcsBranchImpl(
        "All branches");

    private static final VcsBranch MASTER_BRANCH = new VcsBranchImpl("master");
    private static final String CUSTOM_BRANCH_SET = "Custom";

    private String hostname = "";
    private int port = 29418;
    private String project = "";
    private String username = "";
    private String userEmail = "";
    private String sshKey = "";
    private String relativeConfigPath = "";
    private String absConfigPath = "";
    private String relativeSSHKeyFilePath = "";
    private File sshKeyFile = null;
    private String sshPassphrase = "";
    private boolean useShallowClones = false;
    private boolean useSubmodules = false;
    private boolean verboseLogs = false;
    private int commandTimeout = 0;
    private VcsBranch vcsBranch = MASTER_BRANCH;

    private GerritService gerritDAO = null;

    private static final BandanaManager bandanaManager =
        new DefaultBandanaManager(new MemoryBandanaPersister());

    private PlanManager planManager = null;
    private PlanExecutionManager planExeManager = null;
    private ChainBranchManager chainBranchManager = null;
    private RepositoryDefinitionManager repositoryDefinitionManager = null;
    private VariableDefinitionManager variableDefinitionManager = null;

    private EventPublisher eventPublisher = null;

    private I18nResolver i18nResolver;
    private CapabilityContext capabilityContext;
    private SshProxyService sshProxyService;
    private EncryptionService encryptionService;

    private GerritConfig gc = new GerritConfig();

    private GerritChangeVO lastGerritChange = null;

    @Override
    public void init(ModuleDescriptor moduleDescriptor) {
        super.init(moduleDescriptor);

        log.debug("Initialized repository adapter.");

    }

    public void setPlanManager(PlanManager planManager) {
        this.planManager = planManager;
    }

    public void setPlanExeManager(PlanExecutionManager planExeManager) {
        this.planExeManager = planExeManager;
    }

    public ChainBranchManager getChainBranchManager() {
        return chainBranchManager;
    }

    public void setChainBranchManager(ChainBranchManager chainBranchManager) {
        this.chainBranchManager = chainBranchManager;
    }

    public VariableDefinitionManager getVariableDefinitionManager() {
        return variableDefinitionManager;
    }

    public RepositoryDefinitionManager getRepositoryDefinitionManager() {
        return repositoryDefinitionManager;
    }

    public void
                    setRepositoryDefinitionManager(RepositoryDefinitionManager repositoryDefinitionManager) {
        this.repositoryDefinitionManager = repositoryDefinitionManager;
    }

    public void
                    setVariableDefinitionManager(VariableDefinitionManager variableDefinitionManager) {
        this.variableDefinitionManager = variableDefinitionManager;
    }

    public void setEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public BandanaManager getBandanaManager() {
        return bandanaManager;
    }

    @Override
    public void
                    prepareConfigObject(@NotNull BuildConfiguration buildConfiguration) {
        super.prepareConfigObject(buildConfiguration);

        log.debug("Preparing gerrit repository adapter...");

        String strHostName =
            buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
                "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
            strHostName);

        String strPort =
            buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_PORT, "")
                .trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_REPOSITORY_PORT,
            strPort);

        String strProject =
            buildConfiguration.getString(REPOSITORY_GERRIT_PROJECT, "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_PROJECT, strProject);

        String strUserName =
            buildConfiguration.getString(REPOSITORY_GERRIT_USERNAME, "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_USERNAME, strUserName);

        String strPhrase =
            buildConfiguration.getString(TEMPORARY_GERRIT_SSH_PASSPHRASE);
        if (buildConfiguration
            .getBoolean(TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE)) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE,
                encryptionService.encrypt(strPhrase));
        }

        String decryptedKey = "";
        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_KEY_CHANGE)) {
            final Object o =
                buildConfiguration
                    .getProperty(TEMPORARY_GERRIT_SSH_KEY_FROM_FILE);
            if (o instanceof File) {
                File f = (File) o;

                try {
                    decryptedKey = FileUtils.readFileToString(f);
                } catch (IOException e) {
                    log.error(
                        textProvider
                            .getText("repository.gerrit.messages.error.ssh.key.read"),
                        e);
                    return;
                }

                buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY,
                    encryptionService.encrypt(decryptedKey));
            } else {
                buildConfiguration.clearProperty(REPOSITORY_GERRIT_SSH_KEY);
            }
        } else {
            decryptedKey =
                encryptionService.decrypt(buildConfiguration.getString(
                    REPOSITORY_GERRIT_SSH_KEY, ""));
        }

        relativeConfigPath =
            this.getRelativeConfigDirectory(buildConfiguration);

        buildConfiguration.setProperty(REPOSITORY_GERRIT_CONFIG_DIR,
            relativeConfigPath);

        relativeSSHKeyFilePath = getRelativeRepoPath(buildConfiguration);

        File f = prepareSSHKeyFile(relativeSSHKeyFilePath, decryptedKey);

        if (f != null) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE,
                relativeSSHKeyFilePath);
        }

        String strDefBranch =
            buildConfiguration.getString(REPOSITORY_GERRIT_DEFAULT_BRANCH, "");
        String strCustBranch =
            buildConfiguration.getString(REPOSITORY_GERRIT_CUSTOM_BRANCH, "");

        if (strDefBranch.equals(MASTER_BRANCH.getName())
            || strDefBranch.equals(ALL_BRANCH.getName())) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_DEFAULT_BRANCH,
                strDefBranch);
            buildConfiguration.setProperty(REPOSITORY_GERRIT_CUSTOM_BRANCH,
                strDefBranch);

            buildConfiguration.setProperty(REPOSITORY_GERRIT_BRANCH,
                strDefBranch);
        } else {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_DEFAULT_BRANCH,
                CUSTOM_BRANCH_SET);
            buildConfiguration.setProperty(REPOSITORY_GERRIT_CUSTOM_BRANCH,
                strCustBranch);
            buildConfiguration.setProperty(REPOSITORY_GERRIT_BRANCH,
                strCustBranch);
        }
    }

    private String getBaseBuildWorkingDirectory() {
        File parentDirectoryFile =
            this.buildDirectoryManager.getBaseBuildWorkingDirectory();

        String parentDirectory = parentDirectoryFile.getAbsolutePath();

        return parentDirectory;
    }

    private String getRelativePath(BuildConfiguration buildConfiguration) {
        String projectChainKey = "linked";

        String planKey =
            buildConfiguration.getString(REPOSITORY_GERRIT_PLAN_KEY);
        String projectKey =
            buildConfiguration.getString(REPOSITORY_GERRIT_PROJECT_KEY);
        String chainKey =
            buildConfiguration.getString(REPOSITORY_GERRIT_CHAIN_KEY);
        String strProject =
            buildConfiguration.getString(REPOSITORY_GERRIT_PROJECT, "").trim();

        if ((projectKey != null) && (chainKey != null))
            projectChainKey = projectKey + "-" + chainKey;

        if (strProject != null)
            projectChainKey = projectChainKey + File.separator + strProject;

        String workingDirectory = GerritService.SYSTEM_DIRECTORY;

        if (planKey != null) {
            workingDirectory = workingDirectory + File.separator + planKey;
        } else {
            workingDirectory =
                workingDirectory + File.separator + projectChainKey;
        }
        return workingDirectory;
    }

    private String getRelativeRepoPath(BuildConfiguration buildConfiguration) {
        String workingDirectory = getRelativePath(buildConfiguration);

        workingDirectory =
            workingDirectory + File.separator + "GerritSSHKey.txt";

        return workingDirectory.replace("\\", "/");
    }

    private String
                    getRelativeConfigDirectory(BuildConfiguration buildConfiguration) {
        String workingDirectory = getRelativePath(buildConfiguration);

        workingDirectory =
            workingDirectory + File.separator + GerritService.CONFIG_DIRECTORY;

        return workingDirectory.replace("\\", "/");
    }

    public synchronized File prepareConfigDir(String strRelativePath) {
        String filePath =
            getBaseBuildWorkingDirectory() + File.separator + strRelativePath;

        File f = new File(filePath);

        f.setReadable(true, true);
        f.setWritable(true, true);
        f.setExecutable(false, false);

        f.mkdir();

        return f;
    }

    public synchronized File prepareSSHKeyFile(String strRelativePath,
                                               String sshKey) {
        String filePath =
            getBaseBuildWorkingDirectory() + File.separator + strRelativePath;

        File f = new File(filePath);

        f.setReadable(true, true);
        f.setWritable(true, true);
        f.setExecutable(false, false);

        try {
            FileUtils.writeStringToFile(f, sshKey);
        } catch (IOException e) {
            log.error(e.getMessage());
            return null;
        }

        try {
            if (SystemUtils.IS_OS_UNIX || SystemUtils.IS_OS_LINUX
                || SystemUtils.IS_OS_MAC_OSX)
                Runtime.getRuntime().exec("chmod 700 " + filePath);
        } catch (IOException e) {
            log.warn(e.getMessage());
        }

        return f;
    }

    @Override
    public ErrorCollection validate(BuildConfiguration buildConfiguration) {
        boolean error = false;
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        String hostame =
            StringUtils.trim(buildConfiguration
                .getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME));
        if (!StringUtils.isNotBlank(hostame)) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
                "Hostname null!");
            error = true;
        }

        String strPort =
            buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_PORT, "")
                .trim();
        if (!StringUtils.isNotBlank(strPort)) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_PORT,
                "Port null!");
            error = true;
        }

        String strProject =
            buildConfiguration.getString(REPOSITORY_GERRIT_PROJECT, "").trim();

        if (!StringUtils.isNotBlank(strProject)) {
            errorCollection
                .addError(REPOSITORY_GERRIT_PROJECT, "Project null!");
            error = true;
        }

        String username =
            StringUtils.trim(buildConfiguration
                .getString(REPOSITORY_GERRIT_USERNAME));

        if (!StringUtils.isNotBlank(username)) {
            errorCollection.addError(REPOSITORY_GERRIT_USERNAME,
                "Username null!");
            error = true;
        }

        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_KEY_CHANGE)) {
            final Object o =
                buildConfiguration
                    .getProperty(TEMPORARY_GERRIT_SSH_KEY_FROM_FILE);

            if (o == null) {
                errorCollection
                    .addError(
                        REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
                        textProvider
                            .getText("repository.gerrit.messages.error.ssh.key.missing"));
                error = true;
            }
        }

        String key =
            encryptionService.decrypt(buildConfiguration.getString(
                REPOSITORY_GERRIT_SSH_KEY, ""));
        if (!StringUtils.isNotBlank(key)) {
            errorCollection
                .addError(
                    REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
                    textProvider
                        .getText("repository.gerrit.messages.error.ssh.key.missing"));
            error = true;
        }

        String strPhrase;
        if (buildConfiguration
            .getBoolean(TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE)) {
            strPhrase =
                buildConfiguration.getString(TEMPORARY_GERRIT_SSH_PASSPHRASE);
        } else {
            strPhrase =
                buildConfiguration.getString(REPOSITORY_GERRIT_SSH_PASSPHRASE,
                    "");
            if (StringUtils.isNotBlank(strPhrase))
                strPhrase = encryptionService.decrypt(strPhrase);
        }

        String keyFilePath =
            buildConfiguration.getString(REPOSITORY_GERRIT_SSH_KEY_FILE);

        if (!StringUtils.isNotBlank(keyFilePath)) {
            errorCollection.addError(REPOSITORY_GERRIT_SSH_KEY_FILE,
                "Your SSH private key is required for connection!");
            error = true;
        }

        if (error) {
            return errorCollection;
        }

        try {
            ErrorCollection e2 =
                testGerritConnection(keyFilePath, key, hostame,
                    Integer.valueOf(strPort), username, strProject, strPhrase);
            errorCollection.addErrorCollection(e2);
        } catch (RepositoryException e) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
                e.getMessage());
        }

        return errorCollection;
    }

    @Override
    public void populateFromConfig(HierarchicalConfiguration config) {
        super.populateFromConfig(config);

        hostname =
            StringUtils.trimToEmpty(config
                .getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME));
        username = config.getString(REPOSITORY_GERRIT_USERNAME);
        userEmail = config.getString(REPOSITORY_GERRIT_EMAIL);
        sshKey = config.getString(REPOSITORY_GERRIT_SSH_KEY, "");
        sshPassphrase =
            encryptionService.decrypt(config
                .getString(REPOSITORY_GERRIT_SSH_PASSPHRASE));
        port = config.getInt(REPOSITORY_GERRIT_REPOSITORY_PORT, 29418);
        project = config.getString(REPOSITORY_GERRIT_PROJECT);

        String strDefBranch =
            config.getString(REPOSITORY_GERRIT_DEFAULT_BRANCH, "");
        String strCustBranch =
            config.getString(REPOSITORY_GERRIT_CUSTOM_BRANCH, "");

        if (strDefBranch.equals(MASTER_BRANCH.getName())
            || strDefBranch.equals(ALL_BRANCH.getName())) {
            vcsBranch = new VcsBranchImpl(strDefBranch);
        } else {
            vcsBranch = new VcsBranchImpl(strCustBranch);
        }

        useShallowClones =
            config.getBoolean(REPOSITORY_GERRIT_USE_SHALLOW_CLONES);
        useSubmodules = config.getBoolean(REPOSITORY_GERRIT_USE_SUBMODULES);
        commandTimeout =
            config.getInt(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
                DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        verboseLogs = config.getBoolean(REPOSITORY_GERRIT_VERBOSE_LOGS, false);

        String gitRepoUrl =
            "ssh://" + username + "@" + hostname + ":" + port + "/" + project;

        String tmpCP = config.getString(REPOSITORY_GERRIT_CONFIG_DIR);

        if (tmpCP == null || tmpCP.isEmpty()) {
            tmpCP =
                GerritService.SYSTEM_DIRECTORY + File.separator
                    + GerritService.CONFIG_DIRECTORY;
        }

        relativeConfigPath = tmpCP.replace("\\", "/");

        absConfigPath = prepareConfigDir(relativeConfigPath).getAbsolutePath();

        String tmpSSHKFP = config.getString(REPOSITORY_GERRIT_SSH_KEY_FILE);

        if (tmpSSHKFP == null || tmpSSHKFP.isEmpty()) {
            tmpSSHKFP =
                GerritService.SYSTEM_DIRECTORY + File.separator
                    + GerritService.CONFIG_DIRECTORY;
        }

        relativeSSHKeyFilePath = tmpSSHKFP.replace("\\", "/");

        String decryptedKey = encryptionService.decrypt(sshKey);

        sshKeyFile = prepareSSHKeyFile(relativeSSHKeyFilePath, decryptedKey);

        gc.setHost(hostname);
        gc.setPort(port);
        gc.setRepositoryUrl(gitRepoUrl);
        gc.setWorkingDirectory(absConfigPath);
        gc.setSshKeyFile(sshKeyFile);
        gc.setSshKey(decryptedKey);
        gc.setSshPassphrase(sshPassphrase);
        gc.setUsername(username);
        gc.setUserEmail(userEmail);
        gc.setUseShallowClones(useShallowClones);
        gc.setUseSubmodules(useSubmodules);
        gc.setVerboseLogs(verboseLogs);
        gc.setCommandTimeout(commandTimeout);

        try {
            initializeGerritService();
            if (this.isOnLocalAgent()) {
                if (isRemoteTriggeringReop()) {
                    getGerritDAO().addListener(this);
                } else {
                    getGerritDAO().removeListener(this);
                }
            }
        } catch (RepositoryException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public HierarchicalConfiguration toConfiguration() {
        HierarchicalConfiguration configuration = super.toConfiguration();

        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
            hostname);
        configuration.setProperty(REPOSITORY_GERRIT_USERNAME, username);
        configuration.setProperty(REPOSITORY_GERRIT_EMAIL, userEmail);
        configuration.setProperty(REPOSITORY_GERRIT_PROJECT, project);

        if (!vcsBranch.equals(MASTER_BRANCH) && !vcsBranch.equals(ALL_BRANCH)) {
            configuration.setProperty(REPOSITORY_GERRIT_DEFAULT_BRANCH,
                CUSTOM_BRANCH_SET);
            configuration.setProperty(REPOSITORY_GERRIT_CUSTOM_BRANCH,
                vcsBranch.getName());
        } else {
            String br = vcsBranch.getName();

            configuration.setProperty(REPOSITORY_GERRIT_DEFAULT_BRANCH, br);
            configuration.setProperty(REPOSITORY_GERRIT_CUSTOM_BRANCH, br);
        }

        configuration
            .setProperty(REPOSITORY_GERRIT_BRANCH, vcsBranch.getName());

        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY, sshKey);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE,
            encryptionService.encrypt(sshPassphrase));
        configuration.setProperty(REPOSITORY_GERRIT_CONFIG_DIR,
            relativeConfigPath);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE,
            relativeSSHKeyFilePath);
        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_PORT, port);

        configuration.setProperty(REPOSITORY_GERRIT_USE_SHALLOW_CLONES,
            useShallowClones);
        configuration.setProperty(REPOSITORY_GERRIT_USE_SUBMODULES,
            useSubmodules);
        configuration.setProperty(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
            commandTimeout);
        configuration.setProperty(REPOSITORY_GERRIT_VERBOSE_LOGS, verboseLogs);

        return configuration;
    }

    private void initializeGerritService() throws RepositoryException {
        getGerritDAO().initialize();

        String strVersion = getGerritDAO().getGerritVersion();

        log.info(String.format("Gerrit Version: %s", strVersion));

        userEmail = getGerritDAO().getGerritSystemUserEmail();
    }

    @Override
    public void
                    setBuildDirectoryManager(BuildDirectoryManager buildDirectoryManager) {
        super.setBuildDirectoryManager(buildDirectoryManager);
    }

    @Override
    public void setBuildLoggerManager(BuildLoggerManager buildLoggerManager) {
        super.setBuildLoggerManager(buildLoggerManager);
    }

    @Override
    public void
                    setCustomVariableContext(CustomVariableContext customVariableContext) {
        super.setCustomVariableContext(customVariableContext);
    }

    public void setCapabilityContext(final CapabilityContext capabilityContext) {
        this.capabilityContext = capabilityContext;
    }

    @Override
    public void setTemplateRenderer(TemplateRenderer templateRenderer) {
        super.setTemplateRenderer(templateRenderer);
    }

    @Override
    public synchronized void setTextProvider(TextProvider textProvider) {
        super.setTextProvider(textProvider);
    }

    public void setSshProxyService(SshProxyService sshProxyService) {
        this.sshProxyService = sshProxyService;
    }

    public ErrorCollection
                    testGerritConnection(String sshRelKeyFile, String key,
                                         String strHost, int port,
                                         String strUsername, String strProject,
                                         String phrase) throws RepositoryException {
        ErrorCollection errorCollection = new SimpleErrorCollection();

        File f = prepareSSHKeyFile(sshRelKeyFile, key);

        if (!f.isFile()) {
            throw new RepositoryException(
                textProvider
                    .getText("repository.gerrit.messages.error.ssh.key.missing"));
        }

        String gitRepoUrl =
            "ssh://" + username + "@" + strHost + ":" + port + "/" + strProject;

        gc.setHost(strHost);
        gc.setPort(port);
        gc.setRepositoryUrl(gitRepoUrl);
        gc.setSshKeyFile(f);
        gc.setSshKey(key);
        gc.setSshPassphrase(phrase);
        gc.setUsername(strUsername);

        GerritService gerrit = new GerritService(gc);

        try {
            gerrit.testGerritConnection();

            if (!gerrit.isGerritProject(strProject))
                errorCollection.addError(REPOSITORY_GERRIT_PROJECT,
                    "Project doesn't exist!");
        } catch (Exception e) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
                e.getMessage());
        }

        return errorCollection;
    }

    public GerritService getGerritDAO() {
        if (gerritDAO == null) {
            log.debug("SSH-KEY-FILE=" + sshKeyFile);

            gerritDAO = new GerritService(gc);
        }

        return gerritDAO;
    }

    public GerritConfig getGerritConfig() {
        return gc;
    }

    protected class GerritBandanaContext implements BambooBandanaContext {

        private static final long serialVersionUID = 2823839939046273111L;

        private long planID = 639917L;

        @Override
        public boolean hasParentContext() {
            return false;
        }

        @Override
        public BambooBandanaContext getParentContext() {
            return null;
        }

        @Override
        public long getPlanId() {
            return planID;
        }

        @Override
        public String getPluginKey() {
            return moduleDescriptor.getPluginKey();
        }
    }

    public BuildLoggerManager getBuildLoggerManager() {
        return buildLoggerManager;
    }

    @Override
    public BuildRepositoryChanges
                    collectChangesSinceLastBuild(String planKey,
                                                 String lastVcsRevisionKey) throws RepositoryException {
        final BuildLogger buildLogger =
            buildLoggerManager.getLogger(PlanKeys.getPlanKey(planKey));
        List<Commit> commits = new ArrayList<Commit>();
        GerritChangeVO change = null;

        if (this.getVcsBranch().equals(ALL_BRANCH)) {
            change = getGerritDAO().getLastUnverifiedChange(project);
            if (change == null) {
                change = getGerritDAO().getLastChange(project);
            }
        } else {
            change =
                getGerritDAO().getLastUnverifiedChange(project,
                    this.getVcsBranch().getName());
            if (change == null) {
                change =
                    getGerritDAO().getLastChange(project,
                        this.getVcsBranch().getName());
            }
        }

        if ((change == null)
            && ((lastVcsRevisionKey == null) || lastVcsRevisionKey.isEmpty())) {
            throw new RepositoryException(
                textProvider
                    .getText("processor.gerrit.messages.build.error.nochanges"));
        } else {
            log.debug(String.format("collectChangesSinceLastBuild: %s, %s, %s",
                change.getBranch(), change.getId(), change.getCurrentPatchSet()
                    .getRef()));

            buildLogger.addBuildLogEntry(textProvider
                .getText("repository.gerrit.messages.ccRecover.completed"));

            if (lastVcsRevisionKey == null) {
                buildLogger.addBuildLogEntry(textProvider.getText(
                    "repository.gerrit.messages.ccRepositoryNeverChecked",
                    Arrays.asList(change.getLastRevision())));
            } else if (change.getLastRevision().equals(lastVcsRevisionKey)) {
                // Time is unreliable as comments change the last update field.
                // We need to track by last patchset revision
                Object lastRevForChange =
                    bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, change.getId());
                if ((lastRevForChange != null)
                    && lastRevForChange.equals(change.getLastRevision()))
                    return new BuildRepositoryChangesImpl(
                        change.getLastRevision());
            }
        }

        commits.add(convertChangeToCommit(change, true));

        BuildRepositoryChanges buildChanges =
            new BuildRepositoryChangesImpl(change.getLastRevision(), commits);

        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
            change.getId(), change.getLastRevision());

        return buildChanges;
    }

    protected CommitImpl convertChangeToCommit(GerritChangeVO change,
                                               boolean useLast) {
        CommitImpl commit = new CommitImpl();

        PatchSet patch;

        if (useLast)
            patch = change.getCurrentPatchSet();
        else
            patch = change.getPatchSets().iterator().next();

        commit.setComment(change.getSubject());

        String author = patch.getAuthorName();

        if (author == null || author.isEmpty())
            author = change.getOwnerName();

        commit.setAuthor(new AuthorCachingFacade(author));
        commit.setDate(change.getCreatedOn());
        commit.setChangeSetId(patch.getRevision());
        commit.setCreationDate(change.getCreatedOn());
        commit.setLastModificationDate(change.getLastUpdate());

        Set<FileSet> fileSets = patch.getFileSets();

        for (FileSet fileSet : fileSets) {
            if (!fileSet.getFile().equals(GIT_COMMIT_ACTION)) {
                CommitFile file =
                    new CommitFileImpl(patch.getRevision(), fileSet.getFile());
                commit.addFile(file);
            }
        }

        return commit;
    }

    @Override
    public boolean isRepositoryDifferent(@NotNull Repository repository) {
        if (repository instanceof GerritRepositoryAdapter) {
            GerritRepositoryAdapter gitRepo =
                (GerritRepositoryAdapter) repository;
            return !new EqualsBuilder()
                .append(gc.getRepositoryUrl(), gitRepo.gc.getRepositoryUrl())
                .append(this.getProject(), gitRepo.getProject())
                .append(this.getVcsBranch(), gitRepo.getVcsBranch())
                .append(gc.getUsername(), gitRepo.gc.getUsername())
                .append(gc.getSshKey(), gitRepo.gc.getSshKey()).isEquals();
        } else {
            return true;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Repository)
            return !isRepositoryDifferent((Repository) obj);

        return false;
    }

    @Override
    public void addDefaultValues(BuildConfiguration buildConfiguration) {
        buildConfiguration.setProperty(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
            String.valueOf(DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));
        buildConfiguration.clearTree(REPOSITORY_GERRIT_VERBOSE_LOGS);
        buildConfiguration.setProperty(REPOSITORY_GERRIT_USE_SHALLOW_CLONES,
            true);
        buildConfiguration.clearTree(REPOSITORY_GERRIT_USE_SUBMODULES);
    }

    @Override
    public String getHost() {
        return "";
    }

    public int getPort() {
        return port;
    }

    public String getHostname() {
        return hostname;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String getGerritEMail() {
        return userEmail;
    }

    @Override
    public String getName() {
        return textProvider.getText("repository.gerrit.name");
    }

    public String getProject() {
        return project;
    }

    // @NotNull
    // @Override
    // public Iterable<Long> getSharedCredentialIds() {
    // final Long sharedCredentialsId = accessData.getSharedCredentialsId();
    // return sharedCredentialsId != null ? ImmutableList
    // .of(sharedCredentialsId) : Collections.<Long> emptyList();
    // }
    //
    // public void
    // setCredentialsAccessor(final CredentialsAccessor credentialsAccessor) {
    // this.credentialsAccessor = credentialsAccessor;
    // }

    @Override
    @NotNull
    public VcsBranch getVcsBranch() {
        return vcsBranch;
    }

    @Override
    public void setVcsBranch(@NotNull final VcsBranch vcsBranch) {
        this.vcsBranch = vcsBranch;
    }

    private boolean isOnLocalAgent() {
        return !(buildDirectoryManager instanceof RemoteBuildDirectoryManager);
    }

    @Override
    @NotNull
    public String
                    retrieveSourceCode(@NotNull BuildContext buildContext,
                                       String vcsRevisionKey,
                                       File sourceDirectory) throws RepositoryException {
        return retrieveSourceCode(buildContext, vcsRevisionKey,
            sourceDirectory, 1);
    }

    @Override
    @NotNull
    public String
                    retrieveSourceCode(@NotNull BuildContext buildContext,
                                       String vcsRevisionKey,
                                       File sourceDirectory, int depth) throws RepositoryException {

        final boolean doShallowFetch =
            USE_SHALLOW_CLONES && gc.isUseShallowClones() && depth == 1
                && !isOnLocalAgent();

        lastGerritChange = null;

        GerritChangeVO change =
            this.getGerritDAO().getChangeByRevision(vcsRevisionKey);

        if (change == null) {
            throw new RepositoryException(
                textProvider
                    .getText("repository.gerrit.messages.error.retrieve"));
        }

        log.debug(String.format("Retrieving Source for Change: %s, %s, %s",
            change.getBranch(), change.getId(), change.getCurrentPatchSet()
                .getRef()));

        lastGerritChange = change;

        vcsRevisionKey = change.getCurrentPatchSet().getRef();

        log.debug(String.format("getVcsBranch()=%s", getVcsBranch().getName()));

        JGitRepository jgitRepo = new JGitRepository();

        jgitRepo.setAccessData(gc);

        jgitRepo.open(sourceDirectory);

        jgitRepo.openSSHTransport();

        jgitRepo.fetch(vcsRevisionKey);

        jgitRepo.checkout(vcsRevisionKey);

        jgitRepo.close();

        return change.getLastRevision();
    }

    @Override
    public boolean isMergingSupported() {
        return true;
    }

    @Override
    public boolean
                    mergeWorkspaceWith(@NotNull final BuildContext buildContext,
                                       @NotNull final File file,
                                       @NotNull final String s) throws RepositoryException {
        JGitRepository jgitRepo = new JGitRepository();

        jgitRepo.setAccessData(gc);

        jgitRepo.open(file);

        jgitRepo.openSSHTransport();

        jgitRepo.fetch(s);

        jgitRepo.merge(s);

        return false;
    }

    @Override
    public File getGerritAuthKeyFile() {
        return this.sshKeyFile;
    }

    @Override
    public String getGerritAuthKeyFilePassword() {
        return sshPassphrase;
    }

    @Override
    public Authentication getGerritAuthentication() {
        return new Authentication(this.sshKeyFile, username, sshPassphrase);
    }

    @Override
    public String getGerritHostName() {
        return this.getHostname();
    }

    @Override
    public int getGerritSshPort() {
        return port;
    }

    @Override
    public String getGerritUserName() {
        return username;
    }

    @Override
    public int getWatchdogTimeoutSeconds() {
        return 0;
    }

    @Override
    public WatchTimeExceptionData getExceptionData() {
        return null;
    }

    @Override
    public String getGerritProxy() {
        return null;
    }

    @Override
    public int getNumberOfReceivingWorkerThreads() {
        return 2;
    }

    @Override
    public int getNumberOfSendingWorkerThreads() {
        return 2;
    }

    @Override
    public void
                    pushRevision(@NotNull final File file,
                                 @Nullable final String s) throws RepositoryException {
        JGitRepository jgitRepo = new JGitRepository();

        jgitRepo.setAccessData(gc);

        jgitRepo.open(file, true);

        jgitRepo.openSSHTransport();

        jgitRepo.push(file, s);
    }

    @NotNull
    @Override
    public String
                    commit(@NotNull final File file, @NotNull final String s) throws RepositoryException {
        JGitRepository jgitRepo = new JGitRepository();

        jgitRepo.setAccessData(gc);

        jgitRepo.open(file, true);

        jgitRepo.openSSHTransport();

        jgitRepo.add(".");

        RevCommit c = jgitRepo.commit(s);

        return c.name();
    }

    public void setEncryptionService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    public void setI18nResolver(I18nResolver i18nResolver) {
        this.i18nResolver = i18nResolver;
    }

    @Override
    @NotNull
    public Map<String, String> getCustomVariables() {

        Map<String, String> ret = new HashMap<String, String>();
        if (lastGerritChange != null) {
            ret.put(REPOSITORY_GERRIT_CHANGE_ID, lastGerritChange.getId());
            ret.put(REPOSITORY_GERRIT_CHANGE_NUMBER,
                String.valueOf(lastGerritChange.getNumber()));
            ret.put(REPOSITORY_GERRIT_REVISION_NUMBER,
                lastGerritChange.getLastRevision());
        }
        return ret;
    }

    @NotNull
    public Map<String, String> getPlanRepositoryVariables() {
        Map<String, String> variables = new HashMap<String, String>();
        variables.put(REPOSITORY_URL, gc.getRepositoryUrl());
        variables.put(REPOSITORY_USERNAME, gc.getUsername());
        return variables;
    }

    @Override
    public Set<Requirement> getRequirements() {
        return new HashSet<Requirement>();
    }

    @Override
    public List<VcsBranch>
                    getOpenBranches(String context) throws RepositoryException {
        List<VcsBranch> vcsBranches = new ArrayList<VcsBranch>();
        PlanKey planKey = findFirstPlanKey(false);

        if (planKey != null) {
            JGitRepository jgitRepo = new JGitRepository();

            jgitRepo.setAccessData(gc);

            // PlanHelper.

            jgitRepo.open(this.getSourceCodeDirectory(planKey));

            jgitRepo.openSSHTransport();

            Collection<Ref> branches = jgitRepo.lsRemoteBranches();

            for (Ref b : branches) {
                String strBranch = b.getName();

                if (strBranch.contains("/"))
                    strBranch =
                        strBranch.substring(strBranch.lastIndexOf("/") + 1);

                if (this.getVcsBranch() != null
                    && !this.getVcsBranch().isEqualToBranchWith(strBranch))
                    vcsBranches.add(new VcsBranchImpl(strBranch));
            }

            jgitRepo.close();
        }

        return vcsBranches;
    }

    @Override
    public CommitContext getLastCommit() throws RepositoryException {
        CommitImpl commit = new CommitImpl();

        if (lastGerritChange != null) {
            commit = convertChangeToCommit(lastGerritChange, true);
        }

        return commit;
    }

    @Override
    public CommitContext getFirstCommit() throws RepositoryException {
        CommitImpl commit = new CommitImpl();

        if (lastGerritChange != null) {
            commit = convertChangeToCommit(lastGerritChange, false);
        }

        return commit;
    }

    @Override
    public void
                    createBranch(long repositoryId, String branchName,
                                 BuildContext buildContext) throws RepositoryException {
        System.out.println("BRANCH NAME: " + branchName);
    }

    @Override
    public boolean usePollingForBranchDetection() {
        return true;
    }

    private TriggerRemoteBuild createTriggerRemoteBuild(final String requestIp,
                                                        final String allowedIp) {
        return new TriggerRemoteBuild() {

            @Override
            protected List<String> getRequestIpAddresses() {
                return ImmutableList.of(requestIp);
            }

            protected boolean
                            ipMatchesHost(@NotNull final String testedIp,
                                          @NotNull final String hostName) throws UnknownHostException {
                return getHost().equals(hostName) && allowedIp.equals(testedIp);
            }
        };
    }

    /**
     * Check to see if this repository belongs to a plan.
     * 
     * @param isRemoteTriggeredBy
     * @return
     */
    private PlanKey findFirstPlanKey(boolean isRemoteTriggeredBy) {
        Collection<TopLevelPlan> plans = null;
        PlanKey planKey = null;
        Map<Project, Collection<TopLevelPlan>> projectBuilds =
            planManager.getProjectPlanMap(TopLevelPlan.class, false);

        Iterator<Project> it = projectBuilds.keySet().iterator();

        while (it.hasNext()) {
            Project p = it.next();

            plans = projectBuilds.get(p);

            for (TopLevelPlan pl : plans) {
                if (pl.isSuspendedFromBuilding())
                    continue;

                if (isRepoTriggerFor(pl, isRemoteTriggeredBy))
                    return pl.getPlanKey();

                int branchCount = chainBranchManager.getBranchCount(pl);

                if (branchCount > 0) {
                    List<ChainBranch> chains =
                        chainBranchManager.getBranchesForChain(pl);

                    for (ChainBranch c : chains) {
                        if (isRepoTriggerFor(c, isRemoteTriggeredBy))
                            return c.getPlanKey();
                    }
                }
            }
        }

        return planKey;
    }

    /**
     * Return the frist plan which uses this repository
     * 
     * @param isRemoteTriggeredBy
     * @return
     */
    private ImmutableChain findFirstPlan(boolean isRemoteTriggeredBy) {
        PlanKey planKey = findFirstPlanKey(isRemoteTriggeredBy);

        if (planKey == null)
            return null;

        return planManager.getPlanByKey(planKey, Chain.class);
    }

    /**
     * Determine if plan uses this repository
     * 
     * @param chain
     * @param isRemoteTrigger
     *            , dertmined if plan uses repository for remote use
     * @return
     */
    private boolean isRepoTriggerFor(ImmutableChain chain,
                                     boolean isRemoteTrigger) {
        List<BuildStrategy> strats = chain.getTriggers();
        List<RepositoryDefinition> cRepos =
            new ArrayList<RepositoryDefinition>();

        cRepos = chain.getEffectiveRepositoryDefinitions();

        for (RepositoryDefinition rd : cRepos) {
            if (rd.getName().equals(this.getName())
                && rd.getPluginKey().equals(this.getKey())) {
                HierarchicalConfiguration hconfig = rd.getConfiguration();
                String strDefBranch =
                    hconfig.getString(REPOSITORY_GERRIT_DEFAULT_BRANCH, "");
                String strCustBranch =
                    hconfig.getString(REPOSITORY_GERRIT_CUSTOM_BRANCH, "");

                if (this.getVcsBranch().isEqualToBranchWith(strDefBranch)
                    || this.getVcsBranch().isEqualToBranchWith(strCustBranch)) {

                    if (isRemoteTrigger) {
                        for (BuildStrategy s : strats) {
                            if (s instanceof TriggeredBuildStrategy) {
                                TriggeredBuildStrategy tbs =
                                    Narrow.downTo(s,
                                        TriggeredBuildStrategy.class);

                                Set<Long> repos =
                                    tbs.getTriggeringRepositories();

                                if (repos.contains(rd.getId())) {
                                    return true;
                                } else {
                                    for (Long rID : repos) {
                                        RepositoryDataEntity rde =
                                            repositoryDefinitionManager
                                                .getRepositoryDataEntity(rID);
                                        if (rde.getName()
                                            .equals(this.getName())
                                            && rde.getPluginKey().equals(
                                                this.getKey())) {
                                            return true;
                                        }
                                    }
                                }
                            } else {
                                return false;
                            }
                        }
                    } else {
                        return true;
                    }
                }

            }
        }

        return false;
    }

    private boolean isRemoteTriggeringReop() {
        ImmutableChain c = this.findFirstPlan(true);

        return (c != null);
    }

    private void publishChangeDetectionEvent(ImmutableChain c,
                                             GerritTriggeredEvent e) {
        if (c != null) {
            VcsBranch triggerBranch = this.getVcsBranch();

            if (e instanceof PatchsetCreated) {
                PatchsetCreated ps = (PatchsetCreated) e;
                triggerBranch = new VcsBranchImpl(ps.getChange().getBranch());
                project = ps.getChange().getProject();
            }

            BuildDefinition buildDefinition = c.getBuildDefinition();
            for (BuildStrategy buildStrategy : buildDefinition
                .getBuildStrategies()) {
                TriggeredBuildStrategy tbs =
                    Narrow.downTo(buildStrategy, TriggeredBuildStrategy.class);
                if ((tbs != null) && this.getProject().equals(project)
                    && this.getVcsBranch().equals(triggerBranch)) {
                    eventPublisher.publish(new ChangeDetectionRequiredEvent(
                        this, c.getKey(), tbs.getId(), tbs
                            .getTriggeringRepositories(), tbs
                            .getTriggerConditionsConfiguration()));
                }
            }
        }
    }

    @Override
    public void processGerritEvent(GerritTriggeredEvent e) {
        log.debug("GerritRepository received event.");

        Collection<TopLevelPlan> plans = null;
        Map<Project, Collection<TopLevelPlan>> projectBuilds =
            planManager.getProjectPlanMap(TopLevelPlan.class, false);

        Iterator<Project> it = projectBuilds.keySet().iterator();

        while (it.hasNext()) {
            Project p = it.next();

            plans = projectBuilds.get(p);

            for (TopLevelPlan pl : plans) {
                if (pl.isSuspendedFromBuilding())
                    continue;

                if (isRepoTriggerFor(pl, true)) {
                    publishChangeDetectionEvent(pl, e);
                }

                int branchCount = chainBranchManager.getBranchCount(pl);
                if (branchCount > 0) {
                    List<ChainBranch> chains =
                        chainBranchManager.getBranchesForChain(pl);

                    for (ChainBranch c : chains) {
                        if (isRepoTriggerFor(c, true)) {
                            publishChangeDetectionEvent(c, e);
                        }
                    }
                }
            }
        }
    }
}
