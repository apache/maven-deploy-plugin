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
import groovy.json.JsonSlurper

import java.nio.file.Files
import java.util.zip.ZipFile

String getBaseUrl() {
  "http://localhost:8088"
}

URLConnection conn = new URI("$baseUrl/getBundleInfo").toURL().openConnection()
conn.setRequestMethod("GET")
conn.connect()
def json = conn.inputStream.text
List bundles = new JsonSlurper().parseText(json)
println "verify: Received bundles: $bundles"

// Check that exactly 3 bundles was uploaded
assert bundles.size() == 3 : "Expected exactly 3 bundles, but got ${bundles.size()}"

String groupId = "se.alipsa.maven.example"
def expectedAggregatorEntries = { String artifactId, String version ->
  String basePath = "${groupId.replace('.', '/')}/${artifactId}/${version}/"

  String artifactPath = basePath + artifactId + '-' + version
  [
      artifactPath + '.pom',
      artifactPath +'.pom.asc',
      artifactPath +'.pom.md5',
      artifactPath +'.pom.sha1',
      artifactPath +'.pom.sha256'
  ]
}

def expectedFullEntries = {String artifactId, String version ->
  String basePath = "${groupId.replace('.', '/')}/${artifactId}/${version}/"
  String artifactPath = basePath + artifactId + '-' + version
  [
      artifactPath + '.jar',
      artifactPath + '.jar.asc',
      artifactPath + '.jar.md5',
      artifactPath + '.jar.sha1',
      artifactPath + '.jar.sha256',
      artifactPath + '-sources.jar',
      artifactPath + '-sources.jar.asc',
      artifactPath + '-sources.jar.md5',
      artifactPath + '-sources.jar.sha1',
      artifactPath + '-sources.jar.sha256',
      artifactPath + '-javadoc.jar',
      artifactPath + '-javadoc.jar.asc',
      artifactPath + '-javadoc.jar.md5',
      artifactPath + '-javadoc.jar.sha1',
      artifactPath + '-javadoc.jar.sha256'
  ] + expectedAggregatorEntries(artifactId, version)
}

bundles.each { Map info ->
  File zipFile = download(info.deploymentId as String)
  String fileName = info.fileName
  if (fileName.contains('parent')) {
    checkZipContent(zipFile, "publishing-example-parent", "1.0.0", expectedAggregatorEntries)
  } else if (fileName.contains("common")) {
    checkZipContent(zipFile, "publishing-example-common", "1.0.0", expectedFullEntries)
  } else if(fileName.contains("subA")) {
    checkZipContent(zipFile, "publishing-example-subA", "1.0.0", expectedFullEntries)
  } else {
    throw new Exception("Unexpected bundle name: ${info.fileName}")
  }
  zipFile.deleteOnExit()
}

// download zip content
File download(String deploymentId) {
  def conn = new URI("$baseUrl/getBundle?deploymentId=$deploymentId").toURL().openConnection()
  conn.setRequestMethod("GET")
  conn.connect()
  def zip = Files.createTempFile("bundle", ".zip")
  def zipFile = zip.toFile()
  if (zipFile.exists()) {
    zipFile.delete()
  }
  Files.copy(conn.inputStream, zip)
  zipFile
}

static def checkZipContent(File zipFile, String artifactId, String version, Closure expectedMethod) {
  println "Checking content of $zipFile"
  List<String> expectedEntries = expectedMethod(artifactId, version)
  List<String> actualEntries
  try (ZipFile zip = new ZipFile(zipFile)) {
    actualEntries = zip.entries().collect { it.name }
  }
  expectedEntries.each {
    //println "  - checking $it"
    assert actualEntries.contains(it) : "Expected entry not found in $zipFile.name: $it"
  }
  assert expectedEntries.size() == actualEntries.size() : "Mismatch in number of entries in ZIP"
}


// Shut down the server
try {
  conn = new URI("$baseUrl/shutdown").toURL().openConnection()
  conn.setRequestMethod("POST")
  conn.connect()
  println conn.inputStream.text
} catch (Exception e) {
  println "Shutdown failed: ${e.message}"
}