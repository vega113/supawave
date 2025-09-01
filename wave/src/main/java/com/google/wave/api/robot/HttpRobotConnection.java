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

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * A {@link RobotConnection} that uses Apache's {@code HTTP Client} for
 * communicating with the robot.
 *
 */
public class HttpRobotConnection implements RobotConnection {

  /** A user agent client that can execute HTTP methods. */
  private final CloseableHttpClient httpClient;

  /** An executor to submit tasks asynchronously. */
  private final ExecutorService executor;

  /**
   * Constructor.
   *
   * @param client the client for executing HTTP methods.
   * @param executor the executor for submitting tasks asynchronously.
   */
  public HttpRobotConnection(CloseableHttpClient client, ExecutorService executor) {
    this.httpClient = client;
    this.executor = executor;
  }

  @Override
  public String get(String url) throws RobotConnectionException {
    HttpGet req = new HttpGet(url);
    return fetch(url, req);
  }

  @Override
  public ListenableFuture<String> asyncGet(final String url) {
    return JdkFutureAdapters.listenInPoolThread(executor.submit(new Callable<String>() {
      @Override
      public String call() throws RobotConnectionException {
        return get(url);
      }
    }));
  }

  @Override
  public String postJson(String url, String body) throws RobotConnectionException {
    HttpPost req = new HttpPost(url);
    try {
      req.setEntity(new StringEntity(body, Charsets.UTF_8));
      req.setHeader("Content-Type", RobotConnection.JSON_CONTENT_TYPE);
      return fetch(url, req);
    } catch (Exception e) {
      String msg = "Robot fetch http failure: " + url + ": " + e;
      throw new RobotConnectionException(msg, e);
    }
  }

  @Override
  public ListenableFuture<String> asyncPostJson(final String url, final String body) {
    return JdkFutureAdapters.listenInPoolThread(executor.submit(new Callable<String>() {
      @Override
      public String call() throws RobotConnectionException {
        return postJson(url, body);
      }
    }));
  }

  /**
   * Fetches the given URL, given a method ({@code GET} or {@code POST}).
   *
   * @param url the URL to be fetched.
   * @param method the method to fetch the URL, can be {@code GET} or
   *     {@code POST}.
   * @return the content of the URL.
   *
   * @throws RobotConnectionException if there is a problem fetching the URL,
   *     for example, if the response code is not HTTP OK (200).
   */
  private String fetch(String url, org.apache.http.client.methods.HttpUriRequest req)
      throws RobotConnectionException {
    try (CloseableHttpResponse resp = httpClient.execute(req)) {
      int status = resp.getStatusLine().getStatusCode();
      HttpEntity entity = resp.getEntity();
      String result = entity != null
          ? RobotConnectionUtil.validateAndReadResponse(url, status, entity.getContent())
          : RobotConnectionUtil.validateAndReadResponse(url, status, new byte[0]);
      EntityUtils.consumeQuietly(entity);
      return result;
    } catch (IOException e) {
      String msg = "Robot fetch http failure: " + url + ".";
      throw new RobotConnectionException(msg, e);
    }
  }
}
