/*
 * Houghton Associates Framework
 * http://www.houghtonassociates.com
 * 
 * Copyright 2013 Houghton Associates, Inc.
 */
package com.houghtonassociates.bamboo.plugins.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonymobile.tools.gerrit.gerritevents.GerritConnectionConfig;
import com.sonymobile.tools.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryHandler;
import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshConnection;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshException;

public class GerritSQLHandler {

    private static final Logger logger = LoggerFactory
        .getLogger(GerritSQLHandler.class);

    public static final String QUERY_SQL_COMMAND = "gerrit gsql";
    private String gerritHostName;
    private int gerritSshPort;
    private String gerritProxy;
    private Authentication authentication;
    private GerritQueryHandler queryHandler;

    public GerritSQLHandler(String gerritHostName, int gerritSshPort,
                            String gerritProxy, Authentication authentication) {
        this.gerritHostName = gerritHostName;
        this.gerritSshPort = gerritSshPort;
        this.gerritProxy = gerritProxy;
        this.authentication = authentication;
        this.queryHandler =
            new GerritQueryHandler(gerritHostName, gerritSshPort, gerritProxy,
                authentication);
    }

    public GerritSQLHandler(GerritConnectionConfig config) {
        this(config.getGerritHostName(), config.getGerritSshPort(),
            GerritDefaultValues.DEFAULT_GERRIT_PROXY, config
                .getGerritAuthentication());
    }

    public GerritSQLHandler(GerritConnectionConfig2 config) {
        this(config.getGerritHostName(), config.getGerritSshPort(), config
            .getGerritProxy(), config.getGerritAuthentication());
    }

    public List<JSONObject> queryJava(String queryString) throws SshException,
                    IOException,
                    GerritQueryException {
        return queryHandler.queryJava(queryString, true, true, false);
    }

    public List<JSONObject> queryJava(String queryString, boolean getPatchSets,
                                      boolean getCurrentPatchSet,
                                      boolean getFiles) throws SshException,
                    IOException,
                    GerritQueryException {
        return queryHandler.queryJava(queryString, getPatchSets,
            getCurrentPatchSet, getFiles);
    }

    public List<JSONObject> queryFiles(String queryString) throws SshException,
                    IOException,
                    GerritQueryException {
        return queryHandler.queryJava(queryString, false, true, true);
    }

    public List<JSONObject> querySQL(String queryString) throws SshException,
                    IOException,
                    GerritQueryException {

        final List<JSONObject> list = new LinkedList<JSONObject>();

        runSQL(queryString, new LineVisitor() {

            @Override
            public void visit(String line) throws GerritQueryException {
                JSONObject json =
                    (JSONObject) JSONSerializer.toJSON(line.trim());
                if (json.has("type")
                    && "error".equalsIgnoreCase(json.getString("type"))) {
                    throw new GerritQueryException(json.getString("message"));
                }
                list.add(json);
            }
        });
        return list;
    }

    private void
                    runSQL(String queryString, LineVisitor visitor) throws GerritQueryException,
                                    SshException,
                                    IOException {
        StringBuilder str = new StringBuilder(QUERY_SQL_COMMAND);

        queryString = queryString.replace("*", "\\*");

        str.append(" --format=JSON");
        str.append(" -c");
        str.append(" \"");
        str.append(queryString.replace((CharSequence) "\"",
            (CharSequence) "\\\""));
        str.append("\"");

        SshConnection ssh = null;
        try {
            ssh =
                SshConnectionFactory.getConnection(gerritHostName,
                    gerritSshPort, gerritProxy, authentication);
            BufferedReader reader =
                new BufferedReader(ssh.executeCommandReader(str.toString()));
            String incomingLine = null;
            while ((incomingLine = reader.readLine()) != null) {
                logger.trace("Incoming line: {}", incomingLine);
                visitor.visit(incomingLine);
            }
            logger.trace("Closing reader.");
            reader.close();
        } finally {
            if (ssh != null) {
                ssh.disconnect();
            }
        }
    }

    interface LineVisitor {

        void visit(String line) throws GerritQueryException;
    }
}
