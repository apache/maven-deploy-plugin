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
def megaZip = new File(getBaseDir(), "target/se.alipsa.maven.example-1.0.0-bundle.zip")
assert megaZip.exists()
// We should NOT have any bundles for the sub modules
def aggregatorZip = new File(getBaseDir(), "target/publishing-example-parent-1.0.0-bundle.zip")
def commonZip = new File(getBaseDir(), "common/target/publishing-example-common-1.0.0-bundle.zip")
def subAZip = new File(getBaseDir(), "subA/target/publishing-example-subA-1.0.0-bundle.zip")
assert !aggregatorZip.exists()
assert !commonZip.exists()
assert !subAZip.exists()

// Check the content of the bundle
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
// Ensure aggregator, common and subA files exists in the mega bundle

checkZipContent(megaZip, "publishing-example-parent", "1.0.0", expectedAggregatorEntries)
checkZipContent(megaZip, "publishing-example-common", "1.0.0", expectedFullEntries)
checkZipContent(megaZip, "publishing-example-subA", "1.0.0", expectedFullEntries)
List<String> aggregatorEntries = expectedAggregatorEntries("publishing-example-parent", "1.0.0")
List<String> commonEntries = expectedFullEntries("publishing-example-common", "1.0.0")
List<String> subAEntries = expectedFullEntries("publishing-example-subA", "1.0.0")

def actualEntries
try (ZipFile zip = new ZipFile(megaZip)) {
  actualEntries = zip.entries().collect { it.name }
}
int expectedEntries = aggregatorEntries.size() + commonEntries.size() + subAEntries.size()
assert expectedEntries == actualEntries.size() : "Mismatch in number of entries in ZIP, expected $expectedEntries but was ${actualEntries.size()}"

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
  return actualEntries
}

// make it possible to run this script from different locations (command line, gmavenplus, invoker)
File getBaseDir() {
  if (binding.hasVariable('project')) {
    // from gmavenplus
    return binding.getVariable('project').basedir as File
  }
  def bd
  if (binding.hasVariable('basedir')) {
    // from the invoker plugin
    bd = binding.getVariable('basedir')
  } else {
    // from command line (e.g. with -Dbasedir=$PWD)
    bd = System.getProperty("basedir")
  }
  if (bd == null) {
    // invoked from command line and forgot to set property, assume we are in the project root
    bd = "."
  }
  bd instanceof File ? bd : new File(bd)
}