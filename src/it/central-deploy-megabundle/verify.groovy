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
import groovy.json.JsonSlurper
String baseUrl = "http://localhost:8088"

def conn = new URI("$baseUrl/getBundleIds").toURL().openConnection()
conn.setRequestMethod("GET")
conn.connect()
def json = conn.inputStream.text
def ids = new JsonSlurper().parseText(json)
println "verify: Received bundle IDs: $ids"

// Check that exactly one bundle was uploaded
assert ids.size() == 1 : "Expected exactly one bundle, but got ${ids.size()}"
// TODO: maybe download and check the zip content

try {
  conn = new URI("$baseUrl/shutdown").toURL().openConnection()
  conn.setRequestMethod("POST")
  conn.connect()
  println conn.inputStream.text
} catch (Exception e) {
  println "Shutdown failed: ${e.message}"
}


/*
try {
  def url = new URI("$baseUrl/shutdown").toURL()
  def conn = url.openConnection()
  conn.setRequestMethod("POST")
  conn.doOutput = true
  conn.connect()
  println "Shutdown response: ${conn.inputStream.text}"
} catch (Exception e) {
  println "Shutdown failed or server already stopped: ${e.message}"
}*/