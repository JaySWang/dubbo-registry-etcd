package com.alibaba.dubbo.remoting.etcd;

import java.util.List;

public interface EtcdClient {
	public void create(String path,boolean isDir);
	public List<String> addChildListener(String path, ChildListener childListener);
}
