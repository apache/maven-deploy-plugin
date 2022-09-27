package org.apache.maven.plugins.deploy;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.AbstractForwardingRepositorySystemSession;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.WorkspaceReader;

/**
 *
 */
class TempLocalRepoSession extends AbstractForwardingRepositorySystemSession implements Closeable
{
    private final RepositorySystemSession origSession;
    private final RepositoryCache cache;
    private final File tempBasedir;
    private LocalRepositoryManager lrm;

    private TempLocalRepoSession( RepositorySystemSession origSession, File tempBasedir )
    {
        this.origSession = origSession;
        this.cache = new DefaultRepositoryCache();
        this.tempBasedir = tempBasedir;
        this.lrm = null;
    }

    public static TempLocalRepoSession create( RepositorySystemSession origSession, RepositorySystem repoSystem )
            throws IOException
    {
        // Place a temporary local repository next to the regular one, as done on the maven-assembly-plugin.
        File origBasedir = origSession.getLocalRepository().getBasedir();
        Path parentDir = origBasedir.getParentFile().toPath();
        File newBasedir = Files.createTempDirectory( parentDir, origBasedir.getName() ).toFile();

        TempLocalRepoSession newSession = new TempLocalRepoSession( origSession, newBasedir );

        String contentType = origSession.getLocalRepository().getContentType();
        String repositoryType = "enhanced".equals( contentType ) ? "default" : contentType;
        LocalRepository localRepository = new LocalRepository( newBasedir, repositoryType );

        newSession.lrm = repoSystem.newLocalRepositoryManager( newSession, localRepository );

        return newSession;
    }

    @Override
    protected RepositorySystemSession getSession()
    {
        return origSession;
    }

    @Override
    public RepositoryCache getCache()
    {
        return cache;
    }

    @Override
    public LocalRepositoryManager getLocalRepositoryManager()
    {
        return lrm;
    }

    @Override
    public WorkspaceReader getWorkspaceReader()
    {
        return null;
    }

    @Override
    public void close()
            throws IOException
    {
        FileUtils.deleteDirectory( tempBasedir );
    }
}
