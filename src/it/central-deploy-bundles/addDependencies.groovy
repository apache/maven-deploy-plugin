#!/usr/bin/env groovy
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
// This script creates a classpath string based on the dependencies for the invoker plugin
// It is used when you want to change the groovy scripts and have more control from the command line
// i.e allows you to run each step manually. See readme.md for details.
import groovy.xml.XmlParser
import groovy.xml.XmlNodePrinter
import groovy.xml.StreamingMarkupBuilder

// Paths
def realPom = new File("../../../pom.xml")
def tmpDir = File.createTempFile("tmp", "")
tmpDir.delete()
tmpDir.mkdirs()
def tmpPom = new File(tmpDir, "pom.xml")
def depsDir = new File(tmpDir, "deps")
depsDir.mkdirs()

// Parse original POM without namespaces
def parser = new XmlParser(false, false) // disable namespaces
def pom = parser.parse(realPom)

// Locate dependencies inside maven-invoker-plugin / run-its profile
def runItsProfile = pom.profiles.profile.find { it.id.text() == "run-its" }
def rawDeps = []
if (runItsProfile) {
    rawDeps = runItsProfile.depthFirst().findAll {
        it.name() == "plugin" && it.artifactId.text() == "maven-invoker-plugin"
    }*.dependencies*.dependency.flatten()
}

// Build minimal POM
def newPom = {
    mkp.xmlDeclaration()
    project(xmlns: "http://maven.apache.org/POM/4.0.0",
            'xmlns:xsi': "http://www.w3.org/2001/XMLSchema-instance",
            'xsi:schemaLocation': "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd") {
        modelVersion("4.0.0")
        groupId("temp")
        artifactId("invoker-plugin-deps")
        version("1.0-SNAPSHOT")
        dependencies {
            rawDeps.each { dep ->
                dependency {
                    groupId(dep.groupId.text())
                    artifactId(dep.artifactId.text())
                    version(dep.version.text())
                    if (dep.scope) scope(dep.scope.text())
                }
            }
        }
    }
}

// Write temp POM
tmpPom.text = new StreamingMarkupBuilder().bind(newPom).toString()

//println "Downloading dependencies..."
def proc = ["mvn", "-q", "-f", tmpPom.absolutePath,
            "dependency:copy-dependencies",
            "-DoutputDirectory=${depsDir.absolutePath}",
            "-DincludeScope=runtime"].execute()
proc.in.eachLine { println it }
proc.err.eachLine { System.err.println it }
proc.waitFor()

// Build classpath
def jars = depsDir.listFiles().findAll { it.name.endsWith(".jar") }
def classpath = jars.collect { it.absolutePath }.join(":")
println classpath