  ----
  Disable the generation of pom
  ------
  Allan Ramirez
  ------
  2006-06-21
  ------

~~ Licensed to the Apache Software Foundation (ASF) under one
~~ or more contributor license agreements.  See the NOTICE file
~~ distributed with this work for additional information
~~ regarding copyright ownership.  The ASF licenses this file
~~ to you under the Apache License, Version 2.0 (the
~~ "License"); you may not use this file except in compliance
~~ with the License.  You may obtain a copy of the License at
~~
~~   http://www.apache.org/licenses/LICENSE-2.0
~~
~~ Unless required by applicable law or agreed to in writing,
~~ software distributed under the License is distributed on an
~~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
~~ KIND, either express or implied.  See the License for the
~~ specific language governing permissions and limitations
~~ under the License.

~~ NOTE: For help with the syntax of this file, see:
~~ http://maven.apache.org/doxia/references/apt-format.html

Disable the generation of pom

  By default, If no pom is specified during deployment of your 3rd party artifact, a generic pom will be generated
  which contains the minimum required elements needed for the pom. In order to disable it, set the
  <<generatePom>> parameter to <<<false>>>.

+---+
mvn ${project.groupId}:${project.artifactId}:${project.version}:deploy-file -Durl=file:///C:/m2-repo \
                                                                            -DrepositoryId=some.id \
                                                                            -Dfile=path-to-your-artifact-jar \
                                                                            -DgroupId=your.groupId \
                                                                            -DartifactId=your-artifactId \
                                                                            -Dversion=version \
                                                                            -Dpackaging=jar \
                                                                            -DgeneratePom=false
+---+

  <<Note>>: By using the fully qualified path of a goal, you're ensured to be using the preferred version of the maven-deploy-plugin. When using <<<mvn deploy:deploy-file>>> 
  its version depends on its specification in the pom or the version of Apache Maven.
