package com.sap.sme.unicorn.rpc.duboo.registry;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.registry.Registry;
import com.alibaba.dubbo.registry.support.AbstractRegistryFactory;


public class EtcdRegistryFactory extends AbstractRegistryFactory {
	

	public Registry createRegistry(URL url) {
        return new EtcdRegistry(url);
    }

}