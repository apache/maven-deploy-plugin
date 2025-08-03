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
import java.util.zip.ZipFile

// Verify that bundles exists
def aggregatorZip = new File(getBaseDir(), "target/publishing-example-parent-1.0.0-bundle.zip")
def commonZip = new File(getBaseDir(), "common/target/publishing-example-common-1.0.0-bundle.zip")
def subAZip = new File(getBaseDir(), "subA/target/publishing-example-subA-1.0.0-bundle.zip")
assert aggregatorZip.exists()
assert commonZip.exists()
assert subAZip.exists()

// Check the content of each file
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
checkZipContent(aggregatorZip, "publishing-example-parent", "1.0.0", expectedAggregatorEntries)
checkZipContent(commonZip, "publishing-example-common", "1.0.0", expectedFullEntries)
checkZipContent(subAZip, "publishing-example-subA", "1.0.0", expectedFullEntries)

static def checkZipContent(File zipFile, String artifactId, String version, Closure expectedMethod) {

  println "Checking content of $zipFile"
  List<String> expectedEntries = expectedMethod(artifactId, version)
  List<String> actualEntries
  try (ZipFile zip = new ZipFile(zipFile)) {
    actualEntries = zip.entries().collect { it.name }
  }
  expectedEntries.each {
    println "  - checking $it"
    assert actualEntries.contains(it) : "Expected entry not found in ZIP: $it"
  }
  assert expectedEntries.size() == actualEntries.size() : "Mismatch in number of entries in ZIP"

}

def getBaseDir() {
  def bd
  if (binding.hasVariable('basedir')) {
    bd = binding.getVariable('basedir')
  } else {
    bd = System.getProperty("basedir")
  }
  bd instanceof File ? bd : new File(bd)
}