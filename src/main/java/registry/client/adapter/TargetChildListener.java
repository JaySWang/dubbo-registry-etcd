package com.sap.sme.unicorn.rpc.duboo.registry.client.adapter;

import java.util.List;

public interface TargetChildListener {
    public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception;

}
