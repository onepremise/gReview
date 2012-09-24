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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.author.AuthorCachingFacade;
import com.atlassian.bamboo.bandana.BambooBandanaContext;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.repository.AbstractStandaloneRepository;
import com.atlassian.bamboo.repository.AdvancedConfigurationAwareRepository;
import com.atlassian.bamboo.repository.BranchMergingAwareRepository;
import com.atlassian.bamboo.repository.PushCapableRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.bandana.DefaultBandanaManager;
import com.atlassian.bandana.impl.MemoryBandanaPersister;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.util.concurrent.LazyReference;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;
import com.houghtonassociates.bamboo.plugins.dao.GitRepoFactory;
import com.houghtonassociates.bamboo.plugins.utils.I18NUtils;
import com.opensymphony.xwork.TextProvider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;

/**
 * Allows bamboo to use Gerrit as if it were a repository.
 */
public class GerritRepositoryAdapter extends AbstractStandaloneRepository
    implements CustomSourceDirectoryAwareRepository,
    AdvancedConfigurationAwareRepository, PushCapableRepository,
    BranchMergingAwareRepository, GerritConnectionConfig {

    private static final long serialVersionUID = -3518800283574344591L;

    private static final String REPOSITORY_GERRIT_STORAGE = "repositories";

    private static final String REPOSITORY_GERRIT_PLAN_KEY = "planKey";
    private static final String REPOSITORY_GERRIT_REPO_ID = "repositoryId";
    private static final String REPOSITORY_GERRIT_REPO_DISP_NAME = "repositoryName";

    private static final String REPOSITORY_GERRIT_REPOSITORY_HOSTNAME = "repository.gerrit.hostname";
    private static final String REPOSITORY_GERRIT_REPOSITORY_PORT = "repository.gerrit.port";
    private static final String REPOSITORY_GERRIT_PROJECT = "repository.gerrit.project";
    private static final String REPOSITORY_GERRIT_USERNAME = "repository.gerrit.username";
    private static final String REPOSITORY_GERRIT_SSH_KEY = "repository.gerrit.ssh.key";
    private static final String REPOSITORY_GERRIT_SSH_KEY_FILE = "repository.gerrit.ssh.keyfile";
    private static final String REPOSITORY_GERRIT_SSH_PASSPHRASE = "repository.gerrit.ssh.passphrase";
    private static final String TEMPORARY_GERRIT_SSH_PASSPHRASE = "temporary.gerrit.ssh.passphrase";
    private static final String TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE = "temporary.gerrit.ssh.passphrase.change";
    private static final String TEMPORARY_GERRIT_SSH_KEY_FROM_FILE = "temporary.gerrit.ssh.keyfile";
    private static final String TEMPORARY_GERRIT_SSH_KEY_CHANGE = "temporary.gerrit.ssh.key.change";

    private static final String REPOSITORY_GERRIT_USE_SHALLOW_CLONES = "repository.gerrit.useShallowClones";
    private static final String REPOSITORY_GERRIT_USE_SUBMODULES = "repository.gerrit.useSubmodules";
    private static final String REPOSITORY_GERRIT_COMMAND_TIMEOUT = "repository.gerrit.commandTimeout";
    private static final String REPOSITORY_GERRIT_VERBOSE_LOGS = "repository.gerrit.verbose.logs";
    private static final int DEFAULT_COMMAND_TIMEOUT_IN_MINUTES = 180;

    private static final String GIT_COMMIT_ACTION = "/COMMIT_MSG";

    private static final Logger log = Logger.getLogger(GerritRepositoryAdapter.class);

    private String hostname;
    private int port = 29418;
    private String project;
    private String username;
    private String sshKey;
    private String relativeSSHKeyFilePath;
    private File sshKeyFile = null;
    private String sshPassphrase;
    private boolean useShallowClones;
    private boolean useSubmodules;
    private boolean verboseLogs;
    private int commandTimeout;

    private GerritService gerritDAO = null;

    private BandanaManager bandanaManager = null;
	private I18nResolver i18nResolver;
	private CapabilityContext capabilityContext;
	private SshProxyService sshProxyService;
	private EncryptionService encryptionService;

    private GitRepository gitRepository = new GitRepository();

    private final transient LazyReference<StringEncrypter> encrypterRef =
        new LazyReference<StringEncrypter>() {

            @Override
            protected StringEncrypter create() throws Exception {
                return new StringEncrypter();
            }
        };

    @Override
    public void init(ModuleDescriptor moduleDescriptor) {
        super.init(moduleDescriptor);

        bandanaManager =
            new DefaultBandanaManager(new MemoryBandanaPersister());
    }

    public BandanaManager getBandanaManager() {
        return bandanaManager;
    }

    @Override
    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration) {
        String strHostName = buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, strHostName);

        String strPort = buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_PORT, "") .trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_REPOSITORY_PORT, strPort);

        String strProject = buildConfiguration.getString(REPOSITORY_GERRIT_PROJECT, "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_PROJECT, strProject);

        String strUserName = buildConfiguration.getString(REPOSITORY_GERRIT_USERNAME, "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_USERNAME, strUserName);

        String strPhrase = buildConfiguration.getString(TEMPORARY_GERRIT_SSH_PASSPHRASE);
        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE)) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE, encrypterRef.get().encrypt(strPhrase));
        }
        String key = "";
        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_KEY_CHANGE)) {
			final Object o = buildConfiguration .getProperty(TEMPORARY_GERRIT_SSH_KEY_FROM_FILE);
            if (o instanceof File) {
                File f = (File) o;

                try {
                    key = FileUtils.readFileToString(f);
                } catch (IOException e) {
                    log.error(textProvider .getText("repository.gerrit.messages.error.ssh.key.read"), e);
                    return;
                }

                buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY,
                    encrypterRef.get().encrypt(key));
            } else {
                buildConfiguration.clearProperty(REPOSITORY_GERRIT_SSH_KEY);
            }
        } else if (key.isEmpty()) {
            key = encrypterRef.get() .decrypt( buildConfiguration.getString(REPOSITORY_GERRIT_SSH_KEY, ""));
        }

        relativeSSHKeyFilePath = getRelativeRepoPath(buildConfiguration);

        File f = prepareSSHKeyFile(relativeSSHKeyFilePath, key);

        if (f != null) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE, relativeSSHKeyFilePath);
        }
    }

    private String getBaseBuildWorkingDirectory() {
        File parentDirectoryFile = this.buildDirectoryManager.getBaseBuildWorkingDirectory();

        String parentDirectory = parentDirectoryFile.getAbsolutePath();

        return parentDirectory;
    }

    private String getRelativeRepoPath(BuildConfiguration buildConfiguration) {
        String planKey = buildConfiguration.getString(REPOSITORY_GERRIT_PLAN_KEY);
        String repoDisplayName = buildConfiguration.getString(REPOSITORY_GERRIT_REPO_DISP_NAME);

        String workingDirectory = this.getShortKey();

        if (planKey != null) {
            workingDirectory = workingDirectory + File.separator + planKey;
        }

        workingDirectory = workingDirectory + File.separator + REPOSITORY_GERRIT_STORAGE;

        if (repoDisplayName != null) {
            workingDirectory = workingDirectory + File.separator + repoDisplayName;
        }

        return workingDirectory + File.separator + "GerritSSHKey.txt";
    }

    public synchronized File prepareSSHKeyFile(String strRelativePath, String sshKey) {
        String filePath = getBaseBuildWorkingDirectory() + File.separator + strRelativePath;

        File f = new File(filePath);

        try {
            FileUtils.writeStringToFile(f, sshKey);
        } catch (IOException e) {
            log.error(e.getMessage());
            return null;
        }

        return f;
    }

    @Override
    public ErrorCollection validate(BuildConfiguration buildConfiguration) {
        boolean error = false;
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        String hostame = StringUtils.trim(buildConfiguration .getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME));
        if (!StringUtils.isNotBlank(hostame)) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, "Hostname null!");
            error = true;
        }

        String strPort = buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_PORT, "") .trim();
        if (!StringUtils.isNotBlank(strPort)) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_PORT, "Port null!");
            error = true;
        }

        String strProject = buildConfiguration.getString(REPOSITORY_GERRIT_PROJECT, "").trim();

        if (!StringUtils.isNotBlank(strProject)) {
            errorCollection.addError(REPOSITORY_GERRIT_PROJECT, "Project null!");
            error = true;
        }

        String username = StringUtils.trim(buildConfiguration .getString(REPOSITORY_GERRIT_USERNAME));

        if (!StringUtils.isNotBlank(username)) {
            errorCollection.addError(REPOSITORY_GERRIT_USERNAME, "Username null!");
            error = true;
        }

        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_KEY_CHANGE)) {
            final Object o = buildConfiguration .getProperty(TEMPORARY_GERRIT_SSH_KEY_FROM_FILE);

            if (o == null) {
                errorCollection.addError( REPOSITORY_GERRIT_SSH_KEY,
						textProvider.getText("repository.gerrit.messages.error.ssh.key.missing"));
                error = true;
            }
        }

        String key =
            encrypterRef.get().decrypt(
                buildConfiguration.getString(REPOSITORY_GERRIT_SSH_KEY, ""));
        if (!StringUtils.isNotBlank(key)) {
            errorCollection.addError(REPOSITORY_GERRIT_SSH_KEY, textProvider
                .getText("repository.gerrit.messages.error.ssh.key.missing"));
            error = true;
        }

        String strPhrase =
            buildConfiguration.getString(TEMPORARY_GERRIT_SSH_PASSPHRASE);
        if (buildConfiguration
            .getBoolean(TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE)) {
        } else if (strPhrase == null) {
            strPhrase = buildConfiguration.getString(REPOSITORY_GERRIT_SSH_PASSPHRASE, "");
        }

        String keyFilePath = buildConfiguration.getString(REPOSITORY_GERRIT_SSH_KEY_FILE);

        if (!StringUtils.isNotBlank(keyFilePath)) {
			errorCollection.addError(REPOSITORY_GERRIT_SSH_KEY_FILE, "Your SSH private key is required for connection!");
            error = true;
        }

        if (error) {
			return errorCollection;
		}

        try {
            testGerritConnection(keyFilePath, key, hostame, Integer.valueOf(strPort), username, strProject, strPhrase);
        } catch (RepositoryException e) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, e.getMessage());
        }

        return errorCollection;
    }

    @Override
    public void populateFromConfig(HierarchicalConfiguration config) {
        super.populateFromConfig(config);

        hostname = StringUtils.trimToEmpty(config .getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME));
        username = config.getString(REPOSITORY_GERRIT_USERNAME);
        sshKey = config.getString(REPOSITORY_GERRIT_SSH_KEY, "");
        sshPassphrase = encrypterRef.get().decrypt( config.getString(REPOSITORY_GERRIT_SSH_PASSPHRASE));
        port = config.getInt(REPOSITORY_GERRIT_REPOSITORY_PORT, 29418);
        project = config.getString(REPOSITORY_GERRIT_PROJECT);

        useShallowClones = config.getBoolean(REPOSITORY_GERRIT_USE_SHALLOW_CLONES);
        useSubmodules = config.getBoolean(REPOSITORY_GERRIT_USE_SUBMODULES);
        commandTimeout = config.getInt(REPOSITORY_GERRIT_COMMAND_TIMEOUT, DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        verboseLogs = config.getBoolean(REPOSITORY_GERRIT_VERBOSE_LOGS, false);

        String gitRepoUrl = "ssh://" + username + "@" + hostname + ":" + port + "/" + project;

        relativeSSHKeyFilePath = config.getString(REPOSITORY_GERRIT_SSH_KEY_FILE);

        String decryptedKey = encrypterRef.get().decrypt(sshKey);

        sshKeyFile = prepareSSHKeyFile(relativeSSHKeyFilePath, decryptedKey);

        GitRepoFactory.configureSSHGitRepository(gitRepository, gitRepoUrl, username, "",
				GitRepoFactory.MASTER_BRANCH, sshKeyFile, sshPassphrase, useShallowClones,
				useSubmodules, commandTimeout, verboseLogs, textProvider, i18nResolver, capabilityContext,
				sshProxyService, encryptionService);

    }

    @Override
    public HierarchicalConfiguration toConfiguration() {
        HierarchicalConfiguration configuration = super.toConfiguration();

        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, hostname);
        configuration.setProperty(REPOSITORY_GERRIT_USERNAME, username);
        configuration.setProperty(REPOSITORY_GERRIT_PROJECT, project);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY, sshKey);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE, encrypterRef.get().encrypt(sshPassphrase));
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE, relativeSSHKeyFilePath);
        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_PORT, port);

        configuration.setProperty(REPOSITORY_GERRIT_USE_SHALLOW_CLONES, useShallowClones);
        configuration.setProperty(REPOSITORY_GERRIT_USE_SUBMODULES, useSubmodules);
        configuration.setProperty(REPOSITORY_GERRIT_COMMAND_TIMEOUT, commandTimeout);
        configuration.setProperty(REPOSITORY_GERRIT_VERBOSE_LOGS, verboseLogs);

        return configuration;
    }

    @Override
    public void setBuildDirectoryManager(BuildDirectoryManager buildDirectoryManager) {
        super.setBuildDirectoryManager(buildDirectoryManager);
        gitRepository.setBuildDirectoryManager(buildDirectoryManager);
    }

    @Override
    public void setBuildLoggerManager(BuildLoggerManager buildLoggerManager) {
        super.setBuildLoggerManager(buildLoggerManager);
        gitRepository.setBuildLoggerManager(buildLoggerManager);
    }

    @Override
    public void setCustomVariableContext(CustomVariableContext customVariableContext) {
        super.setCustomVariableContext(customVariableContext);
        gitRepository.setCustomVariableContext(customVariableContext);
    }

    public void setCapabilityContext(final CapabilityContext capabilityContext) {
		this.capabilityContext = capabilityContext;
        gitRepository.setCapabilityContext(capabilityContext);
    }

    @Override
    public void setTemplateRenderer(TemplateRenderer templateRenderer) {
        super.setTemplateRenderer(templateRenderer);
        gitRepository.setTemplateRenderer(templateRenderer);
    }

    @Override
    public synchronized void setTextProvider(TextProvider textProvider) {
		super.setTextProvider(textProvider);

		if (this.textProvider != null) {
			I18NUtils.updateTextProvider(this.textProvider, "repository.gerrit.name");
			gitRepository.setTextProvider(this.textProvider);
		}
    }

    public void setSshProxyService(SshProxyService sshProxyService) {
		this.sshProxyService = sshProxyService;
        gitRepository.setSshProxyService(sshProxyService);
    }

    public Authentication createGerritCredentials(File sshKeyFile, String strUsername, String phrase) {
        return new Authentication(sshKeyFile, strUsername, phrase);
    }

    public void testGerritConnection(String sshKeyFile, String key, String strHost, int port,
                                     String strUsername, String strProject, String phrase) throws RepositoryException {
        SshConnection sshConnection = null;

        File f = prepareSSHKeyFile(sshKeyFile, key);

        if (!f.isFile()) {
            throw new RepositoryException(textProvider.getText("repository.gerrit.messages.error.ssh.key.missing"));
        }

        Authentication auth = new Authentication(f, strUsername, phrase);

        try {
            sshConnection = SshConnectionFactory.getConnection(strHost, port, auth);
        } catch (IOException e) {
            throw new RepositoryException(textProvider.getText("repository.gerrit.messages.error.connection"));
        }

        if (!sshConnection.isConnected()) {
            throw new RepositoryException(textProvider .getText("repository.gerrit.messages.error.connection"));
        } else {
            sshConnection.disconnect();
        }
    }

    public GerritService getGerritDAO() {
        if (gerritDAO == null) {
            Authentication auth = createGerritCredentials(sshKeyFile, username, sshPassphrase);
            gerritDAO = new GerritService(hostname, port, auth);
        }

        return gerritDAO;
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
    public BuildRepositoryChanges collectChangesSinceLastBuild(String planKey, String lastVcsRevisionKey)
			throws RepositoryException {

        final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(PlanKeys.getPlanKey(planKey));
        List<Commit> commits = new ArrayList<Commit>();

        GerritChangeVO change = getGerritDAO().getLastUnverifiedChange(project);

		if (change == null) {
			change = getGerritDAO().getLastChange(project);
		}

        buildLogger.addBuildLogEntry(textProvider .getText("repository.gerrit.messages.ccRecover.completed"));

        if ((change == null) && (lastVcsRevisionKey == null)) {
            throw new RepositoryException(textProvider.getText("processor.gerrit.messages.build.error.nochanges"));
        } else if (change == null) {
            buildLogger.addBuildLogEntry(textProvider.getText("processor.gerrit.messages.build.verified.None"));
            GitRepoFactory.configureBranchMaster(gitRepository);
            return gitRepository.collectChangesSinceLastBuild(planKey, lastVcsRevisionKey);
        } else if (lastVcsRevisionKey == null) {
            buildLogger.addBuildLogEntry(textProvider.getText( "repository.gerrit.messages.ccRepositoryNeverChecked",
                Arrays.asList(change.getLastRevision())));
        } else if (change.getLastRevision().equals(lastVcsRevisionKey)) {
            return new BuildRepositoryChangesImpl(change.getLastRevision());
        } else {
            Object lastDate = bandanaManager.getValue(new GerritBandanaContext(), GerritChangeVO.JSON_KEY_ID);

            if ((lastDate != null) && lastDate.equals(change.getLastUpdate())) {
				return new BuildRepositoryChangesImpl(change.getLastRevision());
			}
        }

        CommitImpl commit = new CommitImpl();

        commit.setComment(change.getSubject());
        commit.setAuthor(new AuthorCachingFacade(change.getOwnerName()));
        commit.setDate(change.getLastUpdate());
        commit.setChangeSetId(change.getLastRevision());
        commit.setCreationDate(change.getCreatedOn());
        commit.setLastModificationDate(change.getLastUpdate());

        Set<FileSet> fileSets = change.getCurrentPatchSet().getFileSets();

        for (FileSet fileSet : fileSets) {
            if (!fileSet.getFile().equals(GIT_COMMIT_ACTION)) {
                CommitFile file = new CommitFileImpl(change.getLastRevision(), fileSet.getFile());
                commit.addFile(file);
            }
        }

        commits.add(commit);

        BuildRepositoryChanges buildChanges = new BuildRepositoryChangesImpl(change.getLastRevision(), commits);

        bandanaManager.setValue(new GerritBandanaContext(),
            GerritChangeVO.JSON_KEY_ID, change.getLastUpdate());

        return buildChanges;
    }

    @Override
    public boolean isRepositoryDifferent(Repository repository) {
        if (repository instanceof GerritRepositoryAdapter) {
            GerritRepositoryAdapter gerrit = (GerritRepositoryAdapter) repository;
            return !new EqualsBuilder()
                .append(this.hostname, gerrit.getHostname())
                .append(this.project, gerrit.getProject())
                .append(this.username, gerrit.getUsername()).isEquals();
        } else {
            return false;
        }
    }

    @Override
    public void addDefaultValues(BuildConfiguration buildConfiguration) {
        buildConfiguration.setProperty(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
				String.valueOf(DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));
        buildConfiguration.clearTree(REPOSITORY_GERRIT_VERBOSE_LOGS);
        buildConfiguration.setProperty(REPOSITORY_GERRIT_USE_SHALLOW_CLONES, true);
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
    public String getName() {
        return textProvider.getText("repository.gerrit.name");
    }

    public String getProject() {
        return project;
    }

    @Override
    @NotNull
    public VcsBranch getVcsBranch() {
        return gitRepository.getVcsBranch();
    }

    @Override
    public void setVcsBranch(@NotNull final VcsBranch vcsBranch) {
        gitRepository.setVcsBranch(vcsBranch);
    }

    @Override
    public String retrieveSourceCode(BuildContext buildContext, String vcsRevisionKey, File sourceDirectory)
			throws RepositoryException {

        GerritChangeVO change = this.getGerritDAO().getChangeByRevision(vcsRevisionKey);

        if (change == null) {
            throw new RepositoryException( textProvider .getText("repository.gerrit.messages.error.retrieve"));
        }

        GitRepoFactory.configureBranch(gitRepository, change .getCurrentPatchSet().getRef());

        return gitRepository.retrieveSourceCode(buildContext, vcsRevisionKey, sourceDirectory);
    }

    @Override
    public String retrieveSourceCode(BuildContext buildContext, String vcsRevisionKey, File sourceDirectory, int depth)
			throws RepositoryException {

        GerritChangeVO change = this.getGerritDAO().getChangeByRevision(vcsRevisionKey);

        if (change == null) {
            throw new RepositoryException( textProvider .getText("repository.gerrit.messages.error.retrieve"));
        }

        GitRepoFactory.configureBranch(gitRepository, change .getCurrentPatchSet().getRef());

        return gitRepository.retrieveSourceCode(buildContext, vcsRevisionKey, sourceDirectory, depth);
    }

    @Override
    public boolean isMergingSupported() {
        return true;
    }

    @Override
    public boolean mergeWorkspaceWith(@NotNull final BuildContext buildContext, @NotNull final File file,
			@NotNull final String s) throws RepositoryException {
        return gitRepository.mergeWorkspaceWith(buildContext, file, s);
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
    public int getNumberOfReceivingWorkerThreads() {
        return 2;
    }

    @Override
    public int getNumberOfSendingWorkerThreads() {
        return 2;
    }

    @Override
    public String getBranchIntegrationEditHtml() {
        return gitRepository.getBranchIntegrationEditHtml();
    }

    @Override
    public void pushRevision(@NotNull final File file, @Nullable final String s) throws RepositoryException {
        gitRepository.pushRevision(file, s);
    }

    @NotNull
    @Override
    public String commit(@NotNull final File file, @NotNull final String s) throws RepositoryException {
        return commit(file, s);
    }

    public void setEncryptionService(EncryptionService encryptionService)
    {
		this.encryptionService = encryptionService;
		gitRepository.setEncryptionService(encryptionService);
    }

	public void setI18nResolver(I18nResolver i18nResolver) {
		this.i18nResolver = i18nResolver;
		gitRepository.setI18nResolver(i18nResolver);
	}
}
