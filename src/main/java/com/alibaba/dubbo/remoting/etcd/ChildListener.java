package com.alibaba.dubbo.remoting.etcd;

import java.util.List;

public interface ChildListener {

	void childChanged(String path, List<String> children);

}
