package com.sap.sme.unicorn.rpc.duboo.registry.client.adapter;

import java.util.List;

public interface ChildListener {

	void childChanged(String path, List<String> children);

}
