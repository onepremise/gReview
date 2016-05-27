/*
 * Houghton Associates Framework
 * http://www.houghtonassociates.com
 * 
 * Copyright 2013 Houghton Associates, Inc.
 */
package com.houghtonassociates.bamboo.plugins.dao.jgit;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.SubmoduleInitCommand;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;

import com.atlassian.bamboo.repository.RepositoryException;
import com.houghtonassociates.bamboo.plugins.dao.GerritConfig;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * @author Jason Huntley
 * 
 */
public class JGitRepository {

    private SshSessionFactory factory = null;

    private File fHandle = null;
    private Repository repository = null;
    private Git git = null;

    private GerritConfig accessData = null;
    private Transport transport = null;
    private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

    private String remote = Constants.DEFAULT_REMOTE_NAME;
    private String branch = Constants.HEAD;

    public void open(String filePath) throws RepositoryException {
        this.open(filePath, false);
    }

    public void
                    open(String filePath, boolean recreate) throws RepositoryException {
        File f = new File(filePath, Constants.DOT_GIT);
        open(f, recreate);
    }

    public void open(File repoLoc) throws RepositoryException {
        this.open(repoLoc, false);
    }

    public void open(File repoLoc, boolean recreate) throws RepositoryException {
        if (!repoLoc.getAbsolutePath().endsWith(Constants.DOT_GIT))
            fHandle = new File(repoLoc, Constants.DOT_GIT);
        else
            fHandle = repoLoc;

        try {
            if (recreate && fHandle.exists()) {
                FileUtils.deleteDirectory(fHandle.getParentFile());
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();

            repository =
                builder.setGitDir(fHandle).readEnvironment().findGitDir()
                    .setup().build();

            if (recreate || !fHandle.exists())
                repository.create();

            git = new Git(repository);

            Git.init().call();
        } catch (IOException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }
    }

    public void close() {
        closeTransport();

        if (repository != null)
            repository.close();
    }

    public void setAccessData(GerritConfig grad) {
        accessData = grad;
        factory = null;
    }

    public ProgressMonitor getMonitor() {
        return monitor;
    }

    public void setMonitor(ProgressMonitor monitor) {
        this.monitor = monitor;
    }

    private void initSSH() {
        if (factory == null) {
            factory = new JschConfigSessionFactory() {

                public void configure(Host hc, Session session) {
                    session.setConfig("StrictHostKeyChecking", "no");
                }

                @Override
                protected JSch
                                getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
                    JSch jsch = super.getJSch(hc, fs);
                    jsch.removeAllIdentity();
                    if (StringUtils.isNotEmpty(accessData.getSshKey())) {
                        jsch.addIdentity("identityName", accessData.getSshKey()
                            .getBytes(), null, accessData.getSshPassphrase()
                            .getBytes());
                    }
                    return jsch;
                }
            };
        }
    }

    public Transport openSSHTransport() throws RepositoryException {
        return openSSHTransport(accessData.getRepositoryUrl());
    }

    public Transport openSSHTransport(String url) throws RepositoryException {
        try {
            initSSH();

            transport = Transport.open(git.getRepository(), url);

            ((SshTransport) transport).setSshSessionFactory(factory);
        } catch (NotSupportedException e) {
            throw new RepositoryException(e);
        } catch (TransportException e) {
            throw new RepositoryException(e);
        } catch (URISyntaxException e) {
            throw new RepositoryException(e);
        }

        return transport;
    }

    public void closeTransport() {
        if (transport != null)
            transport.close();
    }

    // WIP - not functional
    private void
                    addMergeConfig(Repository clonedRepo, Ref head) throws IOException {

        String branchName = Repository.shortenRefName(head.getName());
        clonedRepo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
            branchName, ConfigConstants.CONFIG_KEY_REMOTE, remote);
        clonedRepo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
            branchName, ConfigConstants.CONFIG_KEY_MERGE, head.getName());
        String autosetupRebase =
            clonedRepo.getConfig().getString(
                ConfigConstants.CONFIG_BRANCH_SECTION, null,
                ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE);
        if (ConfigConstants.CONFIG_KEY_ALWAYS.equals(autosetupRebase)
            || ConfigConstants.CONFIG_KEY_REMOTE.equals(autosetupRebase))
            clonedRepo.getConfig().setBoolean(
                ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
                ConfigConstants.CONFIG_KEY_REBASE, true);
        clonedRepo.getConfig().save();
    }

    // WIP - not functional
    private RevCommit
                    parseCommit(final Repository clonedRepo, final Ref ref) throws MissingObjectException,
                                    IncorrectObjectTypeException,
                                    IOException {
        final RevWalk rw = new RevWalk(clonedRepo);
        final RevCommit commit;
        try {
            commit = rw.parseCommit(ref.getObjectId());
        } finally {
            rw.close();
        }
        return commit;
    }

    // WIP - not functional
    private Ref findBranchToCheckout(FetchResult result) {
        final Ref idHEAD = result.getAdvertisedRef(Constants.HEAD);
        if (idHEAD == null)
            return null;

        Ref master =
            result.getAdvertisedRef(Constants.R_HEADS + Constants.MASTER);
        if (master != null && master.getObjectId().equals(idHEAD.getObjectId()))
            return master;

        Ref foundBranch = null;
        for (final Ref r : result.getAdvertisedRefs()) {
            final String n = r.getName();
            if (!n.startsWith(Constants.R_HEADS))
                continue;
            if (r.getObjectId().equals(idHEAD.getObjectId())) {
                foundBranch = r;
                break;
            }
        }
        return foundBranch;
    }

    // WIP - not functional
    public void clone_() throws RepositoryException {
        boolean bare = false;

        try {
            final String dst =
                (bare ? Constants.R_HEADS : Constants.R_REMOTES + remote + "/")
                    + "*";
            RefSpec refSpec = new RefSpec();
            refSpec = refSpec.setForceUpdate(true);
            refSpec =
                refSpec.setSourceDestination(Constants.R_HEADS + "*", dst);

            FetchResult result =
                transport.fetch(monitor, Arrays.asList(refSpec));

            checkout(result);
        } catch (NotSupportedException e) {
            throw new RepositoryException(e);
        } catch (TransportException e) {
            throw new RepositoryException(e);
        }
    }

    public Ref
                    getHeadRefForBranch(String branchName) throws RepositoryException {
        Ref ref = null;
        FetchConnection c;

        try {
            c = transport.openFetch();

            ref = c.getRef(Constants.R_HEADS + branchName);

        } catch (NotSupportedException e) {
            throw new RepositoryException(e);
        } catch (TransportException e) {
            throw new RepositoryException(e);
        }

        return ref;
    }

    public RevCommit resolveRev(String rev) throws RepositoryException {
        RevCommit c = null;
        RevWalk rw = new RevWalk(git.getRepository());

        try {
            ObjectId obj = git.getRepository().resolve(rev);
            c = rw.parseCommit(obj);
        } catch (Exception e) {
            throw new RepositoryException(e);
        } finally {
            rw.close();
        }

        return c;
    }

    public String
                    getLatestRevisionForBranch(String branchName) throws RepositoryException {
        String revision = null;
        Ref ref = getHeadRefForBranch(branchName);

        if (ref != null)
            revision = ref.getObjectId().getName();

        return revision;
    }

    public List<Ref> lsLocalBranches(ListMode type) throws RepositoryException {
        List<Ref> call = new ArrayList<Ref>();

        try {
            call = new Git(repository).branchList().setListMode(type).call();
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }

        return call;
    }

    public Collection<Ref> lsRemoteBranches() throws RepositoryException {
        Collection<Ref> call = new ArrayList<Ref>();

        FetchConnection c;

        try {
            c = transport.openFetch();

            for (final Ref r : c.getRefs()) {
                final String n = r.getName();

                if (!n.startsWith(Constants.R_HEADS))
                    continue;

                call.add(r);
            }

        } catch (NotSupportedException e) {
            throw new RepositoryException(e);
        } catch (TransportException e) {
            throw new RepositoryException(e);
        }

        return call;
    }

    public Collection<Ref> lsRemoteTags() throws RepositoryException {
        Collection<Ref> call = new ArrayList<Ref>();

        FetchConnection c;

        try {
            c = transport.openFetch();

            for (final Ref r : c.getRefs()) {
                final String n = r.getName();

                if (!n.startsWith(Constants.R_TAGS))
                    continue;

                call.add(r);
            }

        } catch (NotSupportedException e) {
            throw new RepositoryException(e);
        } catch (TransportException e) {
            throw new RepositoryException(e);
        }

        return call;
    }

    public FetchResult fetch(String targetRevision) throws RepositoryException {
        return fetch(targetRevision, 0);
    }

    public FetchResult
                    fetch(String targetRevision, int depth) throws RepositoryException {
        FetchResult result = null;
        RefSpec refSpec =
            new RefSpec().setForceUpdate(true).setSourceDestination(
                targetRevision, targetRevision);

        try {
            result = transport.fetch(monitor, Arrays.asList(refSpec));
        } catch (NotSupportedException e) {
            throw new RepositoryException(e);
        } catch (TransportException e) {
            throw new RepositoryException(e);
        }

        return result;
    }

    public MergeResult merge(String commit) throws RepositoryException {
        AnyObjectId id = null;
        try {
            id = git.getRepository().resolve(commit);
        } catch (IOException e) {
            throw new RepositoryException(e);
        }

        return merge(id);
    }

    public MergeResult merge(AnyObjectId commit) throws RepositoryException {
        MergeCommand mgCmd = git.merge();

        mgCmd.include(commit);

        MergeResult res = null;

        try {
            res = mgCmd.call();
        } catch (NoHeadException e) {
            throw new RepositoryException(e);
        } catch (ConcurrentRefUpdateException e) {
            throw new RepositoryException(e);
        } catch (CheckoutConflictException e) {
            throw new RepositoryException(e);
        } catch (InvalidMergeHeadsException e) {
            throw new RepositoryException(e);
        } catch (WrongRepositoryStateException e) {
            throw new RepositoryException(e);
        } catch (NoMessageException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }

        return res;
    }

    public MergeResult merge(Ref commit) throws RepositoryException {
        MergeCommand mgCmd = git.merge();

        mgCmd.include(commit);

        MergeResult res = null;

        try {
            res = mgCmd.call();
        } catch (NoHeadException e) {
            throw new RepositoryException(e);
        } catch (ConcurrentRefUpdateException e) {
            throw new RepositoryException(e);
        } catch (CheckoutConflictException e) {
            throw new RepositoryException(e);
        } catch (InvalidMergeHeadsException e) {
            throw new RepositoryException(e);
        } catch (WrongRepositoryStateException e) {
            throw new RepositoryException(e);
        } catch (NoMessageException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }

        return res;
    }

    public void createBranch(String branchName) throws RepositoryException {
        try {
            git.branchCreate().setName(branchName).call();
        } catch (RefAlreadyExistsException e) {
            throw new RepositoryException(e);
        } catch (RefNotFoundException e) {
            throw new RepositoryException(e);
        } catch (InvalidRefNameException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }
    }

    private void checkout(FetchResult result) {
        boolean bare = false;
        Repository clonedRepo = git.getRepository();
        Ref head = null;

        try {
            if (branch.equals(Constants.HEAD)) {
                Ref foundBranch = findBranchToCheckout(result);
                if (foundBranch != null)
                    head = foundBranch;
            }
            if (head == null) {
                head = result.getAdvertisedRef(branch);
                if (head == null)
                    head = result.getAdvertisedRef(Constants.R_HEADS + branch);
                if (head == null)
                    head = result.getAdvertisedRef(Constants.R_TAGS + branch);
            }

            if (head == null || head.getObjectId() == null)
                return; // throw exception?

            if (head.getName().startsWith(Constants.R_HEADS)) {
                final RefUpdate newHead = clonedRepo.updateRef(Constants.HEAD);
                newHead.disableRefLog();
                newHead.link(head.getName());
                addMergeConfig(clonedRepo, head);
            }

            final RevCommit commit = parseCommit(clonedRepo, head);

            boolean detached = !head.getName().startsWith(Constants.R_HEADS);
            RefUpdate u = clonedRepo.updateRef(Constants.HEAD, detached);
            u.setNewObjectId(commit.getId());
            u.forceUpdate();

            if (!bare) {
                DirCache dc = clonedRepo.lockDirCache();
                DirCacheCheckout co =
                    new DirCacheCheckout(clonedRepo, dc, commit.getTree());
                co.checkout();

                if (accessData.isUseSubmodules())
                    cloneSubmodules(clonedRepo);
            }
        } catch (Exception e) {

        }
    }

    public void checkout(String targetRevision) throws RepositoryException {
        CheckoutCommand co = git.checkout();
        co.setName(targetRevision);
        try {
            co.call();

            if (accessData.isUseSubmodules())
                cloneSubmodules(git.getRepository());
        } catch (RefAlreadyExistsException e) {
            throw new RepositoryException(e);
        } catch (RefNotFoundException e) {
            throw new RepositoryException(e);
        } catch (InvalidRefNameException e) {
            throw new RepositoryException(e);
        } catch (CheckoutConflictException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }
    }

    private void
                    cloneSubmodules(Repository clonedRepo) throws RepositoryException {
        try {
            SubmoduleInitCommand init = new SubmoduleInitCommand(clonedRepo);
            if (init.call().isEmpty())
                return;

            SubmoduleUpdateCommand update =
                new SubmoduleUpdateCommand(clonedRepo);

            // configure(update);

            update.setProgressMonitor(monitor);
            if (!update.call().isEmpty()) {
                SubmoduleWalk walk;

                walk = SubmoduleWalk.forIndex(clonedRepo);

                while (walk.next()) {
                    Repository subRepo = walk.getRepository();
                    if (subRepo != null) {
                        try {
                            cloneSubmodules(subRepo);
                        } finally {
                            subRepo.close();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }
    }

    public void add(String criteria) throws RepositoryException {
        try {
            git.add().addFilepattern("project.config").call();
        } catch (NoFilepatternException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }
    }

    public RevCommit commit(String msg) throws RepositoryException {
        RevCommit revCommit = null;

        try {
            revCommit = git.commit().setMessage(msg).call();
        } catch (NoHeadException e) {
            throw new RepositoryException(e);
        } catch (NoMessageException e) {
            throw new RepositoryException(e);
        } catch (UnmergedPathsException e) {
            throw new RepositoryException(e);
        } catch (ConcurrentRefUpdateException e) {
            throw new RepositoryException(e);
        } catch (WrongRepositoryStateException e) {
            throw new RepositoryException(e);
        } catch (GitAPIException e) {
            throw new RepositoryException(e);
        }

        return revCommit;
    }

    public PushResult
                    push(File directory, String targetRef) throws RepositoryException {
        PushResult r = null;
        Collection<RemoteRefUpdate> rru = null;
        try {
            RefSpec refSpec =
                new RefSpec().setForceUpdate(true).setSource(targetRef)
                    .setDestination(targetRef);

            rru =
                transport.findRemoteRefUpdatesFor(Collections
                    .singleton(refSpec));

            r = transport.push(monitor, rru);
        } catch (IOException e) {
            throw new RepositoryException(e);
        }

        return r;
    }

    public PushResult
                    push(RevCommit revCommit, String targetRevision) throws RepositoryException {
        PushResult r = null;
        RemoteRefUpdate rru = null;
        try {
            rru =
                new RemoteRefUpdate(git.getRepository(), revCommit.name(),
                    targetRevision, true, null, null);

            List<RemoteRefUpdate> list = new ArrayList<RemoteRefUpdate>();
            list.add(rru);

            r = transport.push(monitor, list);
        } catch (IOException e) {
            throw new RepositoryException(e);
        }

        return r;
    }

    public PushResult
                    commitPush(String msg, String targetRevision) throws RepositoryException {
        RevCommit c = commit(msg);
        return push(c, targetRevision);
    }
}
