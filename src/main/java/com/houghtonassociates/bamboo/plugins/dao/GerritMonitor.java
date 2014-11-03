/*
 * Houghton Associates Framework
 * http://www.houghtonassociates.com
 * 
 * Copyright 2014 Houghton Associates, Inc.
 */
package com.houghtonassociates.bamboo.plugins.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefUpdated;

/**
 * @author jhuntley
 *
 */
public class GerritMonitor {

    private static final int NUM_WORKER_THREADS = 1;

    private static final Logger log = Logger.getLogger(GerritMonitor.class);
    private GerritHandler gHandler = null;
    private List<GerritProcessListener> gerritListeners = null;
    private GerritService service = null;

    public GerritHandler initialize(GerritService s) {
        if (gHandler == null) {
            this.service = s;

            GerritConfig gc = s.getConfig();

            gHandler =
                new GerritHandler(gc.getHost(), gc.getPort(), gc.getAuth(),
                    NUM_WORKER_THREADS);

            // gHandler.addListener(this);
            gHandler.addListener(new ConnectionListener() {

                @Override
                public void connectionDown() {
                    log.error("Gerrit connection down.");
                    // gHandler.shutdown(false);
                }

                @Override
                public void connectionEstablished() {
                    log.info("Gerrit connection established!");
                }
            });

            gHandler.addListener(new GerritEventListener() {

                @Override
                public void gerritEvent(GerritEvent event) {
                    log.debug("Processing GerritEvent...");
                }

                @Override
                public void gerritEvent(PatchsetCreated event) {
                    log.debug("Processing PatchsetCreated...");
                    processGerritEvent(event);
                }

                @Override
                public void gerritEvent(DraftPublished event) {
                    log.debug("Processing DraftPublished...");
                }

                @Override
                public void gerritEvent(ChangeAbandoned event) {
                    log.debug("Processing ChangeAbandoned...");
                }

                @Override
                public void gerritEvent(ChangeMerged event) {
                    log.debug("Processing ChangeMerged...");
                }

                @Override
                public void gerritEvent(ChangeRestored event) {
                    log.debug("Processing ChangeRestored...");
                }

                @Override
                public void gerritEvent(CommentAdded event) {
                    log.debug("Processing CommentAdded...");
                }

                @Override
                public void gerritEvent(RefUpdated event) {
                    log.debug("Processing RefUpdated...");
                    processGerritEvent(event);
                }

            });
        }

        if (!gHandler.isAlive())
            gHandler.start();

        return gHandler;
    }

    public void processGerritEvent(GerritTriggeredEvent e) {
        if (gerritListeners != null) {
            for (GerritProcessListener l : gerritListeners) {
                l.processGerritEvent(e);
            }
        }
    }

    public void sendCurrentOpenChanges(GerritProcessListener l) {
        try {
            Set<GerritChangeVO> changes = service.getLastUnverifiedChanges();

            for (GerritChangeVO c : changes) {
                PatchsetCreated p = new PatchsetCreated();

                p.setAccount(c.toChange().getOwner());
                p.setChange(c.toChange());
                p.setPatchset(c.getCurrentPatchSet().toPatchSet());
                p.setProvider(service.getProvider());

                processGerritEvent(p);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public void addGerritListener(GerritProcessListener l) {
        if (gerritListeners == null)
            gerritListeners = new ArrayList<GerritProcessListener>();

        if (!gerritListeners.contains(l)) {
            sendCurrentOpenChanges(l);
            gerritListeners.add(l);
        }
    }

    public void removeGerritListener(GerritProcessListener l) {
        if (gerritListeners != null) {
            gerritListeners.remove(l);
        }
    }
}
