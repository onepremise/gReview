package com.houghtonassociates.bamboo.plugins;

import com.atlassian.bamboo.build.logger.BuildLogger;
import org.eclipse.jgit.lib.ProgressMonitor;

public class GitProgressMonitor implements ProgressMonitor {

    private final BuildLogger buildLogger;
    private final boolean verboseLogs;
    private int totalWork;
    private int completedWor;
    private int nextWorkLog;
    private String title;

    public GitProgressMonitor(BuildLogger buildLogger, boolean verboseLogs) {
        this.buildLogger = buildLogger;
        this.verboseLogs = verboseLogs;
    }

    @Override
    public void start(int totalTasks) {
    }

    @Override
    public void beginTask(String title, int totalWork) {
        buildLogger.addBuildLogEntry(title);
        this.title = title;
        this.totalWork = totalWork;
        this.completedWor = 0;
        this.nextWorkLog = 10;
    }

    @Override
    public void update(int completed) {
        completedWor += completed;
        if (verboseLogs && totalWork > 0) {
            int p = completedWor * 100 / totalWork;
            if (p >= nextWorkLog) {
                buildLogger.addBuildLogEntry(String.format("%s [%d%%]", title, p));
                nextWorkLog += 10;
            }
        }
    }

    @Override
    public void endTask() {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
