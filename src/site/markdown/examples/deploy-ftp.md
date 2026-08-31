---
title: Deployment of artifacts with FTP
author: 
  - Jason van Zyl
date: 2005-10-12
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Deployment of artifacts with FTP

**FTP is a cleartext protocol: credentials and artifacts cross the network unencrypted, and
anyone on the path can capture the deployment credential. Prefer deploying over HTTPS to a
repository manager.** The plugin refuses `ftp://` (and `http://`) deployment URLs to
non-loopback hosts by default; deploying over FTP requires an explicit
`-Dmaven.deploy.allowInsecureUrl=true` opt-out. Note also that the default Maven Resolver
transport does not support FTP at all: you must switch to the wagon transport
(`-Dmaven.resolver.transport=wagon`) in addition to declaring the extension below.

In order to deploy artifacts using FTP you must first specify the use of an FTP server in the **distributionManagement** element of your POM as well as specifying an `extension` in your `build` element which will pull in the FTP artifacts required to deploy with FTP:

```unknown
<project>
  ...
  <distributionManagement>
    <repository>
      <id>ftp-repository</id>
      <url>ftp://repository.mycompany.com/repository</url>
    </repository>
  </distributionManagement>

  <build>
    <extensions>
      <!-- Enabling the use of FTP -->
      <extension>
        <groupId>org.apache.maven.wagon</groupId>
         <artifactId>wagon-ftp</artifactId>
         <version>1.0-beta-6</version>
      </extension>
    </extensions>
  </build>
  ...
</project>
```

Your `settings.xml` would contain a `server` element where the `id` of that element matches `id` of the FTP repository specified in the POM above. Store the password in [encrypted form](https://maven.apache.org/guides/mini/guide-encryption.html) rather than as clear text:

```unknown
<settings>
  ...
  <servers>
    <server>
      <id>ftp-repository</id>
      <username>my-user</username>
      <password>{encrypted-password}</password>
    </server>
  </servers>
  ...
</settings>
```

You should, of course, make sure that you can login into the specified FTP server by hand before attempting the deployment with Maven. Once you have verified that everything is setup correctly you can now deploy your artifacts using Maven:

```unknown
mvn deploy
```

