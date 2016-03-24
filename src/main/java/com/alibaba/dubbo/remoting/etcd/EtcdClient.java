package com.alibaba.dubbo.remoting.etcd;

import java.util.List;

public interface EtcdClient {
	public void create(String path);

	public List<String> addChildListener(String path, ChildListener childListener);

	public void removeChildListener(String Path, ChildListener childListener);

	public boolean isAvailable();

	public void delete(String urlPath);

	public void close() throws InterruptedException;

	public List<String> getChildren(String path);
}
