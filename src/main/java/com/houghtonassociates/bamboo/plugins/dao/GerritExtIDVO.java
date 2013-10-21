package com.houghtonassociates.bamboo.plugins.dao;

public class GerritExtIDVO {

    public static final String JSON_KEY_USERNAME = "username:";
    public static final String JSON_KEY_ACCT_ID = "account_id";
    public static final String JSON_KEY_EMAIL = "email_address";
    public static final String JSON_KEY_PASSWD = "password";
    public static final String JSON_KEY_EXT_ID = "external_id";

    private String accountId;
    private String email;
    private String password;
    private String externalId;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

}
