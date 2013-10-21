/*
 * Houghton Associates Framework
 * http://www.houghtonassociates.com
 * 
 * Copyright 2013 Houghton Associates, Inc.
 */
package com.houghtonassociates.bamboo.plugins.dao;

import java.util.Date;

/**
 * @author Jason Huntley
 * 
 */
public class GerritUserVO {

    public static final String JSON_KEY_ACCT_ID = "account_id";
    public static final String JSON_KEY_FULL_NAME = "full_name";
    public static final String JSON_KEY_COPY_SELF_EMAIL = "copy_self_on_email";
    public static final String JSON_KEY_INACTIVE = "inactive";
    public static final String JSON_KEY_EMAIL = "preferred_email";
    public static final String JSON_KEY_REG_DATE = "registered_on";

    private String id = "";
    private String userName = "";
    private String fullName = "";
    private String email = "";
    private boolean active = true;
    private Date registrationDate = null;

    public GerritUserVO fill(GerritUserVO user) {
        if (this.id.isEmpty())
            this.id = user.id;

        if (this.userName.isEmpty())
            this.userName = user.userName;

        if (this.fullName.isEmpty())
            this.fullName = user.fullName;

        if (this.email.isEmpty())
            this.email = user.email;

        this.active = user.active;

        if (this.registrationDate == null)
            this.registrationDate = user.registrationDate;

        return this;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }
}
