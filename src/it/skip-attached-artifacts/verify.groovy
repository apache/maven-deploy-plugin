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

// the repository URL is relative to the basedir of each module on Maven 3 and to the root on Maven 4
def deployed = []
basedir.eachFileRecurse( groovy.io.FileType.FILES ) { file ->
    if ( file.path.replace( '\\', '/' ).contains( '/target/repo/' ) )
    {
        deployed << file.name
    }
}

assert deployed.contains( 'module1-1.0.pom' )
assert deployed.contains( 'module1-1.0.jar' )
assert deployed.contains( 'module2-1.0.pom' )
assert deployed.contains( 'module2-1.0.jar' )

// module1 holds its sources JAR back; the JAR was built and is still attached
assert new File( basedir, 'module1/target/module1-1.0-sources.jar' ).isFile()
assert !deployed.contains( 'module1-1.0-sources.jar' )

// module2, the last module of the build, has no skipAttachedArtifacts and deploys its sources JAR
assert deployed.contains( 'module2-1.0-sources.jar' )

assert new File( basedir, 'build.log' ).text.contains( 'Skipping deployment of attached artifact' )
