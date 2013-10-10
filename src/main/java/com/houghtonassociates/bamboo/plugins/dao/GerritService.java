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

import com.atlassian.bamboo.repository.RepositoryException;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.Approval;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;
import net.sf.json.JSONObject;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Facade for writing with ssh, gerrit-events, parsing JSON results, and Gerrit related data.
 */
public class GerritService {

    private static final Logger log = Logger.getLogger(GerritService.class);

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

    public GerritService(String strHost, int port, Authentication auth) {
        this.strHost = strHost;
        this.port = port;
        this.auth = auth;
    }

    public boolean verifyChange(Boolean pass, Integer changeNumber,
                                Integer patchNumber, String message) {
        String command = "";

        if (pass == null) {
            command = String.format( "gerrit review --message '%s' --verified 0 %d,%d",
                    message, changeNumber, patchNumber);
        } else {
            if (pass) {
                command = String.format( "gerrit review --message '%s' --verified +1 %d,%d",
                        message, changeNumber, patchNumber);
            } else {
                command = String.format( "gerrit review --message '%s' --verified -1 %d,%d",
                        message, changeNumber, patchNumber);
            }
        }

        log.debug("Sending Command: " + command);

        return getGerritCmdProcessor().sendCommand(command);
    }
    /**
     * Return the oldest unverived change.
     *
     *
     * @param project
     * @param branch
     * @param drafts
     * @param lastVcsRevisionKey
     * @return
     * @throws RepositoryException
     */
    public GerritChangeVO getFirstUnverifiedChange(String project, String branch, boolean drafts, String lastVcsRevisionKey) throws RepositoryException {
        log.debug(String.format("getFirstUnverifiedChange(project=%s branch=%s)...", project, branch));

        Set<GerritChangeVO> changes = getGerritChangeInfo(project, branch, drafts);
        GerritChangeVO selectedChange = null;

        Date firstDt = new Date();

        for (GerritChangeVO change : changes) {
            Date dt = change.getLastUpdate();

            if (dt.getTime() < firstDt.getTime() && change.getVerificationScore() == 0 && !change.isMerged()
                    && !change.getCurrentPatchSet().getRevision().equals(lastVcsRevisionKey) ) {
                firstDt = change.getLastUpdate();
                selectedChange = change;
            }
        }

        log.debug(String.format("getFirstUnverifiedChange(project=%s) is: %s", project, selectedChange));
        return selectedChange;
    }

    public GerritChangeVO getChangeByID(String changeID) throws RepositoryException {
        log.debug(String.format("getChangeByID(changeID=%s)...", changeID));

        List<JSONObject> jsonObjects = runGerritQuery(String.format("change:%s", changeID), false);

        if (jsonObjects == null) {
            return null;
        }

        return this.transformJSONObject(jsonObjects.get(0));
    }

    public GerritChangeVO getChangeByRevision(String rev) throws RepositoryException {
        log.debug(String.format("getChangeByRevision(rev=%s)...", rev));

        List<JSONObject> jsonObjects = null;

        jsonObjects = runGerritQuery(String.format("commit:%s", rev), true);

        if (jsonObjects == null) {
            return null;
        }

        GerritChangeVO changeVO = this.transformJSONObject(jsonObjects.get(0));

        if (!rev.equals(changeVO.getLastRevision())) {
            for (PatchSet p : changeVO.getPatchSets()) {
                if (rev.equals(p.getRevision())) {
                    changeVO.setCurrentPatchSet(p);
                    break;
                }
            }
        }
        return changeVO;
    }

    private Set<GerritChangeVO> getGerritChangeInfo(String project, String branch, boolean drafts) throws RepositoryException {

        StringBuffer strQuery = new StringBuffer();

        strQuery.append("is:open project:").append(project);

        if  (!drafts) {
            strQuery.append(" NOT is:draft");
        }

        if (branch!=null) {
            strQuery.append(" branch:").append(branch);
        }

        List<JSONObject> jsonObjects = runGerritQuery(strQuery.toString(), false);
        Set<GerritChangeVO> results = new HashSet<GerritChangeVO>(0);

        if (jsonObjects == null) {
            return results;
        }

        log.debug("Query result count: " + jsonObjects.size());

        for (JSONObject j : jsonObjects) {
            if (j.containsKey(GerritChangeVO.JSON_KEY_PROJECT)) {
                GerritChangeVO info = transformJSONObject(j);
                results.add(info);
            }
        }

        return results;
    }

    private class GerritCmdProcessor extends AbstractSendCommandJob {

        protected GerritCmdProcessor(GerritConnectionConfig config) {
            super(config);
        }

        @Override
        public void run() {

        }
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

    public List<JSONObject> runGerritQuery(String query, boolean withPatchSets) throws RepositoryException {
        List<JSONObject> jsonObjects = null;

        log.debug("Gerrit query: " + query);

        try {
            jsonObjects = getGerritQueryHandler().queryJava(query, withPatchSets, true, true);
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

    private GerritChangeVO transformJSONObject(JSONObject j) throws RepositoryException {
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

        info.setOwnerName(owner.getString(GerritChangeVO.JSON_KEY_OWNER_NAME));
        info.setOwnerEmail(owner.getString(GerritChangeVO.JSON_KEY_OWNER_EMAIL));

        info.setUrl(j.getString(GerritChangeVO.JSON_KEY_URL));

        Integer createdOne = j.getInt(GerritChangeVO.JSON_KEY_CREATED_ON);
        info.setCreatedOn(new Date(createdOne.longValue() * 1000));
        Integer lastUpdate = j.getInt(GerritChangeVO.JSON_KEY_LAST_UPDATE);
        info.setLastUpdate(new Date(lastUpdate.longValue() * 1000));

        info.setOpen(j.getBoolean(GerritChangeVO.JSON_KEY_OPEN));
        info.setStatus(j.getString(GerritChangeVO.JSON_KEY_STATUS));

        JSONObject cp = j.getJSONObject(GerritChangeVO.JSON_KEY_CURRENT_PATCH_SET);
        try {
            PatchSet p = assignPatchSet(info, cp);
            info.setCurrentPatchSet(p);

        } catch (ParseException e) {
            throw new RepositoryException(e.getMessage());
        }

        if (j.containsKey(GerritChangeVO.JSON_KEY_PATCH_SET)) {
            List<JSONObject> psets = j.getJSONArray(GerritChangeVO.JSON_KEY_PATCH_SET);
            for (JSONObject jset : psets) {
                try {
                    info.getPatchSets().add(assignPatchSet(info, jset));
                } catch (ParseException e) {
                    throw new RepositoryException(e.getMessage());
                }
            }
        }

        log.debug(String.format("Object Transformed change=%s", info.toString()));

        return info;
    }

    private PatchSet assignPatchSet(GerritChangeVO info, JSONObject p) throws ParseException {
        log.debug(String.format("Assigning Patchset to: %s", info.toString()));

        PatchSet patch = new PatchSet();

        patch.setNumber(p.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_NUM));
        patch.setRevision(p.getString(GerritChangeVO.JSON_KEY_PATCH_SET_REV));
        patch.setRef(p.getString(GerritChangeVO.JSON_KEY_PATCH_SET_REF));

        JSONObject patchSetUploader = p.getJSONObject(GerritChangeVO.JSON_KEY_PATCH_SET_UPDLOADER);
        patch.setUploaderName(patchSetUploader.getString(
				GerritChangeVO.JSON_KEY_OWNER_SET_UPDLOADER_NAME));
        patch.setUploaderEmail(patchSetUploader.getString(
				GerritChangeVO.JSON_KEY_OWNER_SET_UPDLOADER_EMAIL));

        Integer patchSetCreatedOn = p.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_CREATED_ON);
        patch.setCreatedOn(new Date(patchSetCreatedOn.longValue() * 1000));

        if (p.containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS)) {
            List<JSONObject> approvals = p.getJSONArray(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS);

            for (JSONObject a : approvals) {
                Approval apprv = new Approval();

                apprv.setType(a.getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_TYPE));
                if (a.has(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_DESC)) {
                    apprv.setDescription(a.getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_DESC));
                }
                apprv.setValue(a.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_VALUE));

                Integer grantedOn = a.getInt(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_GRANTED_ON);
                apprv.setGrantedOn(new Date(grantedOn.longValue() * 1000));

                JSONObject by = a.getJSONObject(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY);
                apprv.setByName(by.getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY_NAME));
                apprv.setByEmail(by .getString(GerritChangeVO.JSON_KEY_PATCH_SET_APPRVS_BY_EMAIL));

                if ("Verified".equals(apprv.getType()) || "VRIF".equals(apprv.getType())) {
                    if (info.getVerificationScore()==0 || info.getVerificationScore() > apprv.getValue()) {
                        info.setVerificationScore(apprv.getValue());
                    }
				} else if ("Code-Review".equals(apprv.getType()) || "CRVW".equals(apprv.getType())) {
                    if (info.getReviewScore()==0 || info.getReviewScore() > apprv.getValue()) {
                        info.setReviewScore(apprv.getValue());
                    }
				}

                patch.getApprovals().add(apprv);
            }
        }

        if (p.containsKey(GerritChangeVO.JSON_KEY_PATCH_SET_FILES)) {
            List<JSONObject> fileSets = p.getJSONArray(GerritChangeVO.JSON_KEY_PATCH_SET_FILES);

            for (JSONObject f : fileSets) {
                FileSet fileSet = new FileSet();

                fileSet.setFile(f.getString(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_FILE));
                fileSet.setType(f.getString(GerritChangeVO.JSON_KEY_PATCH_SET_FILES_TYPE));

                patch.getFileSets().add(fileSet);
            }
        }

        if (log.isDebugEnabled()){
            log.debug(String.format("Patchset assigned: %s", patch.toString()));
        }

        return patch;
    }
}
