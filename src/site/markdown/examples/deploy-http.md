---
title: Deployment of artifacts with HTTP(S)
author: 
  - Konrad Windszus
date: 2025-09-17
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

# Deployment of artifacts with HTTP(S)

In order to deploy artifacts using HTTP(S) you must specify the use of an HTTP(S) server in the [**distributionManagement** element of your POM](https://maven.apache.org/pom.html#Distribution_Management):

```unknown
<project>
  ...
  <distributionManagement>
    <repository>
      <id>my-mrm-relases</id>
      <url>http://localhost:8081/nexus/content/repositories/release</url>
    </repository>
  </distributionManagement>
  ...
</project>
```

## Authentication

Your `settings.xml` would contain a `server` element where the `id` of that element matches `id` of the HTTP(S) repository specified in the POM above. It must contain the credentials to be used (in [encrypted form](https://maven.apache.org/guides/mini/guide-encryption.html)):

```unknown
<settings>
  ...
  <servers>
    <server>
      <id>my-mrm-releases</id>
      <username><my-encrypted-user></username>
      <password><my-encrypted-password></password>
    </server>
  </servers>
  ...
</settings>
```

Further details in [Security and Deployment Settings](https://maven.apache.org/guides/mini/guide-deployment-security-settings.html).

## HTTP method

Deployment leverages one *HTTP PUT* request per artifact/checksum/metadata (optionally using HTTP Basic Authentication) with no query parameters. Make sure your used [Maven repository manager](https://maven.apache.org/repository-management.html) supports this method of deployment, otherwise this plugin cannot be used.

*[Sonatype Central Portal](https://maven.apache.org/repository/guide-central-repository-upload.html) only supports this via the legacy [Portal OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/) for release versions. [SNAPSHOT version](https://central.sonatype.org/publish/publish-portal-snapshots/) deployments for Central Portal are still supported with `maven-deploy-plugin`*.

## HTTP client settings

The HTTP client being used by default depends on the underlying Maven version as the [Maven Resolver Transport](https://maven.apache.org/guides/mini/guide-resolver-transport.html) is leveraged for the actual deployment. One can override that default client with Maven user property `maven.resolver.transport`. Maven Resolver also evaluates other advanced [configuration properties](https://maven.apache.org/resolver-archives/resolver-LATEST-1.x/configuration.html) which can be set as Maven user properties. However, in most of the cases the defaults should work fine.
