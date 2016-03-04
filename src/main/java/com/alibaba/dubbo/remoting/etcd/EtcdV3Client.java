package com.alibaba.dubbo.remoting.etcd;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sap.etcd.adaptor.EtcdClientAdaptor;
import com.sap.etcd.adaptor.WatchListener;

public class EtcdV3Client implements EtcdClient {
	EtcdClientAdaptor etcdClient;
	private static Map<ChildListener, ConcurrentHashMap<String, WatchListener>> categoriesListeners = new ConcurrentHashMap<ChildListener, ConcurrentHashMap<String, WatchListener>>();

	public EtcdV3Client(String host, int port) {
		etcdClient = new EtcdClientAdaptor(host, port);
	}

	public void create(String path) {
		etcdClient.create(path);
	}

	public List<String> addChildListener(String path, final ChildListener childListener) {
		ConcurrentHashMap<String, WatchListener> categoryWatchListeners = categoriesListeners.get(childListener);
		if (categoryWatchListeners == null) {
			categoryWatchListeners = new ConcurrentHashMap<String, WatchListener>();
		}
		if(!categoriesListeners.containsKey(childListener))
		{
			categoriesListeners.put(childListener, categoryWatchListeners);
		}
		WatchListener categoryWatchlistener = categoryWatchListeners.get(path);
		if (categoryWatchlistener == null) {
			categoryWatchlistener = new WatchListener() {
				public void update(String path, List<String> newChildren) {
					childListener.childChanged(path, newChildren);
				}
			};
		}
		if(!categoryWatchListeners.containsKey(path)){
			categoryWatchListeners.put(path, categoryWatchlistener);
		}
		List<String> services = etcdClient.addWatchListener(path, categoryWatchlistener);
		return services;
	}

	public void removeChildListener(ChildListener categoriesListener) {
		ConcurrentHashMap<String, WatchListener> categoryWatchListeners = categoriesListeners.get(categoriesListener);
		if (categoryWatchListeners != null) {
			Collection<WatchListener> watchListeners = categoryWatchListeners.values();
			for (WatchListener watchListener : watchListeners) {
				etcdClient.removeWatchListener(watchListener);
			}
		}
	}

	public boolean isAvailable() {
		return etcdClient.isAvailable();
	}

	public void delete(String urlPath) {
		etcdClient.delete(urlPath);
	}

	public void close() throws InterruptedException {
		etcdClient.close();		
	}

	public List<String> getChildren(String path) {
		return 	etcdClient.getChildren(path);
	}
}
