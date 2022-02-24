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

import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.RepositoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for Deploy mojo's.
 */
public abstract class AbstractDeployMojo implements Mojo
{

    protected Logger logger = LoggerFactory.getLogger( getClass() );

    /**
     * Flag whether Maven is currently in online/offline mode.
     */
    @Parameter( defaultValue = "${settings.offline}", readonly = true )
    private boolean offline;

    /**
     * Parameter used to control how many times a failed deployment will be retried before giving up and failing. If a
     * value outside the range 1-10 is specified it will be pulled to the nearest value within the range 1-10.
     * 
     * @since 2.7
     */
    @Parameter( property = "retryFailedDeploymentCount", defaultValue = "1" )
    private int retryFailedDeploymentCount;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private Session session;
    
    /* Setters and Getters */

    void failIfOffline()
        throws MojoException
    {
        if ( offline )
        {
            throw new MojoException( "Cannot deploy artifacts when Maven is in offline mode" );
        }
    }

    int getRetryFailedDeploymentCount()
    {
        return retryFailedDeploymentCount;
    }

    protected RemoteRepository createDeploymentArtifactRepository( String id, String url )
    {
        return getSession().getService( RepositoryFactory.class )
                .createRemote( id, url );
    }
    
    protected final Session getSession()
    {
        return session;
    }
}
