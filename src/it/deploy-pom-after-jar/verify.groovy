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

def versionDir = new File(basedir, 'target/repo/org/apache/maven/its/deploy/pomafterjar/test/1.0')

// Maven 4 deploys the consumer POM as test-1.0.pom and the build POM as test-1.0-build.pom; neither may be the JAR
def poms = versionDir.listFiles().findAll { it.name.endsWith('.pom') }
assert poms*.name.contains('test-1.0.pom')
poms.each { pom ->
    // a JAR deployed under a .pom name starts with the ZIP signature "PK"
    assert !(pom.bytes[0] == 0x50 && pom.bytes[1] == 0x4B) : "${pom.name} is a ZIP, not a POM"
    assert pom.text.contains('<artifactId>test</artifactId>') : "${pom.name} is not this project's POM"
}
assert !new File(versionDir, 'test-1.0.jar').exists()
