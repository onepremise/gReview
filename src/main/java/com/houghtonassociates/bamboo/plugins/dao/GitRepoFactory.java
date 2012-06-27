/**
 * 
 */
package com.houghtonassociates.bamboo.plugins.dao;

import java.io.File;

import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.plugins.git.GitAuthenticationType;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
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

    public static GitRepository
                    createSSHGitRepository(TextProvider txtProvider) {
        GitRepository gitRepository = new GitRepository();

        gitRepository.setTextProvider(txtProvider);

        return gitRepository;
    }

    public static GitRepository
                    createSSHGitRepository(String url, String username,
                                           String password, String branch,
                                           File sshKeyFile,
                                           String sshPassphrase,
                                           boolean useShallowClones,
                                           boolean useSubmodules, int timeout,
                                           boolean verbose,
                                           TextProvider txtProvider) {
        GitRepository gitRepository = new GitRepository();

        configureSSHGitRepository(gitRepository, url, username, password,
            branch, sshKeyFile, sshPassphrase, useShallowClones, useSubmodules,
            timeout, verbose, txtProvider);

        return gitRepository;
    }

    public static void configureSSHGitRepository(GitRepository g, String url,
                                                 String username,
                                                 String password,
                                                 String branch,
                                                 File sshKeyFile,
                                                 String sshPassphrase,
                                                 boolean useShallowClones,
                                                 boolean useSubmodules,
                                                 int timeout, boolean verbose) {
        BuildConfiguration buildConfig = new BuildConfiguration();

        buildConfig.setProperty(REPOSITORY_GIT_REPOSITORY_URL, url);

        buildConfig.setProperty(REPOSITORY_GIT_USERNAME, username);
        buildConfig.setProperty(REPOSITORY_GIT_BRANCH, branch);
        buildConfig.setProperty(REPOSITORY_GIT_AUTHENTICATION_TYPE,
            GitAuthenticationType.SSH_KEYPAIR.name());
        buildConfig.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, new Boolean(
            useShallowClones));
        buildConfig.setProperty(REPOSITORY_GIT_USE_SUBMODULES, new Boolean(
            useSubmodules));
        buildConfig.setProperty(REPOSITORY_GIT_VERBOSE_LOGS, new Boolean(
            verbose));

        buildConfig.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT,
            DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        buildConfig.setProperty(TEMPORARY_GIT_PASSWORD, password);
        buildConfig.setProperty(TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE,
            new Boolean(true));
        buildConfig.setProperty(TEMPORARY_GIT_SSH_PASSPHRASE, sshPassphrase);
        buildConfig
            .setProperty(TEMPORARY_GIT_SSH_KEY_CHANGE, new Boolean(true));
        buildConfig.setProperty(TEMPORARY_GIT_SSH_KEY_FROM_FILE, sshKeyFile);

        g.prepareConfigObject(buildConfig);

        g.populateFromConfig(buildConfig);
    }

    public static void configureSSHGitRepository(GitRepository g, String url,
                                                 String username,
                                                 String password,
                                                 String branch,
                                                 File sshKeyFile,
                                                 String sshPassphrase,
                                                 boolean useShallowClones,
                                                 boolean useSubmodules,
                                                 int timeout, boolean verbose,
                                                 TextProvider txtProvider) {
        BuildConfiguration buildConfig = new BuildConfiguration();

        buildConfig.setProperty(REPOSITORY_GIT_REPOSITORY_URL, url);

        buildConfig.setProperty(REPOSITORY_GIT_USERNAME, username);
        buildConfig.setProperty(REPOSITORY_GIT_BRANCH, branch);
        buildConfig.setProperty(REPOSITORY_GIT_AUTHENTICATION_TYPE,
            GitAuthenticationType.SSH_KEYPAIR.name());
        buildConfig.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, new Boolean(
            useShallowClones));
        buildConfig.setProperty(REPOSITORY_GIT_USE_SUBMODULES, new Boolean(
            useSubmodules));
        buildConfig.setProperty(REPOSITORY_GIT_VERBOSE_LOGS, new Boolean(
            verbose));

        buildConfig.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT,
            DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        buildConfig.setProperty(TEMPORARY_GIT_PASSWORD, password);
        buildConfig.setProperty(TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE,
            new Boolean(true));
        buildConfig.setProperty(TEMPORARY_GIT_SSH_PASSPHRASE, sshPassphrase);
        buildConfig
            .setProperty(TEMPORARY_GIT_SSH_KEY_CHANGE, new Boolean(true));
        buildConfig.setProperty(TEMPORARY_GIT_SSH_KEY_FROM_FILE, sshKeyFile);

        g.prepareConfigObject(buildConfig);
        g.populateFromConfig(buildConfig);

        g.setVcsBranch(new VcsBranchImpl(branch));
        g.setTextProvider(txtProvider);
    }

    public static void configureBranch(GitRepository g, String branch) {
        g.setVcsBranch(new VcsBranchImpl(branch));
    }

    public static void configureBranchMaster(GitRepository g) {
        g.setVcsBranch(new VcsBranchImpl(MASTER_BRANCH));
    }
}
