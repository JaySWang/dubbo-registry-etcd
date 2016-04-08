package com.sap.sme.unicorn.rpc.duboo.registry.client.adapter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;

import com.alibaba.dubbo.common.URL;
import com.google.common.util.concurrent.ListenableFuture;
import com.sap.sme.unicorn.rpc.duboo.registry.client.etcd.EtcdClientException;
import com.sap.sme.unicorn.rpc.duboo.registry.client.etcd.EtcdNode;
import com.sap.sme.unicorn.rpc.duboo.registry.client.etcd.EtcdResult;
import com.sap.sme.unicorn.rpc.duboo.registry.client.etcd.JEtcdClient;

public class EtcdClientV2 implements EtcdClient {
	JEtcdClient client;
	String protocol = "http://";
	String adress;
	URL url;
	private static final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<String, ConcurrentMap<ChildListener, TargetChildListener>>();

	public EtcdClientV2(URL url) {
		this.url = url;
		adress = url.getAddress();
		client = new JEtcdClient(URI.create(protocol + adress));
	}

	@Override
	public void create(String path) {
		try {
			create(path, false);
		} catch (EtcdClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public List<String> addChildListener(String path, ChildListener listener) {

		try {

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
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		;
		return null;
	}

	private TargetChildListener createTargetChildListener(String path, final ChildListener listener) {
		return new TargetChildListener() {
			public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
				listener.childChanged(parentPath, currentChilds);
			}
		};
	}

	private List<String> addTargetChildListener(String path, final TargetChildListener listener) throws Exception {
		EtcdClientV2 etcdClient = new EtcdClientV2(url);
		return etcdClient.watchChildChanges(path, listener);
	}

	private List<String> watchChildChanges(final String path, final TargetChildListener listener) throws Exception {

		ListenableFuture<EtcdResult> watchFuture = client.watch(path, null, true);
		watchFuture.addListener(new Runnable() {
			public void run() {
				try {
					listener.handleChildChange(path, watchChildChanges(path, listener));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, Executors.newCachedThreadPool());

		EtcdResult etcdResult = client.get(path);
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

	@Override
	public void removeChildListener(String path, ChildListener childListener) {

	}

	@Override
	public boolean isAvailable() {
		try {
			client.set("isAvailable", "true", 5);
		} catch (EtcdClientException e) {
			return false;
		}
		return true;
	}

	@Override
	public void delete(String urlPath) {
		try {
			client.delete(urlPath);
		} catch (EtcdClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public List<String> getChildren(String path) {
		List<String> list = new ArrayList<String>();
		EtcdResult children;
		try {
			children = client.get(path);
			EtcdNode childNode = children.node;
			if (childNode != null) {
				List<EtcdNode> nodes = childNode.nodes;
				if (nodes != null) {
					for (EtcdNode node : nodes) {
						list.add(node.key.substring(path.length() + 1));
					}
				}

			}
		} catch (EtcdClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return list;
	}

	private void create(String path, boolean isDir) throws EtcdClientException {
		int i = path.lastIndexOf('/');
		if (i > 0) {
			create(path.substring(0, i), true);
		}
		if (isDir) {
			EtcdResult result = client.get(path);
			if (result == null) {
				client.createDirectory(path);
			}
		} else {
			client.set(path, "");
		}
	}

}
