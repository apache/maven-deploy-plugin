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
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.util.ChecksumUtils;

/**
 * A utility class to assist testing.
 * used in IntegrationTests like attach-jar-checksum-snapshot, attach-jar-checksum-snapshot
 *
 * @author Benjamin Bentmann
 */
public class Utils
{

    public static final List<String> CHECKSUM_ALGORITHMS = Arrays.asList( "MD5", "SHA-1" );

    /**
     * Verifies the checksum files in the local repo for the given file.
     *
     * @param file The file to verify its checksum with, must not be <code>null</code>.
     * @throws MojoExecutionException In case the checksums were incorrect.
     * @throws IOException If the files couldn't be read.
     */
    public static void verifyChecksum( File file )
        throws MojoExecutionException, IOException
    {
        Map<String, Object> checksums = ChecksumUtils.calc( file, CHECKSUM_ALGORITHMS );
        for ( Map.Entry<String, Object> entry : checksums.entrySet() )
        {
            File cksumFile = new File( file + "." + entry.getKey().toLowerCase().replace( "-", "" ) );
            String actualChecksum = ChecksumUtils.read( cksumFile );
            if ( !actualChecksum.equals( entry.getValue() ) )
            {
                throw new MojoExecutionException( "Incorrect " + entry.getKey() + " checksum for file: " + file );
            }
        }
    }

}
