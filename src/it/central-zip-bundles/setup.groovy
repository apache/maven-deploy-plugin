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
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
// Generate jars
"mvn verify".execute().waitFor()

// Since we do not want to mess with external gpg config, we just create fake asc files
signFile(copyPom("pom.xml", "target"))
signFile(copyPom("common/pom.xml", "common/target"))
signJarFiles("common/target")
signFile(copyPom("subA/pom.xml", "subA/target"))
signJarFiles("subA/target")

// returning null is required by the invoker plugin
return null

def signJarFiles(String dir) {
  def targetDir = new File(getTestDir(), dir)
  targetDir.listFiles({ file -> file.name.endsWith(".jar") } as FileFilter).each {
    signFile(it)
  }
}

static def signFile(File file) {
  def ascFile = new File(file.getParentFile(), "${file.getName()}.asc")
  ascFile << "fake signed"
}

def copyPom(String from, String toDir) {
  File testDir = getTestDir()
  def src = new File(testDir, from)
  def pomInfo = readPomInfo(src)
  def dstDir = new File(testDir, toDir)
  dstDir.mkdirs()
  def toFile = new File(dstDir, "$pomInfo.artifactId-${pomInfo.version}.pom")
  toFile.text = src.text
  return toFile
}

static def readPomInfo(File pomFile) {
  MavenXpp3Reader reader = new MavenXpp3Reader()
  pomFile.withReader { r ->
    Model model = reader.read(r)

    // Inherit groupId/version from parent if missing
    if (!model.groupId && model.parent) {
      model.groupId = model.parent.groupId
    }
    if (!model.version && model.parent) {
      model.version = model.parent.version
    }

    return [
        groupId    : model.groupId,
        artifactId : model.artifactId,
        version    : model.version
    ]
  }
}

def getTestDir() {
  new File("${basedir}")
}

