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

import com.atlassian.bamboo.author.AuthorCachingFacade;
import com.atlassian.bamboo.bandana.BambooBandanaContext;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.repository.AbstractStandaloneRepository;
import com.atlassian.bamboo.repository.BranchDetectionCapableRepository;
import com.atlassian.bamboo.repository.CustomVariableProviderRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.bandana.BandanaManager;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * This class allows bamboo to use Gerrit as if it were a repository.
 */
public class GerritRepositoryAdapter extends AbstractStandaloneRepository
    implements  CustomSourceDirectoryAwareRepository, CustomVariableProviderRepository, BranchDetectionCapableRepository {

    private static final long serialVersionUID = -3518800283574344591L;

    private static final String REPOSITORY_GERRIT_STORAGE = "repositories";

    private static final String REPOSITORY_GERRIT_PLAN_KEY = "planKey";
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

    private static final String REPOSITORY_GERRIT_REBUILD_TIMEOUT =
            "repository.gerrit.rebuildTimeout";
    private static final int DEFAULT_REBUILD_TIMEOUT_IN_MINUTES = 60;

    private static final String GIT_COMMIT_ACTION = "/COMMIT_MSG";

    private static final String REPOSITORY_GERRIT_BRANCH =
            "repository.gerrit.branch";

    private static final String REPOSITORY_GERRIT_DRAFTS =
            "repository.gerrit.drafts";

    private static final String REPOSITORY_GERRIT_CHANGE_ID =
            "repository.gerrit.change.id";

    private static final String REPOSITORY_GERRIT_CHANGE_NUMBER =
            "repository.gerrit.change.number";

    private static final String REPOSITORY_GERRIT_REVISION_NUMBER =
            "repository.gerrit.revision.number";

    private static final Logger log = Logger
        .getLogger(GerritRepositoryAdapter.class);

    private  static final VcsBranch DEFAULT_BRANCH = new VcsBranchImpl("All branches");

    private String hostname;
    private int port = 29418;
    private String project;
    private String username;
    private String sshKey;
    private String relativeSSHKeyFilePath;
    private File sshKeyFile = null;
    private String sshPassphrase;
    private boolean drafts;
    private boolean useShallowClones;
    private boolean useSubmodules;
    private boolean verboseLogs;
    private int commandTimeout;
    private int rebuildTimeout;

    private GerritService gerritDAO = null;

    private BandanaManager bandanaManager = null;
    private EncryptionService encryptionService;
    private PlanManager planManager;
    private GerritChangeVO lastGerritChange = null;
    private VcsBranch vcsBranch;

    @SuppressWarnings("unused")
    public void setBandanaManager(BandanaManager bandanaManager) {
        this.bandanaManager = bandanaManager;
    }

    @SuppressWarnings("unused")
    public void setPlanManager(PlanManager planManager) {
        this.planManager = planManager;
    }

    @SuppressWarnings("unused")
    public void setEncryptionService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration) {
        log.info("Preparing repository adapter...");

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

        String strBranch =
                buildConfiguration.getString(REPOSITORY_GERRIT_BRANCH, "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_BRANCH, strBranch);

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

        relativeSSHKeyFilePath = getRelativeRepoPath(buildConfiguration);

        File f = prepareSSHKeyFile(relativeSSHKeyFilePath, key);

        if (f != null) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE,
                relativeSSHKeyFilePath);
        }
    }

    private String getBaseBuildWorkingDirectory() {
        File parentDirectoryFile =
            this.buildDirectoryManager.getBaseBuildWorkingDirectory();

        return parentDirectoryFile.getAbsolutePath();
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

        try {
            FileUtils.writeStringToFile(f, sshKey);
        } catch (IOException e) {
            log.error(e.getMessage());
            return null;
        }

        return f;
    }

    @NotNull
    @Override
    public ErrorCollection validate(@NotNull BuildConfiguration buildConfiguration) {

        log.info("Validate repository adapter...");

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

        int rebuildTimeout = buildConfiguration.getInt(REPOSITORY_GERRIT_REBUILD_TIMEOUT, DEFAULT_REBUILD_TIMEOUT_IN_MINUTES);
        if (rebuildTimeout <0) {
            error = true;
            errorCollection.addError(REPOSITORY_GERRIT_REBUILD_TIMEOUT, "must be integer greater than zero");
        }

        if (error) {
            return errorCollection;
        }

        try {
            testGerritConnection(keyFilePath, key, hostame,
                Integer.valueOf(strPort), username, strPhrase);
        } catch (RepositoryException e) {
            errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
                e.getMessage());
        }

        return errorCollection;
    }

    @Override
    public void populateFromConfig(@NotNull HierarchicalConfiguration config) {

        log.info("populateFromConfig...");

        super.populateFromConfig(config);

        hostname =
            StringUtils.trimToEmpty(config
                .getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME));
        username = config.getString(REPOSITORY_GERRIT_USERNAME);
        sshKey = config.getString(REPOSITORY_GERRIT_SSH_KEY, "");
        sshPassphrase =
            encryptionService.decrypt(config
                .getString(REPOSITORY_GERRIT_SSH_PASSPHRASE));
        port = config.getInt(REPOSITORY_GERRIT_REPOSITORY_PORT, 29418);
        project = config.getString(REPOSITORY_GERRIT_PROJECT);

        String strBranch = config.getString(REPOSITORY_GERRIT_BRANCH, "");
        if (StringUtils.isEmpty(strBranch)) {
            vcsBranch = DEFAULT_BRANCH;
        } else {
            vcsBranch = new VcsBranchImpl(strBranch);
        }

        drafts = config.getBoolean(REPOSITORY_GERRIT_DRAFTS, false);

        useShallowClones =
            config.getBoolean(REPOSITORY_GERRIT_USE_SHALLOW_CLONES);
        useSubmodules = config.getBoolean(REPOSITORY_GERRIT_USE_SUBMODULES);
        commandTimeout =
            config.getInt(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
                DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        verboseLogs = config.getBoolean(REPOSITORY_GERRIT_VERBOSE_LOGS, false);

        rebuildTimeout = config.getInt(REPOSITORY_GERRIT_REBUILD_TIMEOUT,
                DEFAULT_REBUILD_TIMEOUT_IN_MINUTES);

        relativeSSHKeyFilePath =
            config.getString(REPOSITORY_GERRIT_SSH_KEY_FILE);

        String decryptedKey = encryptionService.decrypt(sshKey);

        sshKeyFile = prepareSSHKeyFile(relativeSSHKeyFilePath, decryptedKey);
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration() {

        HierarchicalConfiguration configuration = super.toConfiguration();

        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME,
            hostname);
        configuration.setProperty(REPOSITORY_GERRIT_USERNAME, username);
        configuration.setProperty(REPOSITORY_GERRIT_PROJECT, project);
        if (DEFAULT_BRANCH.equals(vcsBranch)) {
            configuration.setProperty(REPOSITORY_GERRIT_BRANCH, "");
        } else {
            configuration.setProperty(REPOSITORY_GERRIT_BRANCH, vcsBranch.getName());
        }
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY, sshKey);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE,
            encryptionService.encrypt(sshPassphrase));
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE,
            relativeSSHKeyFilePath);
        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_PORT, port);

        configuration.setProperty(REPOSITORY_GERRIT_DRAFTS,
                drafts);
        configuration.setProperty(REPOSITORY_GERRIT_USE_SHALLOW_CLONES,
            useShallowClones);
        configuration.setProperty(REPOSITORY_GERRIT_USE_SUBMODULES,
            useSubmodules);
        configuration.setProperty(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
            commandTimeout);
        configuration.setProperty(REPOSITORY_GERRIT_REBUILD_TIMEOUT, rebuildTimeout);

        configuration.setProperty(REPOSITORY_GERRIT_VERBOSE_LOGS, verboseLogs);

        return configuration;
    }


    public void testGerritConnection(String sshKeyFile, String key,
                                     String strHost, int port,
                                     String strUsername,
                                     String phrase) throws RepositoryException {
        SshConnection sshConnection;

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
            Authentication auth = new Authentication(sshKeyFile, username, sshPassphrase);
            gerritDAO = new GerritService(hostname, port, auth);
        }

        return gerritDAO;
    }

    protected class GerritBandanaContext implements BambooBandanaContext {

        private static final long serialVersionUID = 2823839939046273111L;

        private long planID = 639917L;

        public GerritBandanaContext(String planKey) {
            Plan plan = planManager.getPlanByKey(PlanKeys.getPlanKey(planKey));
            if (plan != null) {
                planID = plan.getId();
            }
        }

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
            return getClass().getPackage().getName();
        }
    }

    @NotNull
    @Override
    public BuildRepositoryChanges collectChangesSinceLastBuild(@NotNull String planKey,
                                                 String lastVcsRevisionKey) throws RepositoryException {

        String strBranch;
        if (DEFAULT_BRANCH.equals(vcsBranch)) {
            strBranch = null;
        } else {
            strBranch = vcsBranch.getName();
        }

        GerritChangeVO change = getGerritDAO().getFirstUnverifiedChange(project, strBranch, drafts, lastVcsRevisionKey);

        log.info(String.format("[%s] collectChangesSinceLastBuild last unverified is: %s", planKey, change ) );

        if ((change == null) && (lastVcsRevisionKey == null)) { // no waiting review and no last revision

            // disable plan if no history
            Plan plan = planManager.getPlanByKey(PlanKeys.getPlanKey(planKey));
            if (plan != null) {
                plan.setSuspendedFromBuilding(true);
                planManager.savePlan(plan);
            }
            throw new RepositoryException(
                    textProvider.getText("processor.gerrit.messages.build.error.nochanges"));

        }

        if (change == null) { // no unverified changes on gerit - return last revision
            return new BuildRepositoryChangesImpl(lastVcsRevisionKey);
        }

        GerritBandanaContext bandanaContext = new GerritBandanaContext(planKey);
        // clean bandama DB
        for (String k: bandanaManager.getKeys(bandanaContext)) {
            Long v = (Long) bandanaManager.getValue(bandanaContext, k);
            if (v < System.currentTimeMillis() - (rebuildTimeout * 60 * 1000L)) {
                log.info(String.format("[%s] Clean from gReview cache: %s", planKey, k));
                bandanaManager.removeValue(bandanaContext, k);
            }
        }

        Long bandRev = (Long) bandanaManager.getValue(bandanaContext, change.getLastRevision());
        if (bandRev != null) {
            log.info(String.format("[%s] rev %s in cache not run", planKey, change.getLastRevision()));
            return new BuildRepositoryChangesImpl(lastVcsRevisionKey);
        } else {
            bandanaManager.setValue(bandanaContext, change.getLastRevision(), System.currentTimeMillis());
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
                CommitFile file =
                    new CommitFileImpl(change.getLastRevision(),
                        fileSet.getFile());
                commit.addFile(file);
            }
        }

        List<Commit> commits = new ArrayList<Commit>();
        commits.add(commit);

        return new BuildRepositoryChangesImpl(change.getLastRevision(), commits);
    }

    @Override
    public boolean isRepositoryDifferent(@NotNull Repository repository) {
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
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration) {

        buildConfiguration.setProperty(REPOSITORY_GERRIT_COMMAND_TIMEOUT,
            String.valueOf(DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));

        buildConfiguration.setProperty(REPOSITORY_GERRIT_REBUILD_TIMEOUT,
                String.valueOf(DEFAULT_REBUILD_TIMEOUT_IN_MINUTES));

        buildConfiguration.clearTree(REPOSITORY_GERRIT_VERBOSE_LOGS);
        buildConfiguration.setProperty(REPOSITORY_GERRIT_USE_SHALLOW_CLONES,
            true);
        buildConfiguration.clearTree(REPOSITORY_GERRIT_USE_SUBMODULES);
    }

    @Override
    public String getHost() {
        return "";
    }

    public String getHostname() {
        return hostname;
    }

    public String getUsername() {
        return username;
    }

    @NotNull
    @Override
    public String getName() {
        return textProvider.getText("repository.gerrit.name");
    }

    public String getProject() {
        return project;
    }

    @Override
    @NotNull
    public String retrieveSourceCode(@NotNull BuildContext buildContext,
                                       String vcsRevisionKey,
                                       @NotNull File sourceDirectory) throws RepositoryException {


        log.info("retrieveSourceCode.. rev=" + vcsRevisionKey + " in=" + sourceDirectory);

        lastGerritChange = null;

        GerritChangeVO change =
            this.getGerritDAO().getChangeByRevision(vcsRevisionKey);

        if (change != null && !DEFAULT_BRANCH.equals(vcsBranch)) {
            if (!vcsBranch.isEqualToBranchWith(change.getBranch())) {
                log.warn(String.format("retrieveSourceCode wrong branch: %s, should be:%s", change.getBranch(), vcsBranch.getName()));
                change = null;
            }
        }

        if (change == null) {
            throw new RepositoryException(
                textProvider
                    .getText("repository.gerrit.messages.error.retrieve"));
        }


        lastGerritChange = change;

        BuildLogger buildLogger = buildLoggerManager.getBuildLogger(buildContext.getPlanResultKey());
        RefSpec refSpec = new RefSpec().setForceUpdate(true).setSource(change.getCurrentPatchSet().getRef());

        buildLogger.addBuildLogEntry(change.getUrl());

        InitCommand initCommand = Git.init();
        initCommand.setDirectory(sourceDirectory);
        try {
            initCommand.call();
        } catch (Exception e) {
            try {
                buildLogger.addErrorLogEntry("Try clean source directory", e);
                // try clean work directory
                FileUtils.cleanDirectory(sourceDirectory);
                initCommand.call();
            } catch (IOException e1) {
                buildLogger.addErrorLogEntry("", e);
                throw new RepositoryException(e);
            }
        }

        if (verboseLogs) {
            buildLogger.addBuildLogEntry(String.format("Init .git into %s", sourceDirectory));
        }

        Git git;
        org.eclipse.jgit.lib.Repository repository = null;
        Transport transport = null;
        try {
            git = Git.open(sourceDirectory);
            repository = git.getRepository();
            transport = Transport.open(repository,
                    new URIish(String.format("ssh://%s@%s:%d/%s", username, hostname, port, project)));
            ((SshTransport)transport).setSshSessionFactory(new GitSshSessionFactory(encryptionService.decrypt(sshKey), sshPassphrase));
            transport.setTimeout(commandTimeout);

            buildLogger.addBuildLogEntry(String.format("Fetch: %s %s", transport.getURI(), refSpec.getSource()));
            FetchResult fetchResult = transport.fetch(new GitProgressMonitor(buildLogger, verboseLogs),
                    Arrays.asList(refSpec), useShallowClones ? 1 : 0);

            buildLogger.addBuildLogEntry(fetchResult.getMessages());

            if (verboseLogs) {
                buildLogger.addBuildLogEntry("checkout FETCH_HEAD");
            }
            CheckoutCommand checkoutCommand = git.checkout();
            checkoutCommand.setName(Constants.FETCH_HEAD);
            checkoutCommand.call();

        } catch (Exception e) {
            buildLogger.addErrorLogEntry("", e);
            throw new RepositoryException(e);
        } finally {
            if (transport!=null) {
                transport.close();
            }
            if (repository!=null){
                repository.close();
            }
        }


        Map<String,String> customConfiguration = buildContext.getBuildDefinition().getCustomConfiguration();
        boolean gerritVerify = Boolean.parseBoolean(customConfiguration.get("custom.gerrit.run"));
        if (gerritVerify) {
            AdministrationConfiguration config = administrationConfigurationManager.getAdministrationConfiguration();
            String resultsUrl = config.getBaseUrl() + "/browse/" + buildContext.getPlanResultKey().toString();
            getGerritDAO().verifyChange(null, change.getNumber(), change.getCurrentPatchSet().getNumber(),
                    String.format("Bamboo: Build started: %s", resultsUrl));
        }
        return vcsRevisionKey;
    }

    // CustomVariableProviderRepository - implementations

    @Override
    @NotNull
    public Map<String, String> getCustomVariables() {

        Map<String, String> ret = new HashMap<String, String>();
        if (lastGerritChange != null) {
            ret.put(REPOSITORY_GERRIT_BRANCH, lastGerritChange.getBranch());
            ret.put(REPOSITORY_GERRIT_CHANGE_ID, lastGerritChange.getId());
            ret.put(REPOSITORY_GERRIT_CHANGE_NUMBER, String.valueOf(lastGerritChange.getNumber()));
            ret.put(REPOSITORY_GERRIT_REVISION_NUMBER, lastGerritChange.getLastRevision());
        }
        return ret;
    }

    // BranchDetectionCapableRepository - implementations

    @NotNull
    @Override
    public VcsBranch getVcsBranch() {
        return vcsBranch;
    }

    @Override
    public void setVcsBranch(@NotNull VcsBranch vcsBranch) {
        this.vcsBranch = vcsBranch;
    }

    @NotNull
    @Override
    public List<VcsBranch> getOpenBranches(@Nullable String s) throws RepositoryException {
        List<VcsBranch> ret = new ArrayList<VcsBranch>();

        log.info("getOpenBranches");

        String strTempDir = System.getProperty("java.io.tmpdir");
        File tempDir = new File(strTempDir, String.format("openBranchGerritTemp-%d-%d", hashCode(), System.currentTimeMillis()));

        if ( !tempDir.mkdirs() ) {
            throw new RepositoryException(String.format("mkdir fail: %s", tempDir.getAbsolutePath()));
        }

        Git.init().setDirectory(tempDir).call();
        Git git = null;
        org.eclipse.jgit.lib.Repository repository = null;
        Transport transport = null;
        try {
            git = Git.open(tempDir);
            repository = git.getRepository();
            transport = Transport.open(repository,
                    new URIish(String.format("ssh://%s@%s:%d/%s", username, hostname, port, project)));

            ((SshTransport)transport).setSshSessionFactory(new GitSshSessionFactory(encryptionService.decrypt(sshKey), sshPassphrase));
            transport.setTimeout(commandTimeout);
            FetchConnection fetchConnection = transport.openFetch();

            for (Ref ref : fetchConnection.getRefs()) {
                if ( Transport.REFSPEC_PUSH_ALL.matchSource(ref) ) {
                    VcsBranch b = new VcsBranchImpl(ref.getName().substring("refs/heads/".length()));
                    ret.add(b);
                }
            }

        } catch (IOException e) {
            throw new RepositoryException(e);
        } catch (URISyntaxException e) {
            throw new RepositoryException(e);
        } finally {
            if (transport != null) {
                transport.close();
            }
            if (repository != null) {
                repository.close();
            }

            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                log.warn(e.getMessage());
            }
        }

        return ret;
    }

    @Nullable
    @Override
    public CommitContext getLastCommit() throws RepositoryException {
        return null;
    }

    @Nullable
    @Override
    public CommitContext getFirstCommit() throws RepositoryException {
        return null;
    }

}
