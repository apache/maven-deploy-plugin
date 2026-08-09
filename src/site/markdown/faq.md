---
title: Frequently Asked Questions
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

<a id="top"></a>

# Frequently Asked Questions

1. [I get an Unsupported Protocol Error when deploying a 3rd party jar. What should I do?](#question)
1. [I don't want to deploy one of the artifacts in my multi-module build. Can I skip deployment?](#skip)
1. [What does the message &quot;The packaging for this project did not assign a file to the build artifact&quot; mean when I run `deploy:deploy`?](#deploy_deploy)

<a id="question"></a>

### I get an Unsupported Protocol Error when deploying a 3rd party jar. What should I do?

If you are using the `deploy:deploy-file` goal and encounter this error:

*&quot;Error deploying artifact: Unsupported Protocol: &apos;ftp&apos;: Cannot find
wagon which supports the requested protocol: ftp&quot;*

Then you need to place the appropriate wagon provider in your `%M2_HOME%/lib`. In
this case the provider needed is ftp, so we have to place the wagon-ftp jar in the
lib directory of your Maven 2 installation.

As an alternative to placing the wagon provider into the Maven distribution, you can
also create a dummy POM that declares the required wagon as an `<extension>` inside
the current directory.

If the error description is something like this:

*&quot;Error deploying artifact: Unsupported Protocol: &apos;ftp&apos;: Cannot find
wagon which supports the requested protocol: ftp
org/apache/commons/net/ftp/FTP&quot;*

Then you need to place the commons-net jar in `%M2_HOME%/lib`.

<a id="skip"></a>

### I don't want to deploy one of the artifacts in my multi-module build. Can I skip deployment?

Yes, you can skip deployment of individual modules by configuring the Deploy Plugin
as follows:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-deploy-plugin</artifactId>
  <version>X.Y</version>
  <configuration>
    <skip>true</skip>
  </configuration>
</plugin>
```

<a id="deploy_deploy"></a>

### What does the message &quot;The packaging for this project did not assign a file to the build artifact&quot; mean when I run `deploy:deploy`?

During the packaging-phase all gathered and placed in context. With this mechanism
Maven can ensure that the `maven-install-plugin` and `maven-deploy-plugin` are
copying/uploading the same set of files. So when you only execute `deploy:deploy`,
then there are no files put in the context and there is nothing to deploy.
