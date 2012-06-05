/**
 * 
 */
package com.google.gerrit.bamboo.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.atlassian.bamboo.author.AuthorCachingFacade;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plugins.git.GitRepoFactory;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.repository.AbstractStandaloneRepository;
import com.atlassian.bamboo.repository.BranchMergingAwareRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.i18n.I18nBeanFactory;
import com.atlassian.bamboo.utils.i18n.TextProviderAdapter;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.bandana.DefaultBandanaManager;
import com.atlassian.bandana.impl.MemoryBandanaPersister;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.spring.container.LazyComponentReference;
import com.atlassian.util.concurrent.LazyReference;
import com.google.gerrit.bamboo.plugins.dao.GerritChangeVO;
import com.google.gerrit.bamboo.plugins.dao.GerritChangeVO.FileSet;
import com.google.gerrit.bamboo.plugins.dao.GerritDAO;
import com.opensymphony.xwork.TextProvider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;

/**
 * @author Jason Huntley
 *
 */
public class GerritRepositoryAdapter extends AbstractStandaloneRepository implements BranchMergingAwareRepository, GerritEventListener {
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3518800283574344591L;
	
	private static final String REPOSITORY_GERRIT_REPOSITORY_HOSTNAME = "repository.gerrit.hostname";
	private static final String REPOSITORY_GERRIT_REPOSITORY_PORT = "repository.gerrit.port";
    private static final String REPOSITORY_GERRIT_USERNAME = "repository.gerrit.username";
    private static final String REPOSITORY_GERRIT_SSH_KEY = "repository.gerrit.ssh.key";
    private static final String REPOSITORY_GERRIT_SSH_KEY_FILE = "repository.gerrit.ssh.keyfile";
    private static final String REPOSITORY_GERRIT_SSH_PASSPHRASE = "repository.gerrit.ssh.passphrase";
    private static final String TEMPORARY_GERRIT_SSH_PASSPHRASE = "temporary.git.ssh.passphrase";
    private static final String TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE = "temporary.git.ssh.passphrase.change";
    private static final String TEMPORARY_GERRIT_SSH_KEY_FROM_FILE = "temporary.gerrit.ssh.keyfile";
    private static final String TEMPORARY_GERRIT_SSH_KEY_CHANGE = "temporary.gerrit.ssh.key.change";

	private static final Logger log = Logger.getLogger(GerritRepositoryAdapter.class);
	
	private static final LazyComponentReference<I18nBeanFactory> i18nBeanFactoryReference = new LazyComponentReference<I18nBeanFactory>("i18nBeanFactory");
    
    private String hostname;
    private int port=29418;
	private String username;
    private String sshKey;
    private File   sshKeyFile=null;
    private String sshPassphrase;
    
    private GerritDAO gerritDAO=null;
    
    BandanaManager bandanaManager = null;
    
    private final transient LazyReference<StringEncrypter> encrypterRef = new LazyReference<StringEncrypter>()
    {
        @Override
        protected StringEncrypter create() throws Exception
        {
            return new StringEncrypter();
        }
    };
    
	@Override
	public void init(ModuleDescriptor moduleDescriptor) {
		super.init(moduleDescriptor);
		
		bandanaManager = new DefaultBandanaManager(new MemoryBandanaPersister());
	}
	
	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.v2.build.BuildConfigurationAwarePlugin#prepareConfigObject(com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration)
	 */
	@Override
    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration) {
		String strHostName=buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, "").trim();
		buildConfiguration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, strHostName);
		
		String strUserName=buildConfiguration.getString(REPOSITORY_GERRIT_USERNAME, "").trim();
        buildConfiguration.setProperty(REPOSITORY_GERRIT_USERNAME, strUserName);
        
        String strPhrase=buildConfiguration.getString(TEMPORARY_GERRIT_SSH_PASSPHRASE);
        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE)) {
            buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE, encrypterRef.get().encrypt(strPhrase));
        } else if (strPhrase==null){
        	strPhrase=buildConfiguration.getString(REPOSITORY_GERRIT_SSH_PASSPHRASE, "");
        }
        
        String key="";
        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_KEY_CHANGE)) {
            final Object o = buildConfiguration.getProperty(TEMPORARY_GERRIT_SSH_KEY_FROM_FILE);
            if (o instanceof File) {
            	File f=(File) o;
            	
                try {
                    key = FileUtils.readFileToString(f);
                } catch (IOException e) {
                    log.error("Cannot read uploaded ssh key file", e);
                    return;
                }
                
                buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY, encrypterRef.get().encrypt(key));
            } else {
                buildConfiguration.clearProperty(REPOSITORY_GERRIT_SSH_KEY);
            }
        } else if (key.isEmpty()) {
        	key=encrypterRef.get().decrypt(buildConfiguration.getString(REPOSITORY_GERRIT_SSH_KEY, ""));
        }
        
        File file=prepareSSHKeyFile(key);
        
        buildConfiguration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE, file.getAbsolutePath());
    }

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.v2.build.BuildConfigurationAwarePlugin#validate(com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration)
	 */
	@Override
	public ErrorCollection validate(BuildConfiguration buildConfiguration) {
		ErrorCollection errorCollection = super.validate(buildConfiguration);
	
		String hostame = StringUtils.trim(buildConfiguration.getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME));
		
		if (!StringUtils.isNotBlank(hostame)) {
			errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, "Hostname null!");
		}
		
		String username = StringUtils.trim(buildConfiguration.getString(REPOSITORY_GERRIT_USERNAME));
		
		if (!StringUtils.isNotBlank(username)) {
			errorCollection.addError(REPOSITORY_GERRIT_USERNAME, "Username null!");
		}

		if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_KEY_CHANGE)) {
			final Object o = buildConfiguration.getProperty(TEMPORARY_GERRIT_SSH_KEY_FROM_FILE);
		
			if (o==null) {
				errorCollection.addError(REPOSITORY_GERRIT_SSH_KEY, "You must provide your private key to connect!");
			}
		}
		
		String key=encrypterRef.get().decrypt(buildConfiguration.getString(REPOSITORY_GERRIT_SSH_KEY, ""));
		if (!StringUtils.isNotBlank(key)) {
			errorCollection.addError(REPOSITORY_GERRIT_SSH_KEY, "You must provide your private key to connect!");
		}
		
        String strPhrase=buildConfiguration.getString(TEMPORARY_GERRIT_SSH_PASSPHRASE);
        if (buildConfiguration.getBoolean(TEMPORARY_GERRIT_SSH_PASSPHRASE_CHANGE)) {
        	strPhrase=encrypterRef.get().encrypt(strPhrase);
        } else if (strPhrase==null){
        	strPhrase=buildConfiguration.getString(REPOSITORY_GERRIT_SSH_PASSPHRASE, "");
        }
		
        try {
        	testGerritConnection(key, hostame, port, username, strPhrase);
		} catch (RepositoryException e) {
			errorCollection.addError(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, e.getMessage());
		}
		
        return errorCollection;
	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.fieldvalue.ConvertibleFromConfig#populateFromConfig(org.apache.commons.configuration.HierarchicalConfiguration)
	 */
	@Override
	public void populateFromConfig(HierarchicalConfiguration config) {
        super.populateFromConfig(config);
        hostname = StringUtils.trimToEmpty(config.getString(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME));
        username = config.getString(REPOSITORY_GERRIT_USERNAME);
        sshKey = config.getString(REPOSITORY_GERRIT_SSH_KEY, "");
        sshKeyFile=new File(config.getString(REPOSITORY_GERRIT_SSH_KEY_FILE));
        sshPassphrase = config.getString(REPOSITORY_GERRIT_SSH_PASSPHRASE);
        port = config.getInt(REPOSITORY_GERRIT_REPOSITORY_PORT, 29418);
	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.fieldvalue.ConvertibleFromConfig#toConfiguration()
	 */
	@Override
	public HierarchicalConfiguration toConfiguration() {
		HierarchicalConfiguration configuration = super.toConfiguration();
		
		configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_HOSTNAME, hostname);
        configuration.setProperty(REPOSITORY_GERRIT_USERNAME, username);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY, sshKey);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_PASSPHRASE, sshPassphrase);
        configuration.setProperty(REPOSITORY_GERRIT_SSH_KEY_FILE, sshKeyFile);
        configuration.setProperty(REPOSITORY_GERRIT_REPOSITORY_PORT, port);
		
		return configuration;
	}
	
    @Override
    public synchronized void setTextProvider(TextProvider textProvider) {
        if (this.textProvider==null) {
        	super.setTextProvider(textProvider);
        	this.textProvider=textProvider;
        }

        if (getName() == null) {
            I18nBeanFactory i18nBeanFactory = i18nBeanFactoryReference.get();
            this.textProvider = new TextProviderAdapter(i18nBeanFactory.getI18nBean(Locale.getDefault()));
        }
    }
    
    public Authentication createGerritCredentials(File sshKeyFile, String strUsername, String phrase) {
		return new Authentication(sshKeyFile, strUsername, phrase);
    }
    
    public File prepareSSHKeyFile(String sshKey) {
    	File parentDirectoryFile=this.buildDirectoryManager.getBaseBuildWorkingDirectory();
    	String parentDirectory=parentDirectoryFile.getAbsolutePath();
    	String workingDirectory=parentDirectory+File.separator+this.getShortKey();
		File f = new File(workingDirectory+"/GerritSSHKey.txt");
		
		try {
			FileUtils.writeStringToFile(f, sshKey);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
		
		return f;
    }
    
    public void testGerritConnection(String sshKey, String strHost, 
    		int port, String strUsername, String phrase) throws RepositoryException {
    	SshConnection sshConnection=null;
    	
		Authentication auth=new Authentication(prepareSSHKeyFile(sshKey), strUsername, phrase);
    	
    	try {
			sshConnection=SshConnectionFactory.getConnection(strHost, port, auth);
		} catch (IOException e) {
			throw new RepositoryException("Failed to establish connection to Gerrit!");
		}
    	
    	if (!sshConnection.isConnected()) {
    		throw new RepositoryException("Failed to establish connection to Gerrit!");
    	} else {
    		sshConnection.disconnect();
    	}
    }
    
    public GerritDAO getGerritDAO() {
    	if (gerritDAO==null) {
    		Authentication auth=createGerritCredentials(sshKeyFile, username, sshPassphrase);
    		gerritDAO=new GerritDAO(hostname, port,  auth);
    	}
    	
    	return gerritDAO;
    }
    
    /*public GerritHandler manageGerritHandler(String sshKey, String strHost, 
    		int port, String strUsername, String phrase, boolean test, 
    		boolean reset) 
    				throws RepositoryException {
    	this.updateCredentials(sshKey, strHost, strUsername, phrase);
    	
    	if (test)
    		testGerritConnection(strHost, port);
    	
    	if (gHandler==null) {
    		synchronized (GerritRepositoryAdapter.class) { 
	    		gHandler=new GerritHandler(strHost, port, authentication, NUM_WORKER_THREADS);
				gHandler.addListener(this);
				gHandler.addListener(new ConnectionListener() {
					@Override
					public void connectionDown() {
						log.error("Gerrit connection down.");
						gHandler.shutdown(false);
					}
	
					@Override
					public void connectionEstablished() {
						log.info("Gerrit connection established!");
					}
				});
    		}
    	}
    	
    	if (reset) {
	    	if (gHandler.isAlive()) {
	    		gHandler.shutdown(true);
	    	}
	    	
	    	gHandler.start();
    	}
    	
    	return gHandler;
    }*/

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.v2.build.repository.RepositoryV2#collectChangesSinceLastBuild(java.lang.String, java.lang.String)
	 */
	@Override
	public BuildRepositoryChanges collectChangesSinceLastBuild(String planKey,
			String lastVcsRevisionKey) throws RepositoryException {
		final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(PlanKeys.getPlanKey(planKey));
		List<Commit> commits = new ArrayList<Commit>();
		
		GerritChangeVO change=getGerritDAO().getLastUnverifiedUpdate();
		
		CommitImpl commit = new CommitImpl();
        commit.setComment(change.getSubject());
        commit.setAuthor(new AuthorCachingFacade(change.getOwnerName()));
        commit.setDate(change.getLastUpdate());
        commit.setChangeSetId(change.getId());
        
        Set<FileSet> fileSets=change.getCurrentPatchSet().getFileSets();
        
        for(FileSet fileSet:fileSets) {
        	CommitFile file=new CommitFileImpl(change.getId(), fileSet.getFile());
        	commit.addFile(file);
        }
        
        commits.add(commit);
        
        BuildRepositoryChanges buildChanges = new BuildRepositoryChangesImpl(change.getLastRevision(), commits);
        
        return buildChanges;
	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.v2.build.repository.RepositoryV2#isRepositoryDifferent(com.atlassian.bamboo.repository.Repository)
	 */
	@Override
	public boolean isRepositoryDifferent(Repository repository) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.v2.build.ConfigurablePlugin#customizeBuildRequirements(com.atlassian.bamboo.plan.PlanKey, com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration, com.atlassian.bamboo.v2.build.agent.capability.RequirementSet)
	 */
	@Override
	public void customizeBuildRequirements(PlanKey planKey,
			BuildConfiguration buildConfiguration, RequirementSet requirementSet) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.v2.build.ConfigurablePlugin#removeBuildRequirements(com.atlassian.bamboo.plan.PlanKey, com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration, com.atlassian.bamboo.v2.build.agent.capability.RequirementSet)
	 */
	@Override
	public void removeBuildRequirements(PlanKey planKey,
			BuildConfiguration buildConfiguration, RequirementSet requirementSet) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.v2.build.BuildConfigurationAwarePlugin#addDefaultValues(com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration)
	 */
	@Override
	public void addDefaultValues(BuildConfiguration buildConfiguration) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.repository.Repository#checkConnection()
	 */
	@Override
	public ErrorCollection checkConnection() {
		ErrorCollection errorCollection = super.checkConnection();
		
		return errorCollection;
	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.repository.Repository#getHost()
	 */
	@Override
	public String getHost() {
		return "";
	}
	
    public int getPort() {
		return port;
	}
	
    public String getHostname() {
        return hostname;
    }

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.repository.Repository#getMinimalEditHtml(com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration)
	 */
	@Override
	public String getMinimalEditHtml(BuildConfiguration buildConfiguration) {
		// TODO Auto-generated method stub
		return super.getMinimalEditHtml(buildConfiguration);
	}

	/* (non-Javadoc)
	 * @see com.atlassian.bamboo.repository.Repository#getName()
	 */
	@Override
	public String getName() {
		return textProvider.getText("repository.gerrit.name");
	}

	@Override
	public VcsBranch getVcsBranch() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setVcsBranch(VcsBranch branch) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String retrieveSourceCode(BuildContext buildContext,
			String vcsRevisionKey, File sourceDirectory)
			throws RepositoryException {

        String gitRepoUrl="ssh://" + hostname + "/" + "";
        
        GitRepository gitRepository = GitRepoFactory.getSSHGitRepository(gitRepoUrl, username, 
        		"", "refs/for/paster", sshKey, sshPassphrase, true, 
        		false, 10000, false);
        
        gitRepository.setTextProvider(this.textProvider);
		
		return gitRepository.retrieveSourceCode(buildContext, vcsRevisionKey, sourceDirectory);
	}

	@Override
	public boolean isMergingSupported() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean mergeWorkspaceWith(BuildContext buildContext,
			File checkoutDirectory, String targetRevision)
			throws RepositoryException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String retrieveSourceCode(BuildContext buildContext,
			String vcsRevisionKey, File sourceDirectory, int depth)
			throws RepositoryException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void gerritEvent(GerritEvent arg0) {
		log.debug(arg0.toString());
	}

	@Override
	public void gerritEvent(PatchsetCreated arg0) {
		log.debug(arg0.toString());
	}

	@Override
	public void gerritEvent(ChangeAbandoned arg0) {
		log.debug(arg0.toString());
	}
}
