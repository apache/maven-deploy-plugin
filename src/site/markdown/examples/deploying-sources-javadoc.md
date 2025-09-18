  ----
  Deploy sources and javadocs for an artifact
  ------
  ------
  20011-02-23
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

Deploy sources and javadoc jars

  A project may include a main jar and associated sources and javadoc jars.

+---+
  artifact-name-1.0.jar
  artifact-name-1.0-sources.jar
  artifact-name-1.0-javadoc.jar
+---+

  The sources jar contains the Java sources, and the javadoc jar contains the generated javadocs.
  To include these files in your deployment, set the <<sources>> and <<javadoc>> parameters to 
  the paths to the sources and javadoc jar files.

+---+
mvn ${project.groupId}:${project.artifactId}:${project.version}:deploy-file -Durl=file:///home/me/m2-repo \
                                                                            -DrepositoryId=some.repo.id \
                                                                            -Dfile=./path/to/artifact-name-1.0.jar \
                                                                            -DpomFile=./path/to/pom.xml \
                                                                            -Dsources=./path/to/artifact-name-1.0-sources.jar \
                                                                            -Djavadoc=./path/to/artifact-name-1.0-javadoc.jar
+---+

  <<Note>>: By using the fully qualified path of a goal, you're ensured to be using the preferred version of the maven-deploy-plugin. When using <<<mvn deploy:deploy-file>>> 
  its version depends on its specification in the pom or the version of Apache Maven.
