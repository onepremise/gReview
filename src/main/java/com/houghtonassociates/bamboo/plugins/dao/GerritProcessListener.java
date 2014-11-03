/*
 * Houghton Associates Framework
 * http://www.houghtonassociates.com
 * 
 * Copyright 2014 Houghton Associates, Inc.
 */
package com.houghtonassociates.bamboo.plugins.dao;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

/**
 * @author jhuntley
 *
 */
public interface GerritProcessListener {

    public void processGerritEvent(GerritTriggeredEvent e);
}
