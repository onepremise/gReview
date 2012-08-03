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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class GerritChangeVO {

    public static final String JSON_KEY_PROJECT = "project";
    public static final String JSON_KEY_BRANCH = "branch";
    public static final String JSON_KEY_ID = "id";
    public static final String JSON_KEY_NUMBER = "number";
    public static final String JSON_KEY_SUBJECT = "subject";
    public static final String JSON_KEY_OWNER = "owner";
    public static final String JSON_KEY_OWNER_NAME = "name";
    public static final String JSON_KEY_OWNER_EMAIL = "email";
    public static final String JSON_KEY_URL = "url";
    public static final String JSON_KEY_CREATED_ON = "createdOn";
    public static final String JSON_KEY_LAST_UPDATE = "lastUpdated";
    public static final String JSON_KEY_SORT_KEY = "sortKey";
    public static final String JSON_KEY_OPEN = "open";
    public static final String JSON_KEY_STATUS = "status";
    public static final String JSON_KEY_CURRENT_PATCH_SET = "currentPatchSet";
    public static final String JSON_KEY_PATCH_SET = "patchSets";
    public static final String JSON_KEY_PATCH_SET_NUM = "number";
    public static final String JSON_KEY_PATCH_SET_REV = "revision";
    public static final String JSON_KEY_PATCH_SET_REF = "ref";
    public static final String JSON_KEY_PATCH_SET_UPDLOADER = "uploader";
    public static final String JSON_KEY_OWNER_SET_UPDLOADER_NAME = "name";
    public static final String JSON_KEY_OWNER_SET_UPDLOADER_EMAIL = "email";
    public static final String JSON_KEY_PATCH_SET_CREATED_ON = "createdOn";
    public static final String JSON_KEY_PATCH_SET_APPRVS = "approvals";
    public static final String JSON_KEY_PATCH_SET_APPRVS_TYPE = "type";
    public static final String JSON_KEY_PATCH_SET_APPRVS_DESC = "description";
    public static final String JSON_KEY_PATCH_SET_APPRVS_VALUE = "value";
    public static final String JSON_KEY_PATCH_SET_APPRVS_GRANTED_ON =
        "grantedOn";
    public static final String JSON_KEY_PATCH_SET_APPRVS_BY = "by";
    public static final String JSON_KEY_PATCH_SET_APPRVS_BY_NAME = "name";
    public static final String JSON_KEY_PATCH_SET_APPRVS_BY_EMAIL = "email";
    public static final String JSON_KEY_PATCH_SET_FILES = "files";
    public static final String JSON_KEY_PATCH_SET_FILES_FILE = "file";
    public static final String JSON_KEY_PATCH_SET_FILES_TYPE = "type";
    public static final String JSON_KEY_ROWCOUNT = "rowCount";

    private static final String CHANGE_STATUS_MERGED = "MERGED";

    private String project;
    private String branch;
    private String id;
    private Integer number;
    private String subject;
    private String ownerName;
    private String ownerEmail;
    private String url;
    private Date createdOn;
    private Date lastUpdate;
    private String sortKey;
    private Boolean open;
    private String status;
    private Integer verificationScore = new Integer(0);
    private Integer reviewScore = new Integer(0);
    private PatchSet currentPatchSet = new PatchSet();

    private final Set<PatchSet> patchSets = new HashSet<PatchSet>(0);

    public static class PatchSet {

        private Integer number;
        private String revision;
        private String ref;
        private String uploaderName;
        private String uploaderEmail;
        private Date createdOn;
        private final Set<Approval> approvals = new HashSet<Approval>(0);
        private final Set<FileSet> fileSets = new HashSet<FileSet>(0);

        public PatchSet() {

        }

        public Integer getNumber() {
            return number;
        }

        public void setNumber(Integer number) {
            this.number = number;
        }

        public String getRevision() {
            return revision;
        }

        public void setRevision(String revision) {
            this.revision = revision;
        }

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getUploaderName() {
            return uploaderName;
        }

        public void setUploaderName(String uploaderName) {
            this.uploaderName = uploaderName;
        }

        public String getUploaderEmail() {
            return uploaderEmail;
        }

        public void setUploaderEmail(String uploaderEmail) {
            this.uploaderEmail = uploaderEmail;
        }

        public Date getCreatedOn() {
            return createdOn;
        }

        public void setCreatedOn(Date createdOn) {
            this.createdOn = createdOn;
        }

        public Set<Approval> getApprovals() {
            return approvals;
        }

        public Set<FileSet> getFileSets() {
            return fileSets;
        }

        @Override
        public String toString() {
            return "PatchSet [number=" + number + ", revision=" + revision
                + ", ref=" + ref + ", createdOn=" + createdOn + "]";
        }
    }

    public static class Approval {

        private String type;
        private String description;
        private Integer value;
        private Date grantedOn;
        private String byName;
        private String byEmail;

        public Approval() {

        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }

        public Date getGrantedOn() {
            return grantedOn;
        }

        public void setGrantedOn(Date grantedOn) {
            this.grantedOn = grantedOn;
        }

        public String getByName() {
            return byName;
        }

        public void setByName(String byName) {
            this.byName = byName;
        }

        public String getByEmail() {
            return byEmail;
        }

        public void setByEmail(String byEmail) {
            this.byEmail = byEmail;
        }

        @Override
        public String toString() {
            return "Approval [type=" + type + ", description=" + description
                + ", value=" + value + ", grantedOn=" + grantedOn + ", byName="
                + byName + ", byEmail=" + byEmail + "]";
        }

    }

    public static class FileSet {

        private String file;
        private String type;

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "FileSet [file=" + file + ", type=" + type + "]";
        }
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getLastRevision() {
        return this.getCurrentPatchSet().getRevision();
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public Boolean getOpen() {
        return open;
    }

    public void setOpen(Boolean open) {
        this.open = open;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getVerificationScore() {
        return verificationScore;
    }

    public void setVerificationScore(Integer verificationScore) {
        this.verificationScore = verificationScore;
    }

    public Integer getReviewScore() {
        return reviewScore;
    }

    public void setReviewScore(Integer reviewScore) {
        this.reviewScore = reviewScore;
    }

    public PatchSet getCurrentPatchSet() {
        return currentPatchSet;
    }

    public void setCurrentPatchSet(PatchSet currentPatchSet) {
        this.currentPatchSet = currentPatchSet;
    }

    public Set<PatchSet> getPatchSets() {
        return patchSets;
    }

    public boolean isMerged() {
        return this.getStatus().equalsIgnoreCase(CHANGE_STATUS_MERGED);
    }

    @Override
    public String toString() {
        return "GerritChangeVO [project=" + project + ", branch=" + branch
            + ", id=" + id + ", number=" + number + ", lastUpdate="
            + lastUpdate + ", open=" + open + ", status=" + status
            + ", verificationScore=" + verificationScore + ", reviewScore="
            + reviewScore + "]";
    }
}
