package com.justinsb.etcd;

import java.net.URI;

public class MainTest {

	public static void main(String[] args) {
		 String etcdServerURL = "http://127.0.0.1:4001/";
		EtcdClient 	etcdClient = new EtcdClient(URI.create(etcdServerURL));
		try {
			etcdClient.set("/dubbo/com.alibaba.dubbo.demo.DemoService/providers/dubbo%3A%2F%2F10.59.184.73%3A20880%2Fcom.alibaba.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddemo-provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26loadbalance%3Droundrobin%26methods%3DsayHello%26owner%3Dwilliam%26pid%3D6620%26side%3Dprovider%26timestamp%3D1456126275206", "null");

		} catch (EtcdClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	//	EtcdRegistry e=	new EtcdRegistry(new URL("http", "127.0.0.1", 4001));
	}

}
