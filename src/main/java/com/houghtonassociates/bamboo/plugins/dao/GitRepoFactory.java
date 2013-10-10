/**
 *
 */
package com.houghtonassociates.bamboo.plugins.dao;

import java.io.File;

import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.plugins.git.GitAuthenticationType;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.plugins.git.GitRepositoryAccessData;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.sal.api.message.I18nResolver;
import com.opensymphony.xwork.TextProvider;

/**
 * @author Jason Huntley
 * 
 */
public class GitRepoFactory {

    private static final String REPOSITORY_GIT_NAME = "repository.git.name";
    private static final String REPOSITORY_GIT_REPOSITORY_URL =
        "repository.git.repositoryUrl";
    private static final String REPOSITORY_GIT_AUTHENTICATION_TYPE =
        "repository.git.authenticationType";
    private static final String REPOSITORY_GIT_USERNAME =
        "repository.git.username";
    private static final String REPOSITORY_GIT_PASSWORD =
        "repository.git.password";
    private static final String REPOSITORY_GIT_BRANCH = "repository.git.branch";
    private static final String REPOSITORY_GIT_SSH_KEY =
        "repository.git.ssh.key";
    private static final String REPOSITORY_GIT_SSH_PASSPHRASE =
        "repository.git.ssh.passphrase";
    private static final String REPOSITORY_GIT_USE_SHALLOW_CLONES =
        "repository.git.useShallowClones";
    private static final String REPOSITORY_GIT_USE_SUBMODULES =
        "repository.git.useSubmodules";
    private static final String REPOSITORY_GIT_MAVEN_PATH =
        "repository.git.maven.path";
    private static final String REPOSITORY_GIT_COMMAND_TIMEOUT =
        "repository.git.commandTimeout";
    private static final String REPOSITORY_GIT_VERBOSE_LOGS =
        "repository.git.verbose.logs";
    private static final String TEMPORARY_GIT_PASSWORD =
        "temporary.git.password";
    private static final String TEMPORARY_GIT_PASSWORD_CHANGE =
        "temporary.git.password.change";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE =
        "temporary.git.ssh.passphrase";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE =
        "temporary.git.ssh.passphrase.change";
    private static final String TEMPORARY_GIT_SSH_KEY_FROM_FILE =
        "temporary.git.ssh.keyfile";
    private static final String TEMPORARY_GIT_SSH_KEY_CHANGE =
        "temporary.git.ssh.key.change";

    final static int DEFAULT_COMMAND_TIMEOUT_IN_MINUTES = 180;

    public static final String MASTER_BRANCH = "refs/heads/master";

    public static void
                    configureSSHGitRepository(GitRepository repository,
                                              String url,
                                              String username,
                                              String password,
                                              String branch,
                                              File sshKeyFile,
                                              String sshPassphrase,
                                              boolean useShallowClones,
                                              boolean useSubmodules,
                                              int timeout,
                                              boolean verbose,
                                              TextProvider txtProvider,
                                              I18nResolver i18nResolver,
                                              CapabilityContext capabilityContext,
                                              SshProxyService sshProxyService,
                                              EncryptionService encryptionService) {
        BuildConfiguration buildConfig = new BuildConfiguration();

        buildConfig.setProperty(REPOSITORY_GIT_REPOSITORY_URL, url);

        buildConfig.setProperty(REPOSITORY_GIT_USERNAME, username);
        buildConfig.setProperty(REPOSITORY_GIT_BRANCH, branch);
        buildConfig.setProperty(REPOSITORY_GIT_AUTHENTICATION_TYPE,
            GitAuthenticationType.SSH_KEYPAIR.name());
        buildConfig.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES,
            useShallowClones);
        buildConfig.setProperty(REPOSITORY_GIT_USE_SUBMODULES, useSubmodules);
        buildConfig.setProperty(REPOSITORY_GIT_VERBOSE_LOGS, verbose);

        buildConfig.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT,
            DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        buildConfig.setProperty(TEMPORARY_GIT_PASSWORD, password);
        buildConfig.setProperty(TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE, true);
        buildConfig.setProperty(TEMPORARY_GIT_SSH_PASSPHRASE, sshPassphrase);
        buildConfig.setProperty(TEMPORARY_GIT_SSH_KEY_CHANGE, true);
        buildConfig.setProperty(TEMPORARY_GIT_SSH_KEY_FROM_FILE, sshKeyFile);

        repository.prepareConfigObject(buildConfig);
        repository.populateFromConfig(buildConfig);

        repository.setVcsBranch(new VcsBranchImpl(branch));
        repository.setTextProvider(txtProvider);
        repository.setI18nResolver(i18nResolver);
        repository.setCapabilityContext(capabilityContext);
        repository.setSshProxyService(sshProxyService);
        repository.setEncryptionService(encryptionService);
    }

    public static void configureBranch(GitRepository g, String branch) {
        GitRepositoryAccessData grad = g.getAccessData();
        GitRepositoryAccessData.Builder b =
            GitRepositoryAccessData.builder(grad);

        VcsBranchImpl vcsBranch = new VcsBranchImpl(branch);

        b.branch(vcsBranch);

        g.setVcsBranch(vcsBranch);

        g.setAccessData(b.build());
    }

    public static void configureBranchMaster(GitRepository g) {
        g.setVcsBranch(new VcsBranchImpl(MASTER_BRANCH));
    }

    public static String getRepoProject(GitRepository g) {
        String url = g.getRepositoryUrl();

        return "";
    }

    public static GitRepositoryAccessData.Builder
                    createSubstitutedAccessDataBuilder(EncryptionService encryptionService,
                                                       GitRepository gitRepository) {
        GitRepositoryAccessData grad = gitRepository.getAccessData();
        GitRepositoryAccessData.Builder b =
            GitRepositoryAccessData.builder(grad);

        b.password(encryptionService.decrypt(grad.getPassword()));
        b.sshKey(encryptionService.decrypt(grad.getSshKey()));
        b.sshPassphrase(encryptionService.decrypt(grad.getSshPassphrase()));

        return b;
    }
}
