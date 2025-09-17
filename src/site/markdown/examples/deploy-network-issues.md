---
title: Deploying With Network Issues
author: 
  - Hervé Boutemy
date: 2019-01-20
---

<!-- Licensed to the Apache Software Foundation (ASF) under one-->
<!-- or more contributor license agreements.  See the NOTICE file-->
<!-- distributed with this work for additional information-->
<!-- regarding copyright ownership.  The ASF licenses this file-->
<!-- to you under the Apache License, Version 2.0 (the-->
<!-- "License"); you may not use this file except in compliance-->
<!-- with the License.  You may obtain a copy of the License at-->
<!---->
<!--   http://www.apache.org/licenses/LICENSE-2.0-->
<!---->
<!-- Unless required by applicable law or agreed to in writing,-->
<!-- software distributed under the License is distributed on an-->
<!-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY-->
<!-- KIND, either express or implied.  See the License for the-->
<!-- specific language governing permissions and limitations-->
<!-- under the License.-->

# Deploying With Network Issues

Sometimes, network quality from building machine to the remote repository is not perfect\. Of course, improving the network would be the best solution, but it is not always possible\.

There are a few strategies to work around the network issue\.

## Configuring Multiple Tries

Deploy plugin provides [`retryFailedDeploymentCount` parameter](../deploy-mojo.html#retryFailedDeploymentCount) to retry deployment multiple times before giving up and returning a failure for the `deploy` goal: 

```unknown
<project>
  [...]
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-deploy-plugin</artifactId>
        <configuration>
          <retryFailedDeploymentCount>3</retryFailedDeploymentCount>
        </configuration>
      </plugin>
    </plugins>
  </build>
  [...]
</project>
```

## Deploying to a Local Staging Directory

When the network is really not consistent, a deeper strategy is to deploy in 2 steps:

1\. `deploy` to a local directory during the build, for example `file:./target/staging-deploy`,

2\. then copy from the local area to the target remote repository, retrying as much as necessary\.

### Deploying to a Local Directory

Deploying to a local directory can be done from command line, without changing POM, using [`altDeploymentRepository` parameter](../deploy-mojo.html#altDeploymentRepository):

```unknown
mvn deploy -DaltDeploymentRepository=local::file:./target/staging-deploy
```

or for older 2\.x version of maven\-deploy\-plugin

```unknown
mvn deploy -DaltDeploymentRepository=local::default::file:./target/staging-deploy
```

Of course, you can configure the repository in your `pom.xml` if you want to go from a temporary strategy to the general strategy\.

### Copying from Local Directory to Target Remote Repository

`wagon-maven-plugin`&apos;s [`merge-maven-repos` goal](https://www.mojohaus.org/wagon-maven-plugin/merge-maven-repos-mojo.html) provides a mechanism to copy from one remote repository to the other, while merging repository metadata\.

`wagon-maven-plugin`&apos;s [`upload` goal](https://www.mojohaus.org/wagon-maven-plugin/upload-mojo.html) will do the same without taking care of repository metadata: use it if you have an empty repository as target, like a staging repository provided by a repository manager\.

It can be invoked fully from command line \(renaming `-Dwagon.` with `wagon.targetId` when [Wagon Maven Plugin 2\.0\.1 will be released](https://github.com/mojohaus/wagon-maven-plugin/pull/26)\):

```unknown
mvn org.codehaus.mojo:wagon-maven-plugin:2.0.0:merge-maven-repos \
  -Dwagon.source=file:./target/staging-deploy \
  -Dwagon.target=https://... \
  -Dwagon.=id
# or once wagon-maven-plugin 2.0.1 is released:
  -Dwagon.targetId=id
```

or more simply with `mvn wagon:merge-maven-repos` with configuration in `pom.xml`:

```unknown
<project>
  [...]
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>wagon-maven-plugin</artifactId>
        <version>2.0.0</version>
        <configuration>
          <source>file:./target/staging-deploy</source>
          <target>${project.distributionManagement.repository.url}</target>
          <targetId>${project.distributionManagement.repository.id}</targetId>
        </configuration>
      </plugin>
    </plugins>
  </build>
  [...]
</project>
```

