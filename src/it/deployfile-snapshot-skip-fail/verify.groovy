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

File deployedDir = new File(basedir, "target/repo/org/apache/maven/plugins/deploy/its/deployfile-snapshot-skip-fail/1.0-SNAPSHOT")
assert deployedDir.exists()
assert deployedDir.isDirectory()
assert deployedDir.listFiles({ File file, String name -> name.matches("deployfile-snapshot-skip-fail-1\\.0-.*\\.jar") } as FilenameFilter).length == 1
assert deployedDir.listFiles({ File file, String name -> name.matches("deployfile-snapshot-skip-fail-1\\.0-.*\\.pom") } as FilenameFilter).length == 1

File buildLog = new File(basedir, 'build.log')
assert buildLog.exists()
assert buildLog.text.contains("[DEBUG] Using META-INF/maven/org.apache.maven.plugins.deploy.its/deployfile-snapshot-skip-fail/pom.xml as pomFile")
