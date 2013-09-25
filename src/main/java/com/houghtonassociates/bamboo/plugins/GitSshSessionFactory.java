package com.houghtonassociates.bamboo.plugins;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;

/**
 * User: slawomir.jaranowski
 */
public class GitSshSessionFactory extends JschConfigSessionFactory {

    final private byte[] sshKey;
    final private byte[] sshPassphrase;

    public GitSshSessionFactory(String sshKey, String sshPassphrase) {
        this.sshKey = sshKey.getBytes();
        this.sshPassphrase = sshPassphrase.getBytes();
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session)
    {
        session.setConfig("StrictHostKeyChecking", "no");
    }

    protected JSch getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
        JSch jsch = super.getJSch(hc, fs);
        jsch.removeAllIdentity();
        jsch.addIdentity("identityName", sshKey, null, sshPassphrase);
        return jsch;
    }
}
