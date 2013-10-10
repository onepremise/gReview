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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.author.AuthorCachingFacade;
import com.atlassian.bamboo.bandana.BambooBandanaContext;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.plugins.git.GitRepositoryAccessData;
import com.atlassian.bamboo.repository.AbstractStandaloneRepository;
import com.atlassian.bamboo.repository.AdvancedConfigurationAwareRepository;
import com.atlassian.bamboo.repository.BranchDetectionCapableRepository;
import com.atlassian.bamboo.repository.BranchMergingAwareRepository;
import com.atlassian.bamboo.repository.CacheDescription;
import com.atlassian.bamboo.repository.CacheHandler;
import com.atlassian.bamboo.repository.CacheId;
import com.atlassian.bamboo.repository.CachingAwareRepository;
import com.atlassian.bamboo.repository.CustomVariableProviderRepository;
import com.atlassian.bamboo.repository.PushCapableRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.remote.RemoteBuildDirectoryManager;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.v2.build.repository.RequirementsAwareRepository;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.bandana.DefaultBandanaManager;
import com.atlassian.bandana.impl.MemoryBandanaPersister;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.sal.api.message.I18nResolver;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;
import com.houghtonassociates.bamboo.plugins.dao.GitRepoFactory;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.opensymphony.xwork.TextProvider;
import com.opensymphony.xwork.ValidationAware;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;

/**
 * This class allows bamboo to use Gerrit as if it were a repository.
 */
public class GerritRepositoryAdapter extends AbstractStandaloneRepository
    implements AdvancedConfigurationAwareRepository, PushCapableRepository,
    BranchMergingAwareRepository, BranchDetectionCapableRepository,
    GerritConnectionConfig2, CustomVariableProviderRepository,
    CustomSourceDirectoryAwareRepository, RequirementsAwareRepository,
    CachingAwareRepository, CacheHandler {

    private static final long serialVersionUID = -3518800283574344591L;

    private static final String REPOSITORY_GERRIT_STORAGE = "repositories";

    private static final String REPOSITORY_GERRIT_PLAN_KEY = "planKey";
    private static final String REPOSITORY_GERRIT_REPO_ID = "repositoryId";
    private static final String REPOSITORY_GERRIT_REPO_DISP_NAME =
        "repositoryName";

    private static final String REPOSITORY_GERRIT_REPOSITORY_HOSTNAME =
        "repository.gerrit.hostname";
    private static final String REPOSITORY_GERRIT_REPOSITORY_PORT =
        "repository.gerrit.port";
    private static final String REPOSITORY_GERRIT_PROJECT =
        "repository.gerrit.project";
    private static final String REPOSITORY_GERRIT_USERNAME =
        "repository.gerrit.username";
    private static final String REPOSITORY_GERRIT_EMAIL =
        "repository.gerrit.email";
    private static final String REPOSITORY_GERRIT_SSH_KEY =
        "repository.gerrit.ssh.key";
    private static final String REPOSITORY_GERRIT_SSH_KEY_FILE =
        "repository.gerrit.ssh.keyfile";
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

    private static final Logger log = Logger
        .getLogger(GerritRepositoryAdapter.class);

    private String hostname;
    private int port = 29418;
    private String project;
    private String username;
    private String userEmail;
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

    private static boolean verifiedLabelAdded = false;

    @Override
    public void init(ModuleDescriptor moduleDescriptor) {
        super.init(moduleDescriptor);

        bandanaManager =
            new DefaultBandanaManager(new MemoryBandanaPersister());

        log.debug("Initialized repository adapter.");
    }

    public BandanaManager getBandanaManager() {
        return bandanaManager;
    }

    @Override
    public void
                    prepareConfigObject(@NotNull BuildConfiguration buildConfiguration) {
        log.debug("Preparing repository adapter...");

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
        String key = "";
        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_KEY_CHANGE)) {
            final Object o =
                buildConfiguration
                    .getProperty(TEMPORARY_GERRIT_SSH_KEY_FROM_FILE);
            if (o instanceof File) {
                File f = (File) o;

                try {
                    key = FileUtils.readFileToString(f);
                } catch (IOException e) {
                    log.error(
                        textProvider
                            .getText("repository.gerrit.messages.error.ssh.key.read"),
                        e);
                    return;
                }

                buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY,
                    encryptionService.encrypt(key));
            } else {
                buildConfiguration.clearProperty(REPOSITORY_GERRIT_SSH_KEY);
            }
        } else if (key.isEmpty()) {
            key =
                encryptionService.decrypt(buildConfiguration.getString(
                    REPOSITORY_GERRIT_SSH_KEY, ""));
        }

        relativeSSHKeyFilePath =
            getRelativeRepoPath(buildConfiguration).replace("\\", "/");

        File f = prepareSSHKeyFile(relativeSSHKeyFilePath, key);

        if (f != null) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE,
                relativeSSHKeyFilePath);
        }
    }

    private String getBaseBuildWorkingDirectory() {
        File parentDirectoryFile =
            this.buildDirectoryManager.getBaseBuildWorkingDirectory();

        String parentDirectory = parentDirectoryFile.getAbsolutePath();

        return parentDirectory;
    }

    private String getRelativeRepoPath(BuildConfiguration buildConfiguration) {
        String planKey =
            buildConfiguration.getString(REPOSITORY_GERRIT_PLAN_KEY);
        String repoDisplayName =
            buildConfiguration.getString(REPOSITORY_GERRIT_REPO_DISP_NAME);

        String workingDirectory = this.getShortKey();

        if (planKey != null) {
            workingDirectory = workingDirectory + File.separator + planKey;
        }

        workingDirectory =
            workingDirectory + File.separator + REPOSITORY_GERRIT_STORAGE;

        if (repoDisplayName != null) {
            workingDirectory =
                workingDirectory + File.separator + repoDisplayName;
        }

        return workingDirectory + File.separator + "GerritSSHKey.txt";
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
                        REPOSITORY_GERRIT_SSH_KEY,
                        textProvider
                            .getText("repository.gerrit.messages.error.ssh.key.missing"));
                error = true;
            }
        }

        String key =
            encryptionService.decrypt(buildConfiguration.getString(
                REPOSITORY_GERRIT_SSH_KEY, ""));
        if (!StringUtils.isNotBlank(key)) {
            errorCollection.addError(REPOSITORY_GERRIT_SSH_KEY, textProvider
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
            testGerritConnection(keyFilePath, key, hostame,
                Integer.valueOf(strPort), username, strProject, strPhrase);
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

        useShallowClones =
            config.getBoolean(REPOSITORY_GERRIT_USE_SHALLOW_CLONES);
        useSubmodules = config.getBoolean(REPOSITORY_GERRIT_USE_SUBMODULES);
        commandTimeout =
            config.getInt(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
                DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        verboseLogs = config.getBoolean(REPOSITORY_GERRIT_VERBOSE_LOGS, false);

        String gitRepoUrl =
            "ssh://" + username + "@" + hostname + ":" + port + "/" + project;

        relativeSSHKeyFilePath =
            config.getString(REPOSITORY_GERRIT_SSH_KEY_FILE).replace("\\", "/");

        String decryptedKey = encryptionService.decrypt(sshKey);

        sshKeyFile = prepareSSHKeyFile(relativeSSHKeyFilePath, decryptedKey);

        GitRepoFactory.configureSSHGitRepository(gitRepository, gitRepoUrl,
            username, "", GitRepoFactory.MASTER_BRANCH, sshKeyFile,
            sshPassphrase, useShallowClones, useSubmodules, commandTimeout,
            verboseLogs, textProvider, i18nResolver, capabilityContext,
            sshProxyService, encryptionService);
    }

    @Override
    public HierarchicalConfiguration toConfiguration() {
        HierarchicalConfiguration configuration = super.toConfiguration();

        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
            hostname);
        configuration.setProperty(REPOSITORY_GERRIT_USERNAME, username);
        configuration.setProperty(REPOSITORY_GERRIT_EMAIL, userEmail);
        configuration.setProperty(REPOSITORY_GERRIT_PROJECT, project);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY, sshKey);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE,
            encryptionService.encrypt(sshPassphrase));
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

    @Override
    public void
                    setBuildDirectoryManager(BuildDirectoryManager buildDirectoryManager) {
        super.setBuildDirectoryManager(buildDirectoryManager);
        gitRepository.setBuildDirectoryManager(buildDirectoryManager);
    }

    @Override
    public void setBuildLoggerManager(BuildLoggerManager buildLoggerManager) {
        super.setBuildLoggerManager(buildLoggerManager);
        gitRepository.setBuildLoggerManager(buildLoggerManager);
    }

    @Override
    public void
                    setCustomVariableContext(CustomVariableContext customVariableContext) {
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
    }

    public void setSshProxyService(SshProxyService sshProxyService) {
        this.sshProxyService = sshProxyService;
        gitRepository.setSshProxyService(sshProxyService);
    }

    public Authentication createGerritCredentials(File sshKeyFile,
                                                  String strUsername,
                                                  String phrase) {
        return new Authentication(sshKeyFile, strUsername, phrase);
    }

    public void testGerritConnection(String sshKeyFile, String key,
                                     String strHost, int port,
                                     String strUsername, String strProject,
                                     String phrase) throws RepositoryException {
        SshConnection sshConnection = null;

        File f = prepareSSHKeyFile(sshKeyFile, key);

        if (!f.isFile()) {
            throw new RepositoryException(
                textProvider
                    .getText("repository.gerrit.messages.error.ssh.key.missing"));
        }

        Authentication auth = new Authentication(f, strUsername, phrase);

        try {
            sshConnection =
                SshConnectionFactory.getConnection(strHost, port, auth);
        } catch (IOException e) {
            throw new RepositoryException(
                textProvider
                    .getText("repository.gerrit.messages.error.connection"));
        }

        if (!sshConnection.isConnected()) {
            throw new RepositoryException(
                textProvider
                    .getText("repository.gerrit.messages.error.connection"));
        } else {
            sshConnection.disconnect();
        }
    }

    public GerritService getGerritDAO() {
        if (gerritDAO == null) {
            log.info("SSH-KEY-FILE=" + sshKeyFile);

            Authentication auth =
                createGerritCredentials(sshKeyFile, username, sshPassphrase);
            gerritDAO = new GerritService(hostname, port, auth);

            String strVersion = gerritDAO.getGerritVersion();

            log.info(String.format("Gerrit Version: %s", strVersion));

            userEmail = gerritDAO.getGerritSystemUserEmail();
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

    public void installVerifyLabel(String planKey) throws RepositoryException {
        final String targetRevision = "refs/meta/config";
        String filePath =
            getBaseBuildWorkingDirectory() + File.separator + "gerrit"
                + File.separator + "repositories" + File.separator
                + "MetaConfig";
        String projectConfig = filePath + File.separator + "project.config";
        String url =
            String.format("ssh://%s@%s:%d/%s", username, this.getHostname(),
                this.getPort(), "All-Projects.git");

        boolean verifiedSectionFound = false;

        Scanner scanner = null;
        FileRepository repository = null;
        Transport transport = null;

        if (verifiedLabelAdded)
            return;

        synchronized (GerritRepositoryAdapter.class) {
            try {

                final GitRepositoryAccessData.Builder accessbuilder =
                    GitRepoFactory.createSubstitutedAccessDataBuilder(
                        encryptionService, gitRepository);

                final GitRepositoryAccessData data = accessbuilder.build();

                File f = new File(filePath, ".git");

                if (f.exists()) {
                    FileUtils.deleteDirectory(f.getParentFile());
                }

                FileRepositoryBuilder builder = new FileRepositoryBuilder();

                repository =
                    builder.setGitDir(f).readEnvironment().findGitDir().setup()
                        .build();
                repository.create();

                SshSessionFactory factory = new JschConfigSessionFactory() {

                    public void configure(Host hc, Session session) {
                        session.setConfig("StrictHostKeyChecking", "no");
                    }

                    @Override
                    protected JSch
                                    getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
                        JSch jsch = super.getJSch(hc, fs);
                        jsch.removeAllIdentity();
                        if (StringUtils.isNotEmpty(data.getSshKey())) {
                            jsch.addIdentity("identityName", data.getSshKey()
                                .getBytes(), null, data.getSshPassphrase()
                                .getBytes());
                        }
                        return jsch;
                    }
                };

                Git git = new Git(repository);

                ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

                Git.init().call();

                transport = Transport.open(git.getRepository(), url);

                ((SshTransport) transport).setSshSessionFactory(factory);

                RefSpec refSpec =
                    new RefSpec().setForceUpdate(true).setSourceDestination(
                        targetRevision, targetRevision);

                transport.fetch(monitor, Arrays.asList(refSpec));
                CheckoutCommand co = git.checkout();
                co.setName(targetRevision);
                co.call();

                StringBuilder content = new StringBuilder();
                File fConfig = new File(projectConfig);
                scanner = new Scanner(fConfig);

                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("[label \"Verified\"]")) {
                        verifiedSectionFound = true;
                        break;
                    }

                    content.append(line).append("\n");

                    if (line.contains("[access \"refs/heads/*\"]")) {
                        content
                            .append("\tlabel-Verified = -1..+1 group Administrators\n");
                    }
                }

                scanner.close();

                if (verifiedSectionFound)
                    return;

                content.append("[label \"Verified\"]\n");
                content.append("\tfunction = MaxWithBlock\n");
                content.append("\tvalue = -1 Fails\n");
                content.append("\tvalue =  0 No score\n");
                content.append("\tvalue = +1 Verified\n");

                File fConfig2 = new File(projectConfig);

                FileUtils.writeStringToFile(fConfig2, content.toString());

                git.add().addFilepattern("project.config").call();
                RevCommit revCommit =
                    git.commit().setMessage("Enabled verification label.")
                        .call();

                RemoteRefUpdate rru =
                    new RemoteRefUpdate(git.getRepository(), revCommit.name(),
                        targetRevision, true, null, null);
                List<RemoteRefUpdate> list = new ArrayList<RemoteRefUpdate>();
                list.add(rru);

                PushResult r = transport.push(monitor, list);

                if (r.getMessages().contains("ERROR")) {
                    throw new RepositoryException(r.getMessages());
                }

                verifiedLabelAdded = true;
            } catch (org.eclipse.jgit.errors.TransportException e) {
                throw new RepositoryException(e);
            } catch (URISyntaxException e) {
                throw new RepositoryException(e);
            } catch (NotSupportedException e) {
                throw new RepositoryException(e);
            } catch (RefAlreadyExistsException e) {
                throw new RepositoryException(e);
            } catch (RefNotFoundException e) {
                throw new RepositoryException(e);
            } catch (InvalidRefNameException e) {
                throw new RepositoryException(e);
            } catch (CheckoutConflictException e) {
                throw new RepositoryException(e);
            } catch (GitAPIException e) {
                throw new RepositoryException(e);
            } catch (FileNotFoundException e) {
                throw new RepositoryException(
                    "Could not locate the project.config! Your checkout must have failed.");
            } catch (IOException e) {
                throw new RepositoryException(e);
            } finally {
                if (scanner != null)
                    scanner.close();
                if (transport != null)
                    transport.close();
                if (repository != null)
                    repository.close();
            }
        }
    }

    @Override
    public BuildRepositoryChanges
                    collectChangesSinceLastBuild(String planKey,
                                                 String lastVcsRevisionKey) throws RepositoryException {

        final BuildLogger buildLogger =
            buildLoggerManager.getBuildLogger(PlanKeys.getPlanKey(planKey));
        List<Commit> commits = new ArrayList<Commit>();

        installVerifyLabel(planKey);

        GerritChangeVO change = getGerritDAO().getLastUnverifiedChange(project);

        if (change == null) {
            change = getGerritDAO().getLastChange(project);
        }

        log.debug(String.format("collectChangesSinceLastBuild: %s, %s, %s",
            change.getBranch(), change.getId(), change.getCurrentPatchSet()
                .getRef()));

        buildLogger.addBuildLogEntry(textProvider
            .getText("repository.gerrit.messages.ccRecover.completed"));

        if ((change == null)
            && ((lastVcsRevisionKey == null) || lastVcsRevisionKey.isEmpty())) {
            throw new RepositoryException(
                textProvider
                    .getText("processor.gerrit.messages.build.error.nochanges"));
        } else if (change == null) {
            buildLogger.addBuildLogEntry(textProvider
                .getText("processor.gerrit.messages.build.verified.None"));
            GitRepoFactory.configureBranchMaster(gitRepository);
            return gitRepository.collectChangesSinceLastBuild(planKey,
                lastVcsRevisionKey);
        } else if (lastVcsRevisionKey == null) {
            buildLogger.addBuildLogEntry(textProvider.getText(
                "repository.gerrit.messages.ccRepositoryNeverChecked",
                Arrays.asList(change.getLastRevision())));
        } else if (change.getLastRevision().equals(lastVcsRevisionKey)) {
            return new BuildRepositoryChangesImpl(change.getLastRevision());
        } else {
            Object lastDate =
                bandanaManager.getValue(new GerritBandanaContext(),
                    GerritChangeVO.JSON_KEY_ID);

            if ((lastDate != null) && lastDate.equals(change.getLastUpdate())) {
                return new BuildRepositoryChangesImpl(change.getLastRevision());
            }
        }

        CommitImpl commit = new CommitImpl();

        commit.setComment(change.getSubject());
        commit.setAuthor(new AuthorCachingFacade(change.getCurrentPatchSet()
            .getAuthorName()));
        commit.setDate(change.getLastUpdate());
        commit.setChangeSetId(change.getLastRevision());
        commit.setCreationDate(change.getCreatedOn());
        commit.setLastModificationDate(change.getLastUpdate());

        Set<FileSet> fileSets = change.getCurrentPatchSet().getFileSets();

        for (FileSet fileSet : fileSets) {
            if (!fileSet.getFile().equals(GIT_COMMIT_ACTION)) {
                CommitFile file =
                    new CommitFileImpl(change.getLastRevision(),
                        fileSet.getFile());
                commit.addFile(file);
            }
        }

        commits.add(commit);

        BuildRepositoryChanges buildChanges =
            new BuildRepositoryChangesImpl(change.getLastRevision(), commits);

        bandanaManager.setValue(new GerritBandanaContext(),
            GerritChangeVO.JSON_KEY_ID, change.getLastUpdate());

        return buildChanges;
    }

    @Override
    public boolean isRepositoryDifferent(Repository repository) {
        if (repository instanceof GerritRepositoryAdapter) {
            GerritRepositoryAdapter gerrit =
                (GerritRepositoryAdapter) repository;
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

    @Override
    @NotNull
    public VcsBranch getVcsBranch() {
        return gitRepository.getVcsBranch();
    }

    @Override
    public void setVcsBranch(@NotNull final VcsBranch vcsBranch) {
        gitRepository.setVcsBranch(vcsBranch);
    }

    public File getCacheDirectory(GitRepositoryAccessData accessData) {
        return gitRepository.getCacheDirectory(accessData);
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

        GerritChangeVO change =
            this.getGerritDAO().getChangeByRevision(vcsRevisionKey);

        log.debug(String.format("Retrieving Source for Change: %s, %s, %s",
            change.getBranch(), change.getId(), change.getCurrentPatchSet()
                .getRef()));

        if (change == null) {
            throw new RepositoryException(
                textProvider
                    .getText("repository.gerrit.messages.error.retrieve"));
        } else if (!isOnLocalAgent()) {
            vcsRevisionKey = change.getCurrentPatchSet().getRef();
        }

        GitRepoFactory.configureBranch(gitRepository, change
            .getCurrentPatchSet().getRef());

        log.debug(String.format("getVcsBranch()=%s", gitRepository
            .getVcsBranch().getName()));

        return gitRepository.retrieveSourceCode(buildContext, vcsRevisionKey,
            sourceDirectory, depth);
    }

    @Override
    public boolean isMergingSupported() {
        return gitRepository.isMergingSupported();
    }

    @Override
    public boolean
                    mergeWorkspaceWith(@NotNull final BuildContext buildContext,
                                       @NotNull final File file,
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
    public int getWatchdogTimeoutSeconds() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public WatchTimeExceptionData getExceptionData() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getGerritProxy() {
        // TODO Auto-generated method stub
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
    public String getBranchIntegrationEditHtml() {
        return gitRepository.getBranchIntegrationEditHtml();
    }

    @Override
    public void
                    pushRevision(@NotNull final File file,
                                 @Nullable final String s) throws RepositoryException {
        gitRepository.pushRevision(file, s);
    }

    @NotNull
    @Override
    public String
                    commit(@NotNull final File file, @NotNull final String s) throws RepositoryException {
        return commit(file, s);
    }

    public void setEncryptionService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        gitRepository.setEncryptionService(encryptionService);
    }

    public void setI18nResolver(I18nResolver i18nResolver) {
        this.i18nResolver = i18nResolver;
        gitRepository.setI18nResolver(i18nResolver);
    }

    @Override
    public Map<String, String> getCustomVariables() {
        return gitRepository.getCustomVariables();
    }

    @Override
    public Set<Requirement> getRequirements() {
        return gitRepository.getRequirements();
    }

    @Override
    public List<VcsBranch>
                    getOpenBranches(String context) throws RepositoryException {
        return gitRepository.getOpenBranches();
    }

    @Override
    public CommitContext getLastCommit() throws RepositoryException {
        return gitRepository.getLastCommit();
    }

    @Override
    public CommitContext getFirstCommit() throws RepositoryException {
        return gitRepository.getFirstCommit();
    }

    @Override
    public CacheId getCacheId(CachableOperation cachableOperation) {
        return gitRepository.getCacheId(cachableOperation);
    }

    @Override
    public boolean isCachingSupportedFor(CachableOperation cachableOperation) {
        return gitRepository.isCachingSupportedFor(cachableOperation);
    }

    @Override
    public String getHandlerDescription() {
        return gitRepository.getHandlerDescription();
    }

    @Override
    public Collection<CacheDescription> getCacheDescriptions() {
        return gitRepository.getCacheDescriptions();
    }

    @Override
    public void deleteCaches(Collection<String> keys, ValidationAware feedback) {
        gitRepository.deleteCaches(keys, feedback);
    }

    @Override
    public void deleteUnusedCaches(ValidationAware feedback) {
        gitRepository.deleteUnusedCaches(feedback);
    }
}
