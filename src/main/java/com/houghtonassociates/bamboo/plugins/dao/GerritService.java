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
package com.houghtonassociates.bamboo.plugins.dao;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.PushResult;

import com.atlassian.bamboo.repository.RepositoryException;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.Approval;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.PatchSet;
import com.houghtonassociates.bamboo.plugins.dao.jgit.JGitRepository;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.TimeSpan;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;

/**
 * Facade for witing with ssh, gerrit-events, parsing JSON results, and Gerrit
 * related data.
 */
public class GerritService {

    public static final String SYSTEM_DIRECTORY = "gerrit";
    public static final String CONFIG_DIRECTORY = "config";
    private static final Logger log = Logger.getLogger(GerritService.class);

    private GerritConfig gc = new GerritConfig();
    private GerritUserVO gerritSystemUser = null;
    // private GerritHandler gHandler = null;
    private GerritSQLHandler gQueryHandler = null;
    private GerritCmdProcessor cmdProcessor = null;

    // private int watchdogTimeoutMinutes;
    // private WatchTimeExceptionData watchTimeExceptionData;
    private static boolean authorSupported = true;
    private static boolean dbAccessGranted = false;
    private static boolean verifiedLabelAdded = false;
    private boolean isInitialized = false;

    private static GerritMonitor monitor = null;

    private String version = null;

    public GerritService(GerritConfig gc) {
        this.gc = gc;
    }

    public void initialize() throws RepositoryException {
        installVerificationLabel();
        grantDatabaseAccess();

        if ((gc.getUserEmail() == null) || gc.getUserEmail().isEmpty()) {
            GerritUserVO user = getGerritSystemUser();

            if (user != null)
                gc.setUserEmail(user.getEmail());
        }

        if (monitor == null) {
            monitor = new GerritMonitor();
            monitor.initialize(this);
        }

        isInitialized = true;
    }

    public GerritConfig getConfig() {
        return gc;
    }

    public Provider getProvider() {
        Provider p = new Provider();

        p.setHost(gc.getHost());
        p.setName(gc.getUsername());
        p.setPort(Integer.toString(gc.getPort()));
        p.setProto("ssh");
        p.setUrl(gc.getRepositoryUrl());
        p.setVersion(this.getGerritVersion());

        return p;
    }

    public void addListener(GerritProcessListener l) {
        monitor.addGerritListener(l);
    }

    public void removeListener(GerritProcessListener l) {
        monitor.removeGerritListener(l);
    }

    public void testGerritConnection() throws RepositoryException {
        SshConnection sshConnection = null;

        try {
            sshConnection =
                SshConnectionFactory.getConnection(gc.getHost(), gc.getPort(),
                    gc.getAuth());
        } catch (IOException e) {
            throw new RepositoryException(
                "Failed to establish connection to Gerrit!");
        }

        if (!sshConnection.isConnected()) {
            throw new RepositoryException(
                "Failed to establish connection to Gerrit!");
        } else {
            sshConnection.disconnect();
        }
    }

    private class GerritCmdProcessor extends AbstractSendCommandJob {

        protected GerritCmdProcessor(GerritConnectionConfig2 config) {
            super(config);
        }

        @Override
        public void run() {

        }
    }

    private void grantDatabaseAccess() throws RepositoryException {
        final String targetRevision = "refs/meta/config";
        String filePath =
            gc.getWorkingDirectoryPath() + File.separator + "MetaConfig";
        String projectConfig = filePath + File.separator + "project.config";
        String url =
            String.format("ssh://%s@%s:%d/%s", gc.getUsername(), gc.getHost(),
                gc.getPort(), "All-Projects.git");

        boolean accessDBFound = false;

        Scanner scanner = null;
        JGitRepository jgitRepo = new JGitRepository();

        if (dbAccessGranted)
            return;

        synchronized (GerritService.class) {
            try {
                jgitRepo.setAccessData(gc);

                jgitRepo.open(filePath, true);

                jgitRepo.openSSHTransport(url);

                jgitRepo.fetch(targetRevision);

                jgitRepo.checkout(targetRevision);

                StringBuilder content = new StringBuilder();
                File fConfig = new File(projectConfig);
                scanner = new Scanner(fConfig);

                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("accessDatabase = group Administrators")) {
                        accessDBFound = true;
                        break;
                    }

                    content.append(line).append("\n");

                    if (line.contains("[capability]")) {
                        content
                            .append("\taccessDatabase = group Administrators\n");
                    }
                }

                scanner.close();

                if (accessDBFound) {
                    dbAccessGranted = true;
                    return;
                }

                File fConfig2 = new File(projectConfig);

                FileUtils.writeStringToFile(fConfig2, content.toString());

                jgitRepo.add("project.config");

                PushResult r =
                    jgitRepo.commitPush("Grant Database Access.",
                        targetRevision);

                if (r.getMessages().contains("ERROR")) {
                    throw new RepositoryException(r.getMessages());
                }

                dbAccessGranted = true;
            } catch (org.eclipse.jgit.errors.TransportException e) {
                throw new RepositoryException(e);
            } catch (FileNotFoundException e) {
                throw new RepositoryException(
                    "Could not locate the project.config! Your checkout must have failed.");
            } catch (IOException e) {
                throw new RepositoryException(e);
            } finally {
                jgitRepo.close();
                if (scanner != null)
                    scanner.close();
            }
        }
    }

    private void installVerificationLabel() throws RepositoryException {
        final String targetRevision = "refs/meta/config";
        String filePath =
            gc.getWorkingDirectoryPath() + File.separator + "MetaConfig";
        String projectConfig = filePath + File.separator + "project.config";
        String url =
            String.format("ssh://%s@%s:%d/%s", gc.getUsername(), gc.getHost(),
                gc.getPort(), "All-Projects.git");

        boolean verifiedSectionFound = false;

        Scanner scanner = null;
        JGitRepository jgitRepo = new JGitRepository();

        if (verifiedLabelAdded)
            return;

        synchronized (GerritService.class) {
            try {
                jgitRepo.setAccessData(gc);

                jgitRepo.open(filePath, true);

                jgitRepo.openSSHTransport(url);

                jgitRepo.fetch(targetRevision);

                jgitRepo.checkout(targetRevision);

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

                if (verifiedSectionFound) {
                    verifiedLabelAdded = true;
                    return;
                }

                content.append("[label \"Verified\"]\n");
                content.append("\tfunction = MaxWithBlock\n");
                content.append("\tvalue = -1 Fails\n");
                content.append("\tvalue =  0 No score\n");
                content.append("\tvalue = +1 Verified\n");

                File fConfig2 = new File(projectConfig);

                FileUtils.writeStringToFile(fConfig2, content.toString());

                jgitRepo.add("project.config");

                PushResult r =
                    jgitRepo.commitPush("Enabled verification label.",
                        targetRevision);

                if (r.getMessages().contains("ERROR")) {
                    throw new RepositoryException(r.getMessages());
                }

                verifiedLabelAdded = true;
            } catch (org.eclipse.jgit.errors.TransportException e) {
                throw new RepositoryException(e);
            } catch (FileNotFoundException e) {
                throw new RepositoryException(
                    "Could not locate the project.config! Your checkout must have failed.");
            } catch (IOException e) {
                throw new RepositoryException(e);
            } finally {
                jgitRepo.close();
                if (scanner != null)
                    scanner.close();
            }
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean verifyChange(Boolean pass, Integer changeNumber,
                                Integer patchNumber, String message) {
        String command = "";

        if (pass.booleanValue()) {
            command =
                String.format(
                    "gerrit review --message '%s' --verified +1 %s,%s",
                    message, changeNumber.intValue(), patchNumber.intValue());
        } else {
            command =
                String.format(
                    "gerrit review --message '%s' --verified -1 %s,%s",
                    message, changeNumber.intValue(), patchNumber.intValue());
        }

        log.debug("Sending Command: " + command);

        return getGerritCmdProcessor().sendCommand(command);
    }

    private GerritCmdProcessor getGerritCmdProcessor() {
        if (cmdProcessor == null) {
            cmdProcessor =
                new GerritCmdProcessor(new GerritConnectionConfig2() {

                    @Override
                    public File getGerritAuthKeyFile() {
                        return gc.getAuth().getPrivateKeyFile();
                    }

                    @Override
                    public String getGerritAuthKeyFilePassword() {
                        return gc.getAuth().getPrivateKeyFilePassword();
                    }

                    @Override
                    public Authentication getGerritAuthentication() {
                        return gc.getAuth();
                    }

                    @Override
                    public String getGerritHostName() {
                        return gc.getHost();
                    }

                    @Override
                    public int getGerritSshPort() {
                        return gc.getPort();
                    }

                    @Override
                    public String getGerritUserName() {
                        return gc.getUsername();
                    }

                    @Override
                    public String getGerritEMail() {
                        return gc.getUserEmail();
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
                    public int getWatchdogTimeoutSeconds() {
                        // return
                        // (int)TimeUnit.MINUTES.toSeconds(watchdogTimeoutMinutes);
                        return 0;
                    }

                    @Override
                    public WatchTimeExceptionData getExceptionData() {
                        List<Integer> days = new LinkedList<Integer>();
                        List<TimeSpan> exceptionTimes =
                            new LinkedList<TimeSpan>();
                        int[] daysAsInt = new int[] {};
                        daysAsInt =
                            ArrayUtils.toPrimitive(days
                                .toArray(new Integer[days.size()]));

                        return new WatchTimeExceptionData(daysAsInt,
                            exceptionTimes);
                    }

                    @Override
                    public String getGerritProxy() {
                        return gc.getProxy();
                    }
                });
        }

        return cmdProcessor;
    }

    private synchronized GerritSQLHandler getGerritQueryHandler() {
        if (gQueryHandler == null) {
            gQueryHandler =
                new GerritSQLHandler(gc.getHost(), gc.getPort(), gc.getProxy(),
                    gc.getAuth());
        }

        return gQueryHandler;
    }

    public List<String> getProjects() throws RepositoryException {
        List<String> listProjects = new ArrayList<String>();
        String projects =
            getGerritCmdProcessor().sendCommandStr("gerrit ls-projects");

        BufferedReader bufReader =
            new BufferedReader(new StringReader(projects));

        String line = null;

        try {
            while ((line = bufReader.readLine()) != null) {
                listProjects.add(line);
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to list remote projects!");
        }

        return listProjects;
    }

    public Boolean isGerritProject(String project) throws RepositoryException {
        List<String> projects = getProjects();

        for (String p : projects) {
            if (p.contains(project)) {
                return true;
            }
        }

        return false;
    }

    public String getGerritVersion() {
        if (version == null)
            version = getGerritCmdProcessor().sendCommandStr("gerrit version");

        return version;
    }

    /**
     * Must have "Access Database" granted under Global Capabilities in Gerrit
     * 
     * @param userName
     * @return
     * @throws RepositoryException
     */
    public GerritUserVO
                    getUserVOByName(String userName) throws RepositoryException {
        GerritUserVO gerritUserVO = null;

        List<JSONObject> jsonObjects =
            runGerritSQL("select * from account_external_ids");

        for (JSONObject j : jsonObjects) {
            if (j.containsKey("type") && j.getString("type").equals("row")) {
                JSONObject extInfo = j.getJSONObject("columns");
                GerritExtIDVO extVO = transformExtIDObject(extInfo);

                if (extVO.getExternalId().equals(
                    GerritExtIDVO.JSON_KEY_USERNAME + gc.getUsername())) {
                    gerritUserVO = new GerritUserVO();

                    gerritUserVO.setId(extVO.getAccountId());
                    gerritUserVO.setUserName(gc.getUsername());
                    break;
                }
            }
        }

        jsonObjects = runGerritSQL("select * from accounts");

        for (JSONObject j : jsonObjects) {
            if (j.containsKey("type") && j.getString("type").equals("row")) {
                JSONObject userInfo = j.getJSONObject("columns");
                GerritUserVO userVO = transformUserJSONObject(userInfo);

                if (userVO.getId().equals(gerritUserVO.getId())) {
                    gerritUserVO.fill(userVO);
                    break;
                }
            }
        }

        return gerritUserVO;
    }

    /**
     * Must have "Access Database" granted under Global Capabilities in Gerrit
     * 
     * @return
     * @throws RepositoryException
     */
    public GerritUserVO getGerritSystemUser() throws RepositoryException {
        synchronized (GerritService.class) {
            if (gerritSystemUser == null) {
                gerritSystemUser = getUserVOByName(gc.getUsername());
            }
        }

        return gerritSystemUser;
    }

    public String getGerritSystemUserEmail() throws RepositoryException {
        return getGerritSystemUser().getEmail();
    }

    /**
     * Must have "Access Database" granted under Global Capabilities in Gerrit
     * 
     * @param query
     * @return
     * @throws RepositoryException
     */
    public List<JSONObject>
                    runGerritSQL(String query) throws RepositoryException {
        List<JSONObject> jsonObjects = null;

        log.debug("Gerrit query: " + query);

        try {
            jsonObjects = getGerritQueryHandler().querySQL(query);
        } catch (SshException e) {
            throw new RepositoryException("SSH connection error", e);
        } catch (IOException e) {
            throw new RepositoryException(e.getMessage());
        } catch (GerritQueryException e) {
            throw new RepositoryException(e.getMessage());
        }

        if (jsonObjects == null || jsonObjects.isEmpty()) {
            throw new RepositoryException(String.format(
                "ALERT: %s does not have \"Access Database\" capability.",
                gc.getUsername()));
        }

        JSONObject setInfo = jsonObjects.get(jsonObjects.size() - 1);

        int rowCount = setInfo.getInt(GerritChangeVO.JSON_KEY_ROWCOUNT);

        log.debug("Gerrit row count: " + rowCount);

        if (rowCount == 0) {
            log.debug("No JSON content to report.");
            return null;
        } else {
            log.debug("JSON content returned: ");
            log.debug(jsonObjects);
        }

        return jsonObjects;
    }

    public List<JSONObject>
                    runGerritQuery(String query) throws RepositoryException {
        List<JSONObject> jsonObjects = null;

        log.debug("Gerrit query: " + query);

        try {
            jsonObjects =
                getGerritQueryHandler().queryJava(query, true, true, true);
        } catch (SshException e) {
            throw new RepositoryException("SSH connection error", e);
        } catch (IOException e) {
            throw new RepositoryException(e.getMessage());
        } catch (GerritQueryException e) {
            throw new RepositoryException(e.getMessage());
        }

        if (jsonObjects == null || jsonObjects.isEmpty()) {
            return null;
        }

        JSONObject setInfo = jsonObjects.get(jsonObjects.size() - 1);

        int rowCount = setInfo.getInt(GerritChangeVO.JSON_KEY_ROWCOUNT);

        log.debug("Gerrit row count: " + rowCount);

        if (rowCount == 0) {
            log.debug("No JSON content to report.");
            return null;
        } else {
            log.debug("JSON content returned: ");
            log.debug(jsonObjects);
        }

        return jsonObjects;
    }

    public GerritChangeVO getLastChange() throws RepositoryException {
        log.debug("getLastChange()...");

        Set<GerritChangeVO> changes = getGerritChangeInfo();

        TreeSet<GerritChangeVO> treeSet =
            new TreeSet<GerritChangeVO>(new SortByLastUpdate());
        treeSet.addAll(changes);

        if (treeSet.size() > 0)
            return treeSet.first();

        return null;
    }

    public GerritChangeVO getLastUnverifiedChange() throws RepositoryException {
        log.debug("getLastUnverifiedChange()...");

        Set<GerritChangeVO> changes = getGerritChangeInfo();

        TreeSet<GerritChangeVO> treeSet =
            new TreeSet<GerritChangeVO>(new SortByUnVerifiedLastUpdate());
        treeSet.addAll(changes);

        if ((treeSet.size() > 0)
            && (treeSet.first().getVerificationScore() == 0))
            return treeSet.first();

        return null;
    }

    public Set<GerritChangeVO>
                    getLastUnverifiedChanges() throws RepositoryException {
        log.debug("getLastUnverifiedChange()...");

        Set<GerritChangeVO> changes = getGerritChangeInfo();

        ConcurrentSkipListSet<GerritChangeVO> filtedChanges =
            new ConcurrentSkipListSet<GerritChangeVO>(
                new SortByUnVerifiedLastUpdate());
        filtedChanges.addAll(changes);

        if ((filtedChanges.size() > 0)) {
            for (GerritChangeVO c : filtedChanges) {
                if (c.getVerificationScore() > 0) {
                    filtedChanges.remove(c);
                }
            }
        }

        return filtedChanges;
    }

    public GerritChangeVO
                    getLastChange(String project) throws RepositoryException {
        log.debug(String.format("getLastChange(project=%s)...", project));

        Set<GerritChangeVO> changes = getGerritChangeInfo(project);

        TreeSet<GerritChangeVO> treeSet =
            new TreeSet<GerritChangeVO>(new SortByLastUpdate());
        treeSet.addAll(changes);

        if (treeSet.size() > 0)
            return treeSet.first();

        return null;
    }

    public GerritChangeVO
                    getLastUnverifiedChange(String project) throws RepositoryException {
        log.debug(String.format("getLastUnverifiedChange(project=%s)...",
            project));

        Set<GerritChangeVO> changes = getGerritChangeInfo(project);

        TreeSet<GerritChangeVO> treeSet =
            new TreeSet<GerritChangeVO>(new SortByUnVerifiedLastUpdate());
        treeSet.addAll(changes);

        if ((treeSet.size() > 0)
            && (treeSet.first().getVerificationScore() == 0))
            return treeSet.first();

        return null;
    }

    public GerritChangeVO
                    getLastChange(String project, String branch) throws RepositoryException {
        log.debug(String.format("getLastChange(project=%s)...", project));

        Set<GerritChangeVO> changes = getGerritChangeInfo(project, branch);

        TreeSet<GerritChangeVO> treeSet =
            new TreeSet<GerritChangeVO>(new SortByLastUpdate());
        treeSet.addAll(changes);

        if (treeSet.size() > 0)
            return treeSet.first();

        return null;
    }

    public GerritChangeVO
                    getLastUnverifiedChange(String project, String branch) throws RepositoryException {
        log.debug(String.format("getLastUnverifiedChange(project=%s)...",
            project));

        Set<GerritChangeVO> changes = getGerritChangeInfo(project, branch);

        TreeSet<GerritChangeVO> treeSet =
            new TreeSet<GerritChangeVO>(new SortByUnVerifiedLastUpdate());
        treeSet.addAll(changes);

        if ((treeSet.size() > 0)
            && (treeSet.first().getVerificationScore() == 0))
            return treeSet.first();

        return null;
    }

    public GerritChangeVO
                    getChangeByID(String changeID) throws RepositoryException {
        log.debug(String.format("getChangeByID(changeID=%s)...", changeID));

        List<JSONObject> jsonObjects = null;

        jsonObjects = runGerritQuery(String.format("change:%s", changeID));

        if (jsonObjects == null) {
            return null;
        }

        return this.transformChangeJSONObject(jsonObjects.get(0));
    }

    public GerritChangeVO
                    getChangeByRevision(String rev) throws RepositoryException {
        log.debug(String.format("getChangeByRevision(rev=%s)...", rev));

        List<JSONObject> jsonObjects = null;

        jsonObjects = runGerritQuery(String.format("commit:%s", rev));

        if (jsonObjects == null) {
            return null;
        }

        return this.transformChangeJSONObject(jsonObjects.get(0));
    }

    public Set<GerritChangeVO> getGerritChangeInfo() throws RepositoryException {
        log.debug("getGerritChangeInfo()...");

        List<JSONObject> jsonObjects = runGerritQuery("is:open");
        Set<GerritChangeVO> results = new HashSet<GerritChangeVO>(0);

        if (jsonObjects == null) {
            return results;
        }

        log.info("Query result count: " + jsonObjects.size());

        for (JSONObject j : jsonObjects) {
            if (j.containsKey(GerritChangeVO.JSON_KEY_PROJECT)) {
                GerritChangeVO info = transformChangeJSONObject(j);
                results.add(info);
            }
        }

        return results;
    }

    public Set<GerritChangeVO>
                    getGerritChangeInfo(String project) throws RepositoryException {
        String strQuery = String.format("is:open project:%s", project);

        log.debug(String.format("getGerritChangeInfo(project=%s)...", project));

        List<JSONObject> jsonObjects = runGerritQuery(strQuery);
        Set<GerritChangeVO> results = new HashSet<GerritChangeVO>(0);

        if (jsonObjects == null) {
            return results;
        }

        log.info("Query result count: " + jsonObjects.size());

        for (JSONObject j : jsonObjects) {
            if (j.containsKey(GerritChangeVO.JSON_KEY_PROJECT)) {
                GerritChangeVO info = transformChangeJSONObject(j);
                results.add(info);
            }
        }

        return results;
    }

    /**
     * Retrieve recent open changes from Gerrit for a specific project and
     * branch.
     * 
     * @param project
     * @param branch
     * @return
     * @throws RepositoryException
     */
    public Set<GerritChangeVO>
                    getGerritChangeInfo(String project, String branch) throws RepositoryException {
        String strQuery =
            String.format("is:open project:%s branch:%s", project, branch);

        log.debug(String.format(
            "getGerritChangeInfo(project=%s, branch:%s)...", project, branch));

        if (branch == null || branch.isEmpty()) {
            throw new RepositoryException(
                "Invalid branch setting. Please provide a valid branch configuration setting!");
        }

        List<JSONObject> jsonObjects = runGerritQuery(strQuery);
        Set<GerritChangeVO> results = new HashSet<GerritChangeVO>(0);

        if (jsonObjects == null) {
            return results;
        }

        log.info("Query result count: " + jsonObjects.size());

        for (JSONObject j : jsonObjects) {
            if (j.containsKey(GerritChangeVO.JSON_KEY_PROJECT)) {
                GerritChangeVO info = transformChangeJSONObject(j);
                results.add(info);
            }
        }

        return results;
    }

    private class SortByUnVerifiedLastUpdate extends SortByLastUpdate {

        public int compare(GerritChangeVO c1, GerritChangeVO c2) {
            boolean verified1 = (c1.getVerificationScore() != 0);
            boolean verified2 = (c2.getVerificationScore() != 0);

            if (verified1 && verified2) {
                return super.compare(c1, c2);
            } else if (verified1 && !verified2) {
                return 1;
            } else if (!verified1 && verified2) {
                return -1;
            } else if (!verified1 && !verified2) {
                return super.compare(c1, c2);
            }

            return 0;
        }
    }

    private class SortByLastUpdate implements Comparator<GerritChangeVO> {

        public int compare(GerritChangeVO c1, GerritChangeVO c2) {
            Date dt1 = c1.getLastUpdate();
            Date dt2 = c2.getLastUpdate();

            if (dt1.getTime() < dt2.getTime())
                return 1;

            if (dt1.getTime() > dt2.getTime())
                return -1;

            return 0;
        }
    }

    private GerritExtIDVO
                    transformExtIDObject(JSONObject j) throws RepositoryException {
        if (j == null) {
            throw new RepositoryException("No data to parse!");
        }

        log.debug(String.format("transformExtIDObject(j=%s)", j));

        GerritExtIDVO ext = new GerritExtIDVO();

        ext.setAccountId(j.getString(GerritExtIDVO.JSON_KEY_ACCT_ID));

        if (j.containsKey(GerritExtIDVO.JSON_KEY_EMAIL))
            ext.setEmail(j.getString(GerritExtIDVO.JSON_KEY_EMAIL));

        if (j.containsKey(GerritExtIDVO.JSON_KEY_PASSWD))
            ext.setPassword(j.getString(GerritExtIDVO.JSON_KEY_PASSWD));

        ext.setExternalId(j.getString(GerritExtIDVO.JSON_KEY_EXT_ID));

        return ext;
    }

    private GerritUserVO
                    transformUserJSONObject(JSONObject j) throws RepositoryException {
        if (j == null) {
            throw new RepositoryException("No data to parse!");
        }

        log.debug(String.format("transformJSONObject(j=%s)", j));

        GerritUserVO user = new GerritUserVO();

        user.setId(j.getString(GerritUserVO.JSON_KEY_ACCT_ID));
        user.setEmail(j.getString(GerritUserVO.JSON_KEY_EMAIL));

        String test = j.getString(GerritUserVO.JSON_KEY_INACTIVE);

        user.setActive(test.equals("N"));

        // "2013-10-03 10:44:10.908"'
        String regDate = j.getString(GerritUserVO.JSON_KEY_REG_DATE);

        try {
            Date date =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)
                    .parse(regDate);
            user.setRegistrationDate(date);
        } catch (ParseException e) {
            log.debug(e.getMessage());
        }

        return user;
    }

    private GerritChangeVO
                    transformChangeJSONObject(JSONObject j) throws RepositoryException {
        if (j == null) {
            throw new RepositoryException("No data to parse!");
        }

        log.debug(String.format("transformJSONObject(j=%s)", j));

        GerritChangeVO info = new GerritChangeVO();

        info.setProject(j.getString(GerritChangeVO.JSON_KEY_PROJECT));
        info.setBranch(j.getString(GerritChangeVO.JSON_KEY_BRANCH));
        info.setId(j.getString(GerritChangeVO.JSON_KEY_ID));
        info.setNumber(j.getInt(GerritChangeVO.JSON_KEY_NUMBER));
        info.setSubject(j.getString(GerritChangeVO.JSON_KEY_SUBJECT));

        JSONObject owner = j.getJSONObject(GerritChangeVO.JSON_KEY_OWNER);

        if (owner.containsKey(GerritChangeVO.JSON_KEY_NAME))
            info.setOwnerName(owner.getString(GerritChangeVO.JSON_KEY_NAME));

        if (owner.containsKey(GerritChangeVO.JSON_KEY_USERNAME))
            info.setOwnerUserName(owner
                .getString(GerritChangeVO.JSON_KEY_USERNAME));

        info.setOwnerEmail(owner.getString(GerritChangeVO.JSON_KEY_EMAIL));

        info.setUrl(j.getString(GerritChangeVO.JSON_KEY_URL));

        Integer createdOne = j.getInt(GerritChangeVO.JSON_KEY_CREATED_ON);
        info.setCreatedOn(new Date(createdOne.longValue() * 1000));
        Integer lastUpdate = j.getInt(GerritChangeVO.JSON_KEY_LAST_UPDATE);
        info.setLastUpdate(new Date(lastUpdate.longValue() * 1000));

        info.setOpen(j.getBoolean(GerritChangeVO.JSON_KEY_OPEN));
        info.setStatus(j.getString(GerritChangeVO.JSON_KEY_STATUS));

        JSONObject cp =
            j.getJSONObject(GerritChangeVO.JSON_KEY_CURRENT_PATCH_SET);
        try {
            assignPatchSet(info, cp, true);

            List<JSONObject> patchSets =
                j.getJSONArray(GerritChangeVO.JSON_KEY_PATCH_SET);

            for (JSONObject p : patchSets) {
                assignPatchSet(info, p, false);
            }
        } catch (ParseException e) {
            throw new RepositoryException(e.getMessage());
        }

        log.debug(String.format("Object Transformed change=%s", info.toString()));

        return info;
    }

    private void assignPatchSet(GerritChangeVO info, JSONObject p,
                                boolean isCurrent) throws ParseException {
        log.debug(String.format("Assigning Patchset to: %s", info.toString()));

        PatchSet patch = new PatchSet();

        patch.setNumber(p.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_NUM));
        patch.setRevision(p.getString(GerritChangeVO.JSON_KEY_PATCH_SET_REV));
        patch.setRef(p.getString(GerritChangeVO.JSON_KEY_PATCH_SET_REF));

        JSONObject patchSetUploader =
            p.getJSONObject(GerritChangeVO.JSON_KEY_PATCH_SET_UPDLOADER);

        if (patchSetUploader.containsKey(GerritChangeVO.JSON_KEY_NAME))
            patch.setUploaderName(patchSetUploader
                .getString(GerritChangeVO.JSON_KEY_NAME));
        patch.setUploaderEmail(patchSetUploader
            .getString(GerritChangeVO.JSON_KEY_EMAIL));

        try {
            if (authorSupported) {
                JSONObject author =
                    p.getJSONObject(GerritChangeVO.JSON_KEY_PATCH_SET_AUTHOR);
                if (author != null) {
                    patch.setAuthorEmail(author
                        .getString(GerritChangeVO.JSON_KEY_EMAIL));
                    patch.setAuthorUserName(author
                        .getString(GerritChangeVO.JSON_KEY_USERNAME));
                    patch.setAuthorName(author
                        .getString(GerritChangeVO.JSON_KEY_NAME));
                }
            }
        } catch (JSONException e) {
            authorSupported = false;
            log.error(String.format("Author not supported in release %s: %s",
                getGerritVersion(), e.getMessage()));
            log.error("Disabling author lookup.");
        }

        Integer patchSetCreatedOn =
            p.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_CREATED_ON);
        patch.setCreatedOn(new Date(patchSetCreatedOn.longValue() * 1000));

        if (p.containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS)) {
            List<JSONObject> approvals =
                p.getJSONArray(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS);

            for (JSONObject a : approvals) {
                Approval apprv = new Approval();

                apprv.setType(a
                    .getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_TYPE));

                if (a.containsKey(GerritChangeVO.JSON_KEY_EMAIL)) {
                    apprv
                        .setDescription(a
                            .getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_DESC));
                }

                apprv.setValue(a
                    .getInt(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_VALUE));

                Integer grantedOn =
                    a.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_GRANTED_ON);
                apprv.setGrantedOn(new Date(grantedOn.longValue() * 1000));

                JSONObject by =
                    a.getJSONObject(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY);

                if (by.containsKey(GerritChangeVO.JSON_KEY_NAME))
                    apprv.setByName(by.getString(GerritChangeVO.JSON_KEY_NAME));

                if (by.containsKey(GerritChangeVO.JSON_KEY_EMAIL)) {
                    apprv.setByEmail(by
                        .getString(GerritChangeVO.JSON_KEY_EMAIL));
                }

                if (isCurrent) {
                    if (apprv.getType().equals("VRIF")
                        || apprv.getType().equals("Verified")) {
                        info.setVerificationScore(info.getVerificationScore()
                            + apprv.getValue());
                    } else if (apprv.getType().equals("CRVW")) {
                        info.setReviewScore(info.getReviewScore()
                            + apprv.getValue());
                    }
                }

                patch.getApprovals().add(apprv);
            }
        }

        if (p.containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_FILES)) {
            List<JSONObject> fileSets =
                p.getJSONArray(GerritChangeVO.JSON_KEY_PATCH_SET_FILES);

            for (JSONObject f : fileSets) {
                FileSet fileSet = new FileSet();

                fileSet.setFile(f
                    .getString(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_FILE));
                fileSet.setType(f
                    .getString(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_TYPE));

                if (f
                    .containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_INSRT)) {
                    fileSet.setInsertions(f
                        .getInt(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_INSRT));
                }

                if (f.containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_DELT)) {
                    fileSet.setDeletions(f
                        .getInt(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_DELT));
                }

                patch.getFileSets().add(fileSet);
            }
        }

        if (isCurrent) {
            info.setCurrentPatchSet(patch);
        } else {
            info.getPatchSets().add(patch);
        }

        log.debug(String.format("Patchset assigned: %s", patch.toString()));
    }
}
