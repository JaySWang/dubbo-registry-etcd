package com.sap.sme.unicorn.rpc.duboo.registry.client.adapter;

import java.util.List;

public interface EtcdClient {
	public void create(String path);

	public List<String> addChildListener(String path, ChildListener childListener);

	public void removeChildListener(String Path, ChildListener childListener);

	public boolean isAvailable();

	public void delete(String urlPath);

	public List<String> getChildren(String path);
}
