
package com.alibaba.dubbo.registry.etcd;


import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.alibaba.dubbo.common.URL;

public class EtcdRegistryTest {

    String            service     = "com.alibaba.dubbo.test.injvmServie";
    URL               registryUrl = URL.valueOf("etcd://239.255.255.255/");
    URL               serviceUrl  = URL.valueOf("etcd://etcd/" + service
                                                + "?notify=false&methods=test1,test2");
    URL               consumerUrl = URL.valueOf("etcd://consumer/" + service + "?notify=false&methods=test1,test2");
    // EtcdRegistry registry    = new EtcdRegistry(registryUrl);




    @Before
    public void setUp() throws Exception {
        //registry.register(service, serviceUrl);
    }

    @Test
    public void testRegister() {

    }

    @Test
    public void testUnregister() {

    }
    
    @Test
    public void testSubscribe() {

    }
    
    @Test
    public void testUnsubscribe() {

    }

}