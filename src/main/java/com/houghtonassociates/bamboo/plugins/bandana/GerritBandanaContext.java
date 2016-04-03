package com.houghtonassociates.bamboo.plugins.bandana;

import com.atlassian.bamboo.bandana.BambooBandanaContext;
import com.atlassian.plugin.ModuleDescriptor;

public class GerritBandanaContext implements BambooBandanaContext {

    private static final long serialVersionUID = 2823839939046273111L;

    private long planID = 639917L;
    
    private ModuleDescriptor moduleDescriptor;
    
    public GerritBandanaContext(ModuleDescriptor moduleDescriptor) {
    	this.moduleDescriptor=moduleDescriptor;
    }

    @Override
    public boolean hasParentContext() {
        return false;
    }

    @Override
    public BambooBandanaContext getParentContext() {
        return null;
    }

    @Override
    public long getPlanId() {
        return planID;
    }

    @Override
    public String getPluginKey() {
        return moduleDescriptor.getPluginKey();
    }
}
