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

import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

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
                new GerritHandler(NUM_WORKER_THREADS);

            // gHandler.addListener(this);
            gHandler.addListener(new GerritEventListener() {
				@Override
				public void gerritEvent(GerritEvent event) {
					log.info("Gerrit event recieved: " + event.toString());
				}
            });

            gHandler.addListener(new GerritEventListener() {

                @Override
                public void gerritEvent(GerritEvent event) {
                    log.debug("Processing GerritEvent...");
                    
                    if (event.getEventType().equals(GerritEventType.PATCHSET_CREATED) ||
                    		event.getEventType().equals(GerritEventType.REF_UPDATED)) {
                    	log.debug(String.format("Processing %s...", event.toString()));
                    	processGerritEvent(event);
                    }
                }
            });
        }

        //if (!gHandler.isAlive())
        //    gHandler.start();

        return gHandler;
    }

    public void processGerritEvent(GerritEvent e) {
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
            gerritListeners.add(l);
            sendCurrentOpenChanges(l);
        }
    }

    public void removeGerritListener(GerritProcessListener l) {
        if (gerritListeners != null) {
            gerritListeners.remove(l);
        }
    }
}
