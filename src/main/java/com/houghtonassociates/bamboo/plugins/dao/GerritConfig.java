package com.houghtonassociates.bamboo.plugins.dao;

import java.io.File;

import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;

public class GerritConfig {

    private String repositoryUrl = "";
    private String host = "";
    private int port = 29418;
    private String proxy = "";
    private String username = "";
    private String password = "";
    private String userEmail = "";
    private File sshKeyFile = null;
    private String workingDirectoryPath = "";
    private String sshKey = "";
    private String sshPassphrase = "";
    private boolean useShallowClones = false;
    private boolean useSubmodules = false;
    private int commandTimeout = 0;
    private boolean verboseLogs = false;

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public File getSshKeyFile() {
        return sshKeyFile;
    }

    public void setSshKeyFile(File sshKeyFile) {
        this.sshKeyFile = sshKeyFile;
    }

    public String getWorkingDirectoryPath() {
        return workingDirectoryPath;
    }

    public void setWorkingDirectory(String workingDirectoryPath) {
        this.workingDirectoryPath = workingDirectoryPath;
    }

    public String getSshKey() {
        return sshKey;
    }

    public void setSshKey(String sshKey) {
        this.sshKey = sshKey;
    }

    public String getSshPassphrase() {
        return sshPassphrase;
    }

    public void setSshPassphrase(String sshPassphrase) {
        this.sshPassphrase = sshPassphrase;
    }

    public boolean isUseShallowClones() {
        return useShallowClones;
    }

    public void setUseShallowClones(boolean useShallowClones) {
        this.useShallowClones = useShallowClones;
    }

    public boolean isUseSubmodules() {
        return useSubmodules;
    }

    public void setUseSubmodules(boolean useSubmodules) {
        this.useSubmodules = useSubmodules;
    }

    public int getCommandTimeout() {
        return commandTimeout;
    }

    public void setCommandTimeout(int commandTimeout) {
        this.commandTimeout = commandTimeout;
    }

    public boolean isVerboseLogs() {
        return verboseLogs;
    }

    public void setVerboseLogs(boolean verboseLogs) {
        this.verboseLogs = verboseLogs;
    }

    public Authentication getAuth() {
        return new Authentication(sshKeyFile, username, sshPassphrase);
    }
}
