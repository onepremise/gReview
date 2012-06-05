/**
 * 
 */
package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plan.branch.VcsBranchImpl;

/**
 * @author Jason Huntley
 *
 */
public class GitRepoFactory {
	public static GitRepository getSSHGitRepository(String url, String username, 
			String password, String branch, String sshKey, String sshPassphrase, 
			boolean useShallowClones, boolean useSubmodules, int timeout, 
			boolean verbose) {
		GitRepository gitRepository = new GitRepository();
		
        gitRepository.accessData.repositoryUrl = url;
        gitRepository.accessData.username = username;
        gitRepository.accessData.password = password;
        gitRepository.accessData.branch = branch;
        gitRepository.accessData.sshKey = "";
        gitRepository.accessData.sshPassphrase = "";
        gitRepository.accessData.authenticationType = GitAuthenticationType.SSH_KEYPAIR;
        gitRepository.accessData.useShallowClones = useShallowClones;
        gitRepository.accessData.useSubmodules = useSubmodules;
        gitRepository.accessData.commandTimeout = timeout;
        gitRepository.accessData.verboseLogs = verbose;

        gitRepository.setVcsBranch(new VcsBranchImpl(branch));
        
        return gitRepository;
	}
}
