package com.justinsb.etcd;

import java.net.URI;

public class MainTest {

	public static void main(String[] args) {
		 String etcdServerURL = "http://127.0.0.1:4001/";
		EtcdClient 	etcdClient = new EtcdClient(URI.create(etcdServerURL));
		try {
			etcdClient.set("LLAA", "null");
			etcdClient.get("LLA");

		} catch (EtcdClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	//	EtcdRegistry e=	new EtcdRegistry(new URL("http", "127.0.0.1", 4001));
	}

}
