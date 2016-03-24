package com.alibaba.dubbo.remoting.etcd;

import java.net.URI;
import java.util.List;

import com.alibaba.dubbo.common.URL;
import com.justinsb.etcd.EtcdClientException;
import com.justinsb.etcd.JEtcdClient;

public class EtcdClientV2 implements EtcdClient {
	JEtcdClient client;
	String protocol = "http://";

	public EtcdClientV2(URL rul) {
		String adress = rul.getAddress();
		client = new JEtcdClient(URI.create(protocol + adress));
	}

	@Override
	public void create(String path) {
		try {
			client.create(path, false);
		} catch (EtcdClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public List<String> addChildListener(String path, ChildListener childListener) {

		try {
			return client.addChildListener(path, childListener);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		;
		return null;
	}

	@Override
	public void removeChildListener(String path, ChildListener childListener) {
		
	}

	@Override
	public boolean isAvailable() {
		// TODO Auto-generated method stub
		return false;
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
	public void close() throws InterruptedException {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> getChildren(String path) {
		try {
			client.getChildren(path);
		} catch (EtcdClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
