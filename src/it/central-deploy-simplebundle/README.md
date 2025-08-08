<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# central-deploy-simplebundle

This is a basic example showing deployment to maven central 
using the new central publishing rest api.


The project consist of 
- a main pom that produces a jar, javadoc and sources

When deploying the whole project, 1 zip file will be created and uploaded to central.

## Running only this test
```shell
mvn -Prun-its verify -Dinvoker.test=central-deploy-simplebundle
```

## Running the test manually
```shell
# copy resources
mvn -Prun-its verify -Dinvoker.test=central-deploy-simplebundle
cd target/it/central-deploy-bundles
CLASSPATH=$(find "$MAVEN_HOME/lib" -name "*.jar" | tr '\n' ':' | sed 's/:$//')
CLASSPATH=$CLASSPATH:$(./addDependencies.groovy)
groovy -cp $CLASSPATH -Dbasedir=$PWD setup.groovy &
mvn --settings ../../../src/it/settings.xml deploy
groovy -cp $CLASSPATH -Dbasedir=$PWD verify.groovy
```