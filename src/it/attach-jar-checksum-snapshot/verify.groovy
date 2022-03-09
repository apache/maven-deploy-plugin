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

import org.apache.maven.plugins.deploy.Utils;

def pathsInTargetDirectory = [
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.pom",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.pom\\.md5",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.pom\\.sha1",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.jar",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.jar\\.md5",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.jar\\.sha1",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\-sources\\.jar",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\-sources\\.jar\\.md5",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\-sources\\.jar\\.sha1",
    //The following files will be generated. But they can't be check for the checksums
    //cause they contain timestamps which means they change everytime.
    "maven\\-metadata\\.xml",
    "maven\\-metadata\\.xml\\.md5",
    "maven\\-metadata\\.xml\\.sha1",
]

def checkSumsToCheckPaths = [
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.pom",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\.jar",
    "test\\-1\\.0\\-\\d{8}\\.\\d{6}\\-\\d{1}\\-sources\\.jar",
]

// All files are being deployed to that location. See pom.xml
def repository = new File (basedir, "target/remoterepo/org/apache/maven/its/deploy/ajc/test/1.0-SNAPSHOT" )

// Read all files from the target directory.
def filesInDirectory = []
repository.eachFile() { file ->
    filesInDirectory << file.getName()
}

println "Size: ${filesInDirectory.size()} / ${pathsInTargetDirectory.size()}"

// First Step is to check the number of files found in directory against
// the number of files we expect to find.
if (filesInDirectory.size() != pathsInTargetDirectory.size()) {
    throw new Exception( "The number of files in filesInDirectory and the number of files in pathsInTargetDirectory are not equal" );
}

// The following will check for the existence of all given
// files based on the given regular expressions.
// This is needed cause the time stamp in the file name
// changes each time this test will be running.
filesInDirectory.each { existingFile ->
    def result = false
    pathsInTargetDirectory.each { searchItem ->
      def expected = existingFile ==~ searchItem
      println "existingFile: ${existingFile} ${searchItem} expected:${expected}"
      if (expected) {
	result = true
      }
    }

    if (!result) {
      throw new FileNotFoundException ( "Missing: ${existingFile}" )
    }
}

// The following will check the existing checksums.
filesInDirectory.each { existingFile ->
    def result = false
    checkSumsToCheckPaths.each { searchItem ->
      //search for the file name pattern..
      def expected = existingFile ==~ searchItem
      if (expected) {
	println "Verifying ${existingFile}"
	Utils.verifyChecksum( new File(repository, existingFile) );
      }
    }
}

return true;

