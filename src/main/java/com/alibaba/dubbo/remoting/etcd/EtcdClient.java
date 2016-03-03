package com.alibaba.dubbo.remoting.etcd;

import java.util.List;

public interface EtcdClient {
	public void create(String path,boolean isDir);
	public List<String> addChildListener(String path, ChildListener childListener);
	public void removeChildListener(ChildListener childListener);
	public boolean isAvailable();
	public void delete(String urlPath);
}
