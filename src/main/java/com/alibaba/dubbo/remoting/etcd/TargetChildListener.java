package com.alibaba.dubbo.remoting.etcd;

import java.util.List;

public interface TargetChildListener {
    public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception;

}
