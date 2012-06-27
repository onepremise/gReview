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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

import com.atlassian.bamboo.repository.RepositoryException;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.Approval;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;

/**
 * Facade for witing with ssh, gerrit-events, parsing JSON results, and Gerrit
 * related data.
 * 
 * @author Jason Huntley
 * 
 */
public class GerritService {

    private static final Logger log = Logger
        .getLogger(GerritRepositoryAdapter.class);

    private GerritHandler gHandler = null;
    private GerritQueryHandler gQueryHandler = null;
    private GerritCmdProcessor cmdProcessor = null;
    private String strHost;
    private int port = 29418;
    private Authentication auth = null;

    public GerritService(String strHost, int port, File sshKeyFile,
                         String strUsername, String phrase) {
        auth = new Authentication(sshKeyFile, strUsername, phrase);
        this.strHost = strHost;
        this.port = port;
    }

    public GerritService(String strHost, File sshKeyFile, String strUsername,
                         String phrase) {
        auth = new Authentication(sshKeyFile, strUsername, phrase);
        this.strHost = strHost;
    }

    public GerritService(String strHost, int port, Authentication auth) {
        this.strHost = strHost;
        this.port = port;
        this.auth = auth;
    }

    public GerritService(String strHost, Authentication auth) {
        this.strHost = strHost;
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

    private class GerritCmdProcessor extends AbstractSendCommandJob {

        protected GerritCmdProcessor(GerritConnectionConfig config) {
            super(config);
        }

        @Override
        public void run() {

        }
    }

    public boolean verifyChange(Boolean pass, Integer changeNumber,
                                Integer patchNumber, String message) {
        if (pass.booleanValue())
            return getGerritCmdProcessor().sendCommand(
                String.format(
                    "gerrit review --message \"%s\" --verified +1 %s,%s",
                    message, changeNumber.intValue(), patchNumber.intValue()));

        return getGerritCmdProcessor().sendCommand(
            String.format("gerrit review --message \"%s\" --verified -1 %s,%s",
                message, changeNumber.intValue(), patchNumber.intValue()));
    }

    private GerritCmdProcessor getGerritCmdProcessor() {
        if (cmdProcessor == null) {
            cmdProcessor = new GerritCmdProcessor(new GerritConnectionConfig() {

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
                public int getNumberOfReceivingWorkerThreads() {
                    return 2;
                }

                @Override
                public int getNumberOfSendingWorkerThreads() {
                    return 2;
                }

            });
        }

        return cmdProcessor;
    }

    private GerritQueryHandler getGerritQueryHandler() {
        if (gQueryHandler == null) {
            gQueryHandler = new GerritQueryHandler(strHost, port, auth);
        }

        return gQueryHandler;
    }

    public List<JSONObject> runGerritQuery(String query) throws SshException,
                    IOException, GerritQueryException {
        return getGerritQueryHandler().queryJava(query, true, true, true);
    }

    public GerritChangeVO getLastChange() throws RepositoryException {
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

    public GerritChangeVO getLastChange(String project)
                    throws RepositoryException {
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

    public GerritChangeVO getLastUnverifiedChange(String project)
                    throws RepositoryException {
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

    public GerritChangeVO getChangeByRevision(String rev)
                    throws RepositoryException {
        List<JSONObject> jsonObjects = null;

        try {
            jsonObjects = runGerritQuery(String.format("commit:%s", rev));
        } catch (SshException e) {
            throw new RepositoryException(e.getMessage());
        } catch (IOException e) {
            throw new RepositoryException(e.getMessage());
        } catch (GerritQueryException e) {
            throw new RepositoryException(e.getMessage());
        }

        if (jsonObjects == null || jsonObjects.size() == 0)
            return null;

        JSONObject setInfo = jsonObjects.get(jsonObjects.size() - 1);

        int rowCount = setInfo.getInt(GerritChangeVO.JSON_KEY_ROWCOUNT);

        if (rowCount == 0)
            return null;

        return this.transformJSONObject(jsonObjects.get(0));
    }

    public Set<GerritChangeVO> getGerritChangeInfo() throws RepositoryException {
        List<JSONObject> jsonObjects = null;

        try {
            jsonObjects = runGerritQuery("is:open");
        } catch (SshException e) {
            throw new RepositoryException(e.getMessage());
        } catch (IOException e) {
            throw new RepositoryException(e.getMessage());
        } catch (GerritQueryException e) {
            throw new RepositoryException(e.getMessage());
        }

        log.info("Query result count: " + jsonObjects.size());

        Set<GerritChangeVO> results = new HashSet<GerritChangeVO>(0);

        for (JSONObject j : jsonObjects) {
            if (j.containsKey(GerritChangeVO.JSON_KEY_PROJECT)) {
                GerritChangeVO info = transformJSONObject(j);
                results.add(info);
            }
        }

        return results;
    }

    public Set<GerritChangeVO> getGerritChangeInfo(String project)
                    throws RepositoryException {
        List<JSONObject> jsonObjects = null;

        try {
            String strQuery = String.format("is:open project:%s", project);
            jsonObjects = runGerritQuery(strQuery);
        } catch (SshException e) {
            throw new RepositoryException(e.getMessage());
        } catch (IOException e) {
            throw new RepositoryException(e.getMessage());
        } catch (GerritQueryException e) {
            throw new RepositoryException(e.getMessage());
        }

        log.info("Query result count: " + jsonObjects.size());

        Set<GerritChangeVO> results = new HashSet<GerritChangeVO>(0);

        for (JSONObject j : jsonObjects) {
            if (j.containsKey(GerritChangeVO.JSON_KEY_PROJECT)) {
                GerritChangeVO info = transformJSONObject(j);
                results.add(info);
            }
        }

        return results;
    }

    private GerritChangeVO transformJSONObject(JSONObject j)
                    throws RepositoryException {
        if (j == null) {
            throw new RepositoryException("No data to parse!");
        }

        GerritChangeVO info = new GerritChangeVO();

        info.setProject(j.getString(GerritChangeVO.JSON_KEY_PROJECT));
        info.setBranch(j.getString(GerritChangeVO.JSON_KEY_BRANCH));
        info.setId(j.getString(GerritChangeVO.JSON_KEY_ID));
        info.setNumber(j.getInt(GerritChangeVO.JSON_KEY_NUMBER));
        info.setSubject(j.getString(GerritChangeVO.JSON_KEY_SUBJECT));

        JSONObject owner = j.getJSONObject(GerritChangeVO.JSON_KEY_OWNER);

        info.setOwnerName(owner.getString(GerritChangeVO.JSON_KEY_OWNER_NAME));
        info.setOwnerEmail(owner.getString(GerritChangeVO.JSON_KEY_OWNER_EMAIL));

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

        return info;
    }

    private void assignPatchSet(GerritChangeVO info, JSONObject p,
                                boolean isCurrent) throws ParseException {
        PatchSet patch = new PatchSet();

        patch.setNumber(p.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_NUM));
        patch.setRevision(p.getString(GerritChangeVO.JSON_KEY_PATCH_SET_REV));
        patch.setRef(p.getString(GerritChangeVO.JSON_KEY_PATCH_SET_REF));

        JSONObject patchSetUploader =
            p.getJSONObject(GerritChangeVO.JSON_KEY_PATCH_SET_UPDLOADER);
        patch.setUploaderName(patchSetUploader
            .getString(GerritChangeVO.JSON_KEY_OWNER_SET_UPDLOADER_NAME));
        patch.setUploaderEmail(patchSetUploader
            .getString(GerritChangeVO.JSON_KEY_OWNER_SET_UPDLOADER_EMAIL));

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

                if (a
                    .containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY_EMAIL)) {
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
                apprv
                    .setByName(by
                        .getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY_NAME));

                if (by
                    .containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY_EMAIL)) {
                    apprv
                        .setByEmail(by
                            .getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY_EMAIL));
                }

                if (isCurrent) {
                    if (apprv.getType().equals("VRIF"))
                        info.setVerificationScore(info.getVerificationScore()
                            + apprv.getValue());
                    else if (apprv.getType().equals("CRVW"))
                        info.setReviewScore(info.getReviewScore()
                            + apprv.getValue());
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

                patch.getFileSets().add(fileSet);
            }
        }

        if (isCurrent) {
            info.setCurrentPatchSet(patch);
        } else {
            info.getPatchSets().add(patch);
        }
    }
}
