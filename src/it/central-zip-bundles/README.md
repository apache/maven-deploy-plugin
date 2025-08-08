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
# central-zip-bundles

This is a multimodule example showing the creation of the zip bundle (for deployment to maven central) 
using the new central publishing rest api.

The project consist of 
- an aggregator
- a common sub module
- two sub modules that each depends on the common submodule

Each module (including the main aggregator) will be deployed separately.
I.e. when deploying the whole project, 4 zip files will be created and uploaded to central:
1. The aggregator pom (+ asc, md5 and sha1 files)
2. common, contains the pom, jar, javadoc and sources (all signed and with md5 and sha1 files)
3. subA, contains the pom, jar, javadoc and sources (all signed and with md5 and sha1 files)  
4. subB, contains the pom, jar, javadoc and sources (all signed and with md5 and sha1 files)  

Note, normally we would have the gpg plugin configured in the build, e.g:
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-gpg-plugin</artifactId>
  <version>3.2.8</version>
  <executions>
    <execution>
      <id>sign-artifacts</id>
      <phase>verify</phase>
      <goals>
        <goal>sign</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```
But this requires some external setup so we just create fake asc files in this test using a
a groovy script. We cannot do it in setup.groovy as the invoker plugin uses <cloneClean>true</cloneClean> 
config, so instead we mimic what the sign plugin would do in another groovy script (fakeSign.groovy) 
that we call from the pom (and hence it is part of the build, which is also closer to reality).

## Running only this test
```shell
mvn -Prun-its verify -Dinvoker.test=central-zip-bundles
```

## Running the test manually
```shell
CLASSPATH=$(find "$MAVEN_HOME/lib" -name "*.jar" | tr '\n' ':' | sed 's/:$//')
mvn deploy
groovy -cp $CLASSPATH -Dbasedir=$PWD verify.groovy
```


