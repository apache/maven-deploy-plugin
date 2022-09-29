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

class LogInspector
{
    File log;
    int index;
    LogInspector( File log )
    {
        this.log = log;
        this.index = 0;
    }
    boolean containsAfter( CharSequence text )
    {
        int newIdx = log.text.indexOf( text, index + 1 )
        if ( newIdx > index)
        {
            index = newIdx
            return true
        }
        return false
    }
    String toString ()
    {
        return "Log file ${log} after index ${index}."
    }
}


assert new File( basedir, "repo/org/apache/maven/its/deploy/comparepom/test/maven-metadata.xml" ).exists()
assert new File( basedir, "repo/org/apache/maven/its/deploy/comparepom/test/1.0/test-1.0-first.jar" ).exists()
assert new File( basedir, "repo/org/apache/maven/its/deploy/comparepom/test/1.0/test-1.0-second.jar").exists()

File deployedPom = new File( basedir, "repo/org/apache/maven/its/deploy/comparepom/test/1.0/test-1.0.pom" )
assert deployedPom.exists()
assert ! deployedPom.text.contains("Modified POM!")

File installedPom = new File( localRepositoryPath, "org/apache/maven/its/deploy/comparepom/test/1.0/test-1.0.pom" )
assert installedPom.exists()
assert installedPom.text.contains("Modified POM!")

File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()

// Inspect log
LogInspector li = new LogInspector( buildLog )
String groupUrl = "file:///${basedir}/repo/org/apache/maven/its/deploy/comparepom"

// First run: The POM tried to be downloaded and uploaded:
assert li.containsAfter( "[INFO] Downloading from it: ${groupUrl}/test/1.0/test-1.0.pom" )
assert li.containsAfter( "[INFO] Uploaded to it: ${groupUrl}/test/1.0/test-1.0.pom" )

// After that, it is never tried to be uploaded:
assert -1 == buildLog.text.indexOf( "[INFO] Uploading to it: ${groupUrl}/test/1.0/test-1.0.pom", li.index + 1 )

// Second run: POM is downloaded and not uploaded:
assert li.containsAfter( "[INFO] Downloaded from it: ${groupUrl}/test/1.0/test-1.0.pom" )
assert li.containsAfter( "[INFO] Not deploying POM, since deployed POM is equal to current POM." )

// Third run: POM is downloaded, nothing is tried to be uploaded after that, and the build fails with error:
assert li.containsAfter( "[INFO] Downloaded from it: ${groupUrl}/test/1.0/test-1.0.pom" )
assert -1 == buildLog.text.indexOf( "[INFO] Uploading to", li.index + 1 )
assert li.containsAfter( "[ERROR] Project version org.apache.maven.its.deploy.comparepom:test:1.0 already deployed with a differing POM." )
