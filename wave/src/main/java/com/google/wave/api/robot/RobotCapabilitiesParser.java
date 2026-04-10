/**
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

package com.google.wave.api.robot;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wave.api.Context;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * RobotCapabilityParser is responsible for parsing Robot's capabilities.xml
 * file.
 *
 */
public class RobotCapabilitiesParser {

  private static final Namespace XML_NS =
      Namespace.getNamespace("w", "http://wave.google.com/extensions/robots/1.0");

  private static final String CAPABILITIES_TAG = "capabilities";
  private static final String CAPABILITY_TAG = "capability";
  private static final String CAPABILITY_CONTEXT_ATTRIBUTE = "context";
  private static final String CAPABILITY_FILTER_ATTRIBUTE = "filter";
  private static final String CAPABILITY_NAME_ATTRIBUTE = "name";

  private static final String ROBOT_VERSION_TAG = "version";
  private static final String PROTOCOL_VERSION_TAG = "protocolversion";
  private static final String CONSUMER_KEYS_TAG = "consumer_keys";
  private static final String CONSUMER_KEY_TAG = "consumer_key";
  private static final String CONSUMER_KEY_FOR_ATTRIBUTE = "for";
  private static final String ACTIVE_RPC_PATH = "/robot/rpc";
  private static final String DATA_API_RPC_PATH = "/robot/dataapi/rpc";

  private final String capabilitiesXmlUrl;

  private final Map<EventType, Capability> capabilities;

  private final RobotConnection connection;

  private String capabilitiesHash;

  private ProtocolVersion protocolVersion;

  private String consumerKey;  // null if no consumer key
  private String rpcServerUrl = "";

  private final String activeRobotApiUrl;

  public RobotCapabilitiesParser(String capabilitiesXmlUrl, RobotConnection connection,
      String activeRobotApiUrl)
      throws CapabilityFetchException {
    this.capabilitiesXmlUrl = capabilitiesXmlUrl;
    this.activeRobotApiUrl = activeRobotApiUrl;
    this.capabilities = Maps.newHashMap();
    this.connection = connection;
    parseRobotDescriptionXmlFile();
  }

  public Map<EventType, Capability> getCapabilities() {
    return capabilities;
  }

  public String getCapabilitiesHash() {
    return capabilitiesHash;
  }

  public ProtocolVersion getProtocolVersion() {
    return protocolVersion;
  }

  public String getConsumerKey() {
    return consumerKey;
  }

  public String getRpcServerUrl() {
    return rpcServerUrl;
  }

  private void parseRobotDescriptionXmlFile() throws CapabilityFetchException {
    // Fetch the XML file that defines the Robot capabilities.
    try {
      String xmlContent = connection.get(capabilitiesXmlUrl);
      if (xmlContent == null || xmlContent.isEmpty()) {
        throw new CapabilityFetchException("Empty capabilities.xml");
      }
      StringReader reader = new StringReader(xmlContent);
      Document document = new SAXBuilder().build(reader);

      // Parse all "<w:capability>" tags.
      List<Element> capabilities = getElements(document, CAPABILITIES_TAG, CAPABILITY_TAG, XML_NS);
      for (Element capability : capabilities) {
        parseCapabilityTag(capability);
      }

      // Always react to SELF_ADDED:
      if (!this.capabilities.containsKey(EventType.WAVELET_SELF_ADDED)) {
        this.capabilities.put(EventType.WAVELET_SELF_ADDED,
            new Capability(EventType.WAVELET_SELF_ADDED, Capability.DEFAULT_CONTEXT));
      }

      // Parse "<w:version>" tag.
      Element capabilitiesHashElement =
          document.getRootElement().getChild(ROBOT_VERSION_TAG, XML_NS);
      if (capabilitiesHashElement != null) {
        capabilitiesHash = capabilitiesHashElement.getText();
      }

      // Parse "<w:protocolversion>" tag.
      Element protocolVersionElement =
          document.getRootElement().getChild(PROTOCOL_VERSION_TAG, XML_NS);
      if (protocolVersionElement != null) {
        protocolVersion = ProtocolVersion.fromVersionString(protocolVersionElement.getText());
      } else {
        // In V1 API, we don't have <w:protocolversion> tag in the
        // capabilities.xml file.
        protocolVersion = ProtocolVersion.V1;
      }

      // Parse "<w:consumer_key>" tag(s).
      for (Element consumerKeyElement : getElements(document, CONSUMER_KEYS_TAG, CONSUMER_KEY_TAG,
          XML_NS)) {
        String forUrl = consumerKeyElement.getAttributeValue(CONSUMER_KEY_FOR_ATTRIBUTE);
        if (forUrl != null && forUrl.equals(activeRobotApiUrl)) {
          consumerKey = consumerKeyElement.getText();
        }
        if (forUrl == null || forUrl.isEmpty()) {
          continue;
        }
        // Prefer the 2-legged active endpoint when both are advertised.
        // Only accept endpoints on the same host as this server.
        if (isSameHost(forUrl, activeRobotApiUrl)) {
          if (forUrl.endsWith(ACTIVE_RPC_PATH)) {
            rpcServerUrl = forUrl;
          } else if (rpcServerUrl.isEmpty() && forUrl.endsWith(DATA_API_RPC_PATH)) {
            rpcServerUrl = forUrl;
          }
        }
      }
    } catch (IOException iox) {
      throw new CapabilityFetchException("Failure reading capabilities for: " + capabilitiesXmlUrl,
          iox);
    } catch (JDOMException jdomx) {
      throw new CapabilityFetchException("Failure parsing capabilities for: " + capabilitiesXmlUrl,
          jdomx);
    } catch (RobotConnectionException e) {
      throw new CapabilityFetchException(e);
    }
  }

  private void parseCapabilityTag(Element capability) {
    // Get the event type.
    EventType eventType = EventType.valueOfIgnoreCase(
        capability.getAttributeValue(CAPABILITY_NAME_ATTRIBUTE));
    if (eventType == EventType.UNKNOWN) {
      return;
    }

    // Parse comma separated "context" attribute.
    List<Context> contexts;
    String contextsString = capability.getAttributeValue(CAPABILITY_CONTEXT_ATTRIBUTE);
    if (contextsString != null && !contextsString.isEmpty()) {
      try {
        contexts = Lists.newArrayList();
        for (String context : contextsString.split(",")) {
          contexts.add(Context.valueOfIgnoreCase(context));
        }
      } catch (IllegalArgumentException e) {
        contexts = Capability.DEFAULT_CONTEXT;
      }
    } else {
      contexts = Capability.DEFAULT_CONTEXT;
    }
    // Parse optional "filter" attribute.
    String filter = capability.getAttributeValue(CAPABILITY_FILTER_ATTRIBUTE);
    if (filter == null || filter.isEmpty()) {
      filter = "";
    }

    this.capabilities.put(eventType, new Capability(eventType, contexts, filter));
  }

  private static boolean isSameHost(String url, String referenceUrl) {
    try {
      URI u = new URI(url);
      URI ref = new URI(referenceUrl);
      return u.getScheme() != null && u.getScheme().equals(ref.getScheme())
          && u.getHost() != null && u.getHost().equals(ref.getHost())
          && effectivePort(u) == effectivePort(ref);
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static int effectivePort(URI uri) {
    int port = uri.getPort();
    if (port != -1) return port;
    String scheme = uri.getScheme();
    if ("https".equalsIgnoreCase(scheme)) return 443;
    if ("http".equalsIgnoreCase(scheme)) return 80;
    return -1;
  }

  @SuppressWarnings({"cast", "unchecked"})
  private List<Element> getElements(Document doc, String parentTag, String tag, Namespace ns) {
    Element parent = doc.getRootElement().getChild(parentTag, ns);
    return (List<Element>) (parent == null ? Lists.newArrayList() : parent.getChildren(tag, ns));
  }
}
