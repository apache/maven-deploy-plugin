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
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import javax.servlet.http.*
import javax.servlet.*
import java.util.concurrent.ConcurrentHashMap
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.IOUtils
import com.fasterxml.jackson.databind.ObjectMapper

def port = 8088
def server = new Server(port)
def context = new ServletContextHandler(ServletContextHandler.SESSIONS)
context.setContextPath("/")

def bundles = new ConcurrentHashMap<String, Bundle>()
def mapper = new ObjectMapper()
class Bundle {
  byte[] content
  String name
  String publishingType
  String fileName
}

// /api/v1/publisher/upload - Upload endpoint
context.addServlet(new ServletHolder(new HttpServlet() {
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    try {
      if (!authHeader().equals(req.getHeader("Authorization"))) {
        resp.setStatus(401)
        println "Bearer token not recognized, should be based on testUser:testPwd"
        return
      }

      if (!ServletFileUpload.isMultipartContent(req)) {
        resp.setStatus(400)
        return
      }

      def multipartConfig = new MultipartConfigElement(System.getProperty("java.io.tmpdir"))
      req.setAttribute("org.eclipse.jetty.multipartConfig", multipartConfig)

      def publishingType = req.getParameter("publishingType")
      def name = req.getParameter("name")
      def part = req.getPart("bundle")
      def zipData = IOUtils.toByteArray(part.inputStream)
      if (zipData == null || zipData.length == 0) {
        resp.setStatus(400)
        resp.setContentType("application/json")
        resp.writer.write("bundle contains no data")
        return
      }
      def deploymentId = UUID.randomUUID().toString()

      Bundle bundle = new Bundle()
      bundle.content = zipData
      bundle.name = name
      bundle.publishingType = publishingType
      bundle.fileName = part.getSubmittedFileName()

      bundles.put(deploymentId, bundle)
      println("Central Simulator received bundle $name, publishingType: $publishingType")

      resp.setContentType("text/plain")
      resp.writer.write(deploymentId)

    } catch (Exception e) {
      // Log stack trace to console
      e.printStackTrace()

      // Respond with HTTP 500 and JSON error details
      resp.setStatus(500)
      resp.setContentType("application/json")
      def errorResponse = [
          error: e.getClass().name,
          message: e.message,
          stackTrace: e.stackTrace.collect { it.toString() }
      ]
      resp.writer.write(mapper.writeValueAsString(errorResponse))
    }
  }
}), "/api/v1/publisher/upload")

// /api/v1/publisher/status - Status check
context.addServlet(new ServletHolder(new HttpServlet() {
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    try {
      if (!authHeader().equals(req.getHeader("Authorization"))) {
        resp.setStatus(401)
        println "Bearer token not recognized, should be based on testUser:testPwd"
        return
      }

      def deploymentId = req.getParameter("id")
      def bundleInfo = bundles.get(deploymentId)

      if (bundleInfo == null) {
        resp.writer.write("Deployment $deploymentId not found")
        resp.setStatus(404)
        return
      }
      String deployState = bundleInfo.publishingType == "AUTOMATIC" ? "PUBLISHING" : "VALIDATED"

      resp.setContentType("application/json")
      Map<String, Object> deployments = new HashMap<>()
      deployments.put(deploymentId, [
          deploymentId: deploymentId,
          deploymentState: deployState,
          purls: ["pkg:maven/se.alipsa.maven.example/example_java_project@0.0.7"]
      ])
      resp.writer.write(mapper.writeValueAsString(deployments))
    } catch (Exception e) {
      // Log stack trace to console
      e.printStackTrace()

      // Respond with HTTP 500 and JSON error details
      resp.setStatus(500)
      resp.setContentType("application/json")
      def errorResponse = [
          error: e.getClass().name,
          message: e.message,
          stackTrace: e.stackTrace.collect { it.toString() }
      ]
      resp.writer.write(mapper.writeValueAsString(errorResponse))
    }
  }
}), "/api/v1/publisher/status")

// /getBundleIds - get a json list of all uploaded bundles (test only api, not part of central api)
context.addServlet(new ServletHolder(new HttpServlet() {
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.setContentType("application/json")
    List<Map<String, String>> bundleInfo = new ArrayList()
    for (Map.Entry<String, Bundle> entry : bundles.entrySet()) {
      bundleInfo << [
          deploymentId: entry.key,
          name: entry.value.name,
          publishingType: entry.value.publishingType,
          fileName: entry.value.fileName
      ]
    }
    resp.writer.write(mapper.writeValueAsString(bundleInfo))
  }
}), "/getBundleInfo")

// /getBundle - Retrieve the uploaded bundle by deploymentId (test only api, not part of central api)
context.addServlet(new ServletHolder(new HttpServlet() {
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    def id = req.getParameter("deploymentId")
    def zip = bundles.get(id)
    if (zip == null) {
      resp.setStatus(404)
      return
    }

    resp.setContentType("application/zip")
    resp.setHeader("Content-Disposition", "attachment; filename=\"${id}.zip\"")
    resp.outputStream.write(zip.content)
  }
}), "/getBundle")

// /shutdown - Shut down the server gracefully (test only api, not part of central api)
context.addServlet(new ServletHolder(new HttpServlet() {
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.writer.write("Shutting down...")
    new Thread({
      Thread.sleep(1000)
      server.stop()
    }).start()
  }
}), "/shutdown")

// catch all for everything else (test only api, not part of central api)
context.addServlet(new ServletHolder(new HttpServlet() {
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    // Log or print the unmatched request URI
    println("Unhandled request to: " + req.getRequestURI())

    resp.setStatus(404)
    resp.setContentType("application/json")
    resp.writer.write(mapper.writeValueAsString([
        error: "Unknown endpoint",
        path: req.getRequestURI()
    ]))
  }
}), "/*")

// Must match the user and pwd in settings.xml for the central server
private String authHeader() {
  return "Bearer " + Base64.getEncoder().encodeToString(("testUser:testPwd").getBytes());
}

server.setHandler(context)
server.start()
