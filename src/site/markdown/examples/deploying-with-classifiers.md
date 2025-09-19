  ----
  Deploy an artifact with classifier
  ------
  Robert Scholte 
  ------
  2013-09-01
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

Deploy an artifact with classifier

  Beside the main artifact there can be additional files which are attached to the Maven project. Such attached files can be recognized and accessed by their classifier.

  For example: from the following artifact names, the classifier is located between the version and extension name of the artifact.

  * <<<artifact-name-1.0.jar>>> the main jar which contains classes compiled without {{{https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#debug}debugging}} information (such as linenumbers)

  * <<<artifact-name-1.0-debug.jar>>> the classified jar which contains classes compiled with {{{https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#debug}debugging}} information, so will be larger

  * <<<artifact-name-1.0-site.pdf>>> a pdf which contains an export of the site documentation. 

  []

  You can deploy the main artifact and the classified artifacts in a single run. Let's assume the original filename for the documentation is <<<site.pdf>>>: 

+---+
mvn ${project.groupId}:${project.artifactId}:${project.version}:deploy-file -Durl=http://localhost:8081/repomanager/ \
                                                                            -DrepositoryId=some.id \
                                                                            -Dfile=path/to/artifact-name-1.0.jar \
                                                                            -DpomFile=path-to-your-pom.xml \
                                                                            -Dfiles=path/to/artifact-name-1.0-debug.jar,path/to/site.pdf \
                                                                            -Dclassifiers=debug,site \
                                                                            -Dtypes=jar,pdf
+---+

  If you only want to deploy the <<<debug>>>-jar and want to keep the classifier, you can execute the <<<deploy-file>>> like

+---+
mvn ${project.groupId}:${project.artifactId}:${project.version}:deploy-file -Durl=http://localhost:8081/repomanager/ \
                                                                            -DrepositoryId=some.id \
                                                                            -Dfile=path-to-your-artifact-jar \
                                                                            -DpomFile=path-to-your-pom.xml \
                                                                            -Dclassifier=bin
+---+

  <<Note>>: By using the fully qualified path of a goal, you're ensured to be using the preferred version of the maven-deploy-plugin. When using <<<mvn deploy:deploy-file>>> 
  its version depends on its specification in the pom or the version of Apache Maven.
