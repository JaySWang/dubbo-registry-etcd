package com.alibaba.dubbo.remoting.etcd;

import java.util.List;

import com.sap.etcd.adaptor.EtcdClientAdaptor;
import com.sap.etcd.adaptor.WatchListener;

public class EtcdV3Client implements EtcdClient {
	EtcdClientAdaptor etcdClient;

	public EtcdV3Client(String host, int port) {
		etcdClient = new EtcdClientAdaptor(host, port);
	}

	public void create(String path, boolean isDir) {
		etcdClient.create(path, isDir);

	}

	public List<String> addChildListener(String path, final ChildListener childListener) {
		List<String> services = etcdClient.addChildListener(path, new WatchListener() {
			public void update(String path, List<String> newChildren) {
				childListener.childChanged(path, newChildren);
			}
		});
		return services;
	}

}
