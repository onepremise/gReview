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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

import com.atlassian.bamboo.repository.RepositoryException;
import com.google.common.primitives.Ints;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.Approval;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryException;
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

    private static final Logger log = Logger.getLogger(GerritService.class);

    private GerritHandler gHandler = null;
    private GerritSQLHandler gQueryHandler = null;
    private GerritCmdProcessor cmdProcessor = null;
    private GerritUserVO gerritSystemUser = null;
    private String strHost;
    private String strProxy;
    private String userEmail;

    private int port = 29418;
    private Authentication auth = null;
    private int watchdogTimeoutMinutes;
    private WatchTimeExceptionData watchTimeExceptionData;

    public GerritService(String strHost, int port, File sshKeyFile,
                         String strUsername, String phrase) {
        auth = new Authentication(sshKeyFile, strUsername, phrase);
        this.strHost = strHost;
        this.port = port;
    }

    public GerritService(String strHost, int port, Authentication auth) {
        this.strHost = strHost;
        this.port = port;
        this.auth = auth;
    }

    public void testGerritConnection() throws RepositoryException {
        SshConnection sshConnection = null;

        try {
            sshConnection =
                SshConnectionFactory.getConnection(strHost, port, auth);
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

    /*
     * public GerritHandler manageGerritHandler(String sshKey, String strHost,
     * int port, String strUsername, String phrase, boolean test,
     * boolean reset)
     * throws RepositoryException {
     * this.updateCredentials(sshKey, strHost, strUsername, phrase);
     * 
     * if (test)
     * testGerritConnection(strHost, port);
     * 
     * if (gHandler==null) {
     * synchronized (GerritRepositoryAdapter.class) {
     * gHandler=new GerritHandler(strHost, port, authentication,
     * NUM_WORKER_THREADS);
     * gHandler.addListener(this);
     * gHandler.addListener(new ConnectionListener() {
     * 
     * @Override
     * public void connectionDown() {
     * log.error("Gerrit connection down.");
     * gHandler.shutdown(false);
     * }
     * 
     * @Override
     * public void connectionEstablished() {
     * log.info("Gerrit connection established!");
     * }
     * });
     * }
     * }
     * 
     * if (reset) {
     * if (gHandler.isAlive()) {
     * gHandler.shutdown(true);
     * }
     * 
     * gHandler.start();
     * }
     * 
     * return gHandler;
     * }
     */

    private class GerritCmdProcessor extends AbstractSendCommandJob {

        protected GerritCmdProcessor(GerritConnectionConfig2 config) {
            super(config);
        }

        @Override
        public void run() {

        }
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
                        return auth.getPrivateKeyFile();
                    }

                    @Override
                    public String getGerritAuthKeyFilePassword() {
                        return auth.getPrivateKeyFilePassword();
                    }

                    @Override
                    public Authentication getGerritAuthentication() {
                        return auth;
                    }

                    @Override
                    public String getGerritHostName() {
                        return strHost;
                    }

                    @Override
                    public int getGerritSshPort() {
                        return port;
                    }

                    @Override
                    public String getGerritUserName() {
                        return auth.getUsername();
                    }

                    @Override
                    public String getGerritEMail() {
                        return userEmail;
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

                        daysAsInt = Ints.toArray(days);

                        return new WatchTimeExceptionData(daysAsInt,
                            exceptionTimes);
                    }

                    @Override
                    public String getGerritProxy() {
                        return strProxy;
                    }
                });
        }

        return cmdProcessor;
    }

    private GerritSQLHandler getGerritQueryHandler() {
        if (gQueryHandler == null) {
            gQueryHandler = new GerritSQLHandler(strHost, port, strProxy, auth);

            requestExtendedInfo();
        }

        return gQueryHandler;
    }

    protected void requestExtendedInfo() {
        Thread asyncCommand = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    GerritUserVO user = getGerritSystemUser();

                    userEmail = user.getEmail();
                } catch (RepositoryException e) {
                    log.info(e.getMessage());
                }
            }
        });
        asyncCommand.start();
    }

    public String getGerritVersion() {
        String version =
            getGerritCmdProcessor().sendCommandStr("gerrit version");

        return version;
    }

    public GerritUserVO getGerritSystemUser() throws RepositoryException {

        if (gerritSystemUser == null) {
            List<JSONObject> jsonObjects =
                runGerritSQL("select * from accounts");

            for (JSONObject j : jsonObjects) {
                if (j.containsKey("type") && j.getString("type").equals("row")) {
                    JSONObject userInfo = j.getJSONObject("columns");
                    return transformUserJSONObject(userInfo);
                }
            }
        }

        return gerritSystemUser;
    }

    public String getGerritSystemUserEmail() {
        return userEmail;
    }

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
                auth.getUsername()));
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
        GerritChangeVO selectedChange = null;

        Date lastDt = new Date(0);

        for (GerritChangeVO change : changes) {
            Date dt = change.getLastUpdate();

            if (dt.getTime() > lastDt.getTime()) {
                lastDt = change.getLastUpdate();
                selectedChange = change;
            }
        }

        return selectedChange;
    }

    public GerritChangeVO getLastUnverifiedChange() throws RepositoryException {
        log.debug("getLastUnverifiedChange()...");

        Set<GerritChangeVO> changes = getGerritChangeInfo();
        GerritChangeVO selectedChange = null;

        Date lastDt = new Date(0);

        for (GerritChangeVO change : changes) {
            Date dt = change.getLastUpdate();

            if (dt.getTime() > lastDt.getTime()
                && change.getVerificationScore() < 1) {
                lastDt = change.getLastUpdate();
                selectedChange = change;
            }
        }

        return selectedChange;
    }

    public GerritChangeVO
                    getLastChange(String project) throws RepositoryException {
        log.debug(String.format("getLastChange(project=%s)...", project));

        Set<GerritChangeVO> changes = getGerritChangeInfo(project);
        GerritChangeVO selectedChange = null;

        Date lastDt = new Date(0);

        for (GerritChangeVO change : changes) {
            Date dt = change.getLastUpdate();

            if (dt.getTime() > lastDt.getTime()) {
                lastDt = change.getLastUpdate();
                selectedChange = change;
            }
        }

        return selectedChange;
    }

    public GerritChangeVO
                    getLastUnverifiedChange(String project) throws RepositoryException {
        log.debug(String.format("getLastUnverifiedChange(project=%s)...",
            project));

        Set<GerritChangeVO> changes = getGerritChangeInfo(project);
        GerritChangeVO selectedChange = null;

        Date lastDt = new Date(0);

        for (GerritChangeVO change : changes) {
            Date dt = change.getLastUpdate();

            if (dt.getTime() > lastDt.getTime()
                && change.getVerificationScore() < 1) {
                lastDt = change.getLastUpdate();
                selectedChange = change;
            }
        }

        return selectedChange;
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

    private GerritUserVO
                    transformUserJSONObject(JSONObject j) throws RepositoryException {
        if (j == null) {
            throw new RepositoryException("No data to parse!");
        }

        log.debug(String.format("transformJSONObject(j=%s)", j));

        GerritUserVO user = new GerritUserVO();

        user.setId(j.getString(GerritUserVO.JSON_KEY_ACCT_ID));
        user.setUserName(j.getString(GerritUserVO.JSON_KEY_ACCT_ID));
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
        } catch (JSONException e) {
            log.info(String.format("Author not supported in release %s: %s",
                getGerritVersion(), e.getMessage()));
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
                    if (apprv.getType().equals("VRIF")) {
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
