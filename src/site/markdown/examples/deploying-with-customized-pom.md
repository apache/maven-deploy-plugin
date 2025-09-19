  ----
  Deploy an artifact with a customized pom
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

Deploy an artifact with a customized pom

  If there is already an existing pom and want it to be deployed together with the 3rd party artifact,
  set the <<pomFile>> parameter to the path of the pom.xml.

+---+
mvn ${project.groupId}:${project.artifactId}:${project.version}:deploy-file -Durl=file:///C:/m2-repo \
                                                                            -DrepositoryId=some.id \
                                                                            -Dfile=path-to-your-artifact-jar \
                                                                            -DpomFile=path-to-your-pom.xml
+---+

   Note that the groupId, artifactId, version and packaging informations are automatically retrieved from the
   given pom.

  <<Note>>: By using the fully qualified path of a goal, you're ensured to be using the preferred version of the maven-deploy-plugin. When using <<<mvn deploy:deploy-file>>> 
  its version depends on its specification in the pom or the version of Apache Maven.
   
