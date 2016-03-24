package com.justinsb.etcd;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.alibaba.dubbo.remoting.etcd.ChildListener;
import com.alibaba.dubbo.remoting.etcd.TargetChildListener;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class JEtcdClient {
	CloseableHttpAsyncClient httpClient = buildDefaultHttpClient();
	static final Gson gson = new GsonBuilder().create();
	private static final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<String, ConcurrentMap<ChildListener, TargetChildListener>>();

	CloseableHttpAsyncClient buildDefaultHttpClient() {
		// TODO: Increase timeout??
		RequestConfig requestConfig = RequestConfig.custom().build();
		CloseableHttpAsyncClient httpClient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build();
		httpClient.start();
		return httpClient;
	}

	final URI baseUri;

	public JEtcdClient(URI baseUri) {
		String uri = baseUri.toString();
		if (!uri.endsWith("/")) {
			uri += "/";
			baseUri = URI.create(uri);
		}
		this.baseUri = baseUri;
	}

	/**
	 * Retrieves a key. Returns null if not found.
	 */
	public EtcdResult get(String key) throws EtcdClientException {
		URI uri = buildKeyUri("v2/keys", key, "");
		HttpGet request = new HttpGet(uri);

		EtcdResult result = syncExecute(request, new int[] { 200, 404 }, 100);
		if (result.isError()) {
			if (result.errorCode == 100) {
				return null;
			}
		}
		return result;
	}

	/**
	 * Deletes the given key
	 */
	public EtcdResult delete(String key) throws EtcdClientException {
		URI uri = buildKeyUri("v2/keys", key, "");
		HttpDelete request = new HttpDelete(uri);

		return syncExecute(request, new int[] { 200, 404 });
	}

	/**
	 * Deletes the given dir and it child nodes
	 */
	public EtcdResult deleteDir(String key) throws EtcdClientException {
		URI uri = buildKeyUri("v2/keys", key, "?recursive=true");
		HttpDelete request = new HttpDelete(uri);

		return syncExecute(request, new int[] { 200, 404 });
	}

	/**
	 * Sets a key to a new value
	 */
	public EtcdResult set(String key, String value) throws EtcdClientException {
		return set(key, value, null);
	}

	/**
	 * Sets a key to a new value with an (optional) ttl
	 */

	public EtcdResult set(String key, String value, Integer ttl) throws EtcdClientException {
		List<BasicNameValuePair> data = Lists.newArrayList();
		data.add(new BasicNameValuePair("value", value));
		if (ttl != null) {
			data.add(new BasicNameValuePair("ttl", Integer.toString(ttl)));
		}

		return set0(key, data, new int[] { 200, 201 });
	}

	/**
	 * Creates a directory
	 */
	public EtcdResult createDirectory(String key) throws EtcdClientException {
		List<BasicNameValuePair> data = Lists.newArrayList();
		data.add(new BasicNameValuePair("dir", "true"));
		return set0(key, data, new int[] { 102, 200, 201 });
	}

	/**
	 * Lists a directory
	 */
	public List<EtcdNode> listDirectory(String key) throws EtcdClientException {
		EtcdResult result = get(key + "/");
		if (result == null || result.node == null) {
			return null;
		}
		return result.node.nodes;
	}

	/**
	 * Delete a directory
	 */
	public EtcdResult deleteDirectory(String key) throws EtcdClientException {
		URI uri = buildKeyUri("v2/keys", key, "?dir=true");
		HttpDelete request = new HttpDelete(uri);
		return syncExecute(request, new int[] { 202 });
	}

	/**
	 * Sets a key to a new value, if the value is a specified value
	 */
	public EtcdResult cas(String key, String prevValue, String value) throws EtcdClientException {
		List<BasicNameValuePair> data = Lists.newArrayList();
		data.add(new BasicNameValuePair("value", value));
		data.add(new BasicNameValuePair("prevValue", prevValue));

		return set0(key, data, new int[] { 200, 412 }, 101);
	}

	/**
	 * Watches the given subtree
	 */
	public ListenableFuture<EtcdResult> watch(String key) throws EtcdClientException {
		return watch(key, null, false);
	}

	/**
	 * Watches the given subtree
	 */
	public ListenableFuture<EtcdResult> watch(String key, Long index, boolean recursive) throws EtcdClientException {
		String suffix = "?wait=true";
		if (index != null) {
			suffix += "&waitIndex=" + index;
		}
		if (recursive) {
			suffix += "&recursive=true";
		}
		URI uri = buildKeyUri("v2/keys", key, suffix);

		HttpGet request = new HttpGet(uri);

		return asyncExecute(request, new int[] { 200 });
	}

	/**
	 * Gets the etcd version
	 */
	public String getVersion() throws EtcdClientException {
		URI uri = baseUri.resolve("version");

		HttpGet request = new HttpGet(uri);

		// Technically not JSON, but it'll work
		// This call is the odd one out
		JsonResponse s = syncExecuteJson(request, 200);
		if (s.httpStatusCode != 200) {
			throw new EtcdClientException("Error while fetching versions", s.httpStatusCode);
		}
		return s.json;
	}

	private EtcdResult set0(String key, List<BasicNameValuePair> data, int[] httpErrorCodes, int... expectedErrorCodes)
			throws EtcdClientException {
		URI uri = buildKeyUri("v2/keys", key, "");

		HttpPut request = new HttpPut(uri);

		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(data, Charsets.UTF_8);
		request.setEntity(entity);

		return syncExecute(request, httpErrorCodes, expectedErrorCodes);
	}

	public EtcdResult listChildren(String key) throws EtcdClientException {
		URI uri = buildKeyUri("v2/keys", key, "/");
		HttpGet request = new HttpGet(uri);

		EtcdResult result = syncExecute(request, new int[] { 200 });
		return result;
	}

	protected ListenableFuture<EtcdResult> asyncExecute(HttpUriRequest request, int[] expectedHttpStatusCodes,
			final int... expectedErrorCodes) throws EtcdClientException {
		ListenableFuture<JsonResponse> json = asyncExecuteJson(request, expectedHttpStatusCodes);
		return Futures.transform(json, new AsyncFunction<JsonResponse, EtcdResult>() {
			public ListenableFuture<EtcdResult> apply(JsonResponse json) throws Exception {
				EtcdResult result = jsonToEtcdResult(json, expectedErrorCodes);
				return Futures.immediateFuture(result);
			}
		});
	}

	protected EtcdResult syncExecute(HttpUriRequest request, int[] expectedHttpStatusCodes, int... expectedErrorCodes)
			throws EtcdClientException {
		try {
			return asyncExecute(request, expectedHttpStatusCodes, expectedErrorCodes).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();

			throw new EtcdClientException("Interrupted during request", e);
		} catch (ExecutionException e) {
			throw unwrap(e);
		}
		// String json = syncExecuteJson(request);
		// return jsonToEtcdResult(json, expectedErrorCodes);
	}

	private EtcdClientException unwrap(ExecutionException e) {
		Throwable cause = e.getCause();
		if (cause instanceof EtcdClientException) {
			return (EtcdClientException) cause;
		}
		return new EtcdClientException("Error executing request", e);
	}

	private EtcdResult jsonToEtcdResult(JsonResponse response, int... expectedErrorCodes) throws EtcdClientException {
		if (response == null || response.json == null) {
			return null;
		}
		EtcdResult result = parseEtcdResult(response.json);

		if (result.isError()) {
			if (!contains(expectedErrorCodes, result.errorCode)) {
				throw new EtcdClientException(result.message, result);
			}
		}
		return result;
	}

	private EtcdResult parseEtcdResult(String json) throws EtcdClientException {
		EtcdResult result;
		try {
			result = gson.fromJson(json, EtcdResult.class);
		} catch (JsonParseException e) {
			throw new EtcdClientException("Error parsing response from etcd", e);
		}
		return result;
	}

	private static boolean contains(int[] list, int find) {
		for (int i = 0; i < list.length; i++) {
			if (list[i] == find) {
				return true;
			}
		}
		return false;
	}

	protected List<EtcdResult> syncExecuteList(HttpUriRequest request) throws EtcdClientException {
		JsonResponse response = syncExecuteJson(request, 200);
		if (response.json == null) {
			return null;
		}

		if (response.httpStatusCode != 200) {
			EtcdResult etcdResult = parseEtcdResult(response.json);
			throw new EtcdClientException("Error listing keys", etcdResult);
		}

		try {
			List<EtcdResult> ret = new ArrayList<EtcdResult>();
			JsonParser parser = new JsonParser();
			JsonArray array = parser.parse(response.json).getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				EtcdResult next = gson.fromJson(array.get(i), EtcdResult.class);
				ret.add(next);
			}
			return ret;
		} catch (JsonParseException e) {
			throw new EtcdClientException("Error parsing response from etcd", e);
		}
	}

	protected JsonResponse syncExecuteJson(HttpUriRequest request, int... expectedHttpStatusCodes)
			throws EtcdClientException {
		try {
			return asyncExecuteJson(request, expectedHttpStatusCodes).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new EtcdClientException("Interrupted during request processing", e);
		} catch (ExecutionException e) {
			throw unwrap(e);
		}
	}

	protected ListenableFuture<JsonResponse> asyncExecuteJson(HttpUriRequest request,
			final int[] expectedHttpStatusCodes) throws EtcdClientException {
		ListenableFuture<HttpResponse> response = asyncExecuteHttp(request);

		return Futures.transform(response, new AsyncFunction<HttpResponse, JsonResponse>() {
			public ListenableFuture<JsonResponse> apply(HttpResponse httpResponse) throws Exception {
				JsonResponse json = extractJsonResponse(httpResponse, expectedHttpStatusCodes);
				return Futures.immediateFuture(json);
			}
		});
	}

	/**
	 * We need the status code & the response to parse an error response.
	 */
	static class JsonResponse {
		final String json;
		final int httpStatusCode;

		public JsonResponse(String json, int statusCode) {
			this.json = json;
			this.httpStatusCode = statusCode;
		}

	}

	protected JsonResponse extractJsonResponse(HttpResponse httpResponse, int[] expectedHttpStatusCodes)
			throws EtcdClientException {
		try {
			StatusLine statusLine = httpResponse.getStatusLine();
			int statusCode = statusLine.getStatusCode();

			String json = null;

			if (httpResponse.getEntity() != null) {
				try {
					json = EntityUtils.toString(httpResponse.getEntity());
				} catch (IOException e) {
					throw new EtcdClientException("Error reading response", e);
				}
			}

			if (!contains(expectedHttpStatusCodes, statusCode)) {
				if (statusCode == 400 && json != null) {
					// More information in JSON
				} else {
					throw new EtcdClientException("Error response from etcd: " + statusLine.getReasonPhrase(),
							statusCode);
				}
			}

			return new JsonResponse(json, statusCode);
		} finally {
			close(httpResponse);
		}
	}

	private URI buildKeyUri(String prefix, String key, String suffix) {
		StringBuilder sb = new StringBuilder();
		sb.append(prefix);
		if (key.startsWith("/")) {
			key = key.substring(1);
		}
		for (String token : Splitter.on('/').split(key)) {
			sb.append("/");
			sb.append(urlEscape(token));
		}
		sb.append(suffix);

		URI uri = baseUri.resolve(sb.toString());
		return uri;
	}

	protected ListenableFuture<HttpResponse> asyncExecuteHttp(HttpUriRequest request) {
		final SettableFuture<HttpResponse> future = SettableFuture.create();

		httpClient.execute(request, new FutureCallback<HttpResponse>() {
			public void completed(HttpResponse result) {
				future.set(result);
			}

			public void failed(Exception ex) {
				future.setException(ex);
			}

			public void cancelled() {
				future.setException(new InterruptedException());
			}
		});

		return future;
	}

	public static void close(HttpResponse response) {
		if (response == null) {
			return;
		}
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			EntityUtils.consumeQuietly(entity);
		}
	}

	protected static String urlEscape(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException();
		}
	}

	public static String format(Object o) {
		try {
			return gson.toJson(o);
		} catch (Exception e) {
			return "Error formatting: " + e.getMessage();
		}
	}

	public void create(String path, boolean isDir) throws EtcdClientException {
		int i = path.lastIndexOf('/');
		if (i > 0) {
			create(path.substring(0, i), true);
		}
		if (isDir) {
			EtcdResult result = get(path);
			if (result == null) {
				createDirectory(path);
			}
		} else {
			set(path, "");
		}
	}

	public List<String> addChildListener(String path, ChildListener listener) throws Exception {

		ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
		if (listeners == null) {
			childListeners.putIfAbsent(path, new ConcurrentHashMap<ChildListener, TargetChildListener>());
			listeners = childListeners.get(path);
		}
		TargetChildListener targetListener = listeners.get(listener);
		if (targetListener == null) {
			listeners.putIfAbsent(listener, createTargetChildListener(path, listener));
			targetListener = listeners.get(listener);
		}
		return addTargetChildListener(path, targetListener);

	}

	public TargetChildListener createTargetChildListener(String path, final ChildListener listener) {
		return new TargetChildListener() {
			public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
				listener.childChanged(parentPath, currentChilds);
			}
		};
	}

	public List<String> addTargetChildListener(String path, final TargetChildListener listener) throws Exception {
		JEtcdClient etcdClient = new JEtcdClient(baseUri);
		return etcdClient.watchChildChanges(path, listener);
	}

	private List<String> watchChildChanges(final String path, final TargetChildListener listener) throws Exception {

		ListenableFuture<EtcdResult> watchFuture = watch(path, null, true);
		watchFuture.addListener(new Runnable() {
			public void run() {
				try {
					listener.handleChildChange(path, watchChildChanges(path, listener));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, Executors.newCachedThreadPool());

		EtcdResult etcdResult = get(path);
		final List<String> currentChildren = getURLsFromResult(path, etcdResult);
		return currentChildren;
	}

	private List<String> getURLsFromResult(String path, EtcdResult etcdResult) {

		List<String> list = new ArrayList<String>();
		if (etcdResult == null) {
			return null;
		}
		EtcdNode childNode = etcdResult.node;
		if (childNode != null) {
			List<EtcdNode> nodes = childNode.nodes;
			if (nodes != null) {
				for (EtcdNode node : nodes) {
					list.add(node.key.substring(path.length() + 1));
				}
			}

		}
		return list;

	}

	public List<String> getChildren(String path) throws EtcdClientException {
		List<String> list = new ArrayList<String>();
		EtcdResult children = get(path);
		EtcdNode childNode = children.node;
		if (childNode != null) {
			List<EtcdNode> nodes = childNode.nodes;
			if (nodes != null) {
				for (EtcdNode node : nodes) {
					list.add(node.key.substring(path.length() + 1));
				}
			}

		}
		return list;
	}

}
