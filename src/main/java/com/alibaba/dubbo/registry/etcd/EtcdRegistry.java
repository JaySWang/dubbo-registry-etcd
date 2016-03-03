
package com.alibaba.dubbo.registry.etcd;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.alibaba.dubbo.remoting.etcd.ChildListener;
import com.alibaba.dubbo.remoting.etcd.EtcdClient;
import com.alibaba.dubbo.remoting.etcd.EtcdV3Client;
import com.alibaba.dubbo.rpc.RpcException;

/**
 * EtcdRegistry
 * 
 * @author dyson.wang
 */
public class EtcdRegistry extends FailbackRegistry {

	private final static String DEFAULT_ROOT = "dubbo";
	private final String root;
	private final static Logger logger = LoggerFactory.getLogger(EtcdRegistry.class);
	private final Set<String> anyServices = new ConcurrentHashSet<String>();
	private final ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>> serviceListeners = new ConcurrentHashMap<URL, ConcurrentMap<NotifyListener, ChildListener>>();
	private EtcdClient etcdClient;

	public EtcdRegistry(URL url) {
		super(url);
		logger.debug("star EtcdRegistry");
		String group = url.getParameter(Constants.GROUP_KEY, DEFAULT_ROOT);
		if (!group.startsWith(Constants.PATH_SEPARATOR)) {
			group = Constants.PATH_SEPARATOR + group;
		}
		this.root = group;
		String host = url.getHost();
		int port = url.getPort();
		etcdClient = new EtcdV3Client(host, port);
	}

	public boolean isAvailable() {
		return etcdClient.isAvailable();
	}

	@Override
	protected void doRegister(URL url) {
		logger.debug("doRegister: " + url);
		etcdClient.create(toUrlPath(url), false);
	}

	private String toUrlPath(URL url) {
		return toCategoryPath(url) + Constants.PATH_SEPARATOR + URL.encode(url.toFullString());
	}

	private String toCategoryPath(URL url) {
		return toServicePath(url) + Constants.PATH_SEPARATOR
				+ url.getParameter(Constants.CATEGORY_KEY, Constants.DEFAULT_CATEGORY);
	}

	private String toServicePath(URL url) {
		String name = url.getServiceInterface();
		if (Constants.ANY_VALUE.equals(name)) {
			return toRootPath();
		}
		return toRootDir() + URL.encode(name);
	}

	private String toRootPath() {
		return root;
	}

	private String toRootDir() {
		if (root.equals(Constants.PATH_SEPARATOR)) {
			return root;
		}
		return root + Constants.PATH_SEPARATOR;
	}

	@Override
	protected void doUnregister(URL url) {
		logger.debug("doUnregister: " + url);
		etcdClient.delete(toUrlPath(url));
	}

	@Override
	protected void doSubscribe(final URL url, final NotifyListener listener) {
		try {
			if (Constants.ANY_VALUE.equals(url.getServiceInterface())) {
				doSubscribeForAnyInterfaces(url, listener);
			} else {
				List<URL> urls = new ArrayList<URL>();
				ConcurrentMap<NotifyListener, ChildListener> childListeners = serviceListeners.get(url);
				if (childListeners == null) {
					childListeners = new ConcurrentHashMap<NotifyListener, ChildListener>();
					serviceListeners.putIfAbsent(url, childListeners);
				}
				for (String path : toCategoriesPath(url)) {
					etcdClient.create(path, true);
					ChildListener categoriesListener = childListeners.get(listener);
					if (categoriesListener == null) {
						categoriesListener = new ChildListener() {
							public void childChanged(String path, List<String> currentChilds) {
								EtcdRegistry.this.notify(url, listener, toUrlsWithEmpty(url, path, currentChilds));
							}
						};

						childListeners.putIfAbsent(listener, categoriesListener);
					}
					List<String> urlsOfTheCategory = etcdClient.addChildListener(path, categoriesListener);
					if (urlsOfTheCategory != null) {
						urls.addAll(toUrlsWithEmpty(url, path, urlsOfTheCategory));
					}
				}
				super.notify(url, listener, urls);
			}
		} catch (Throwable e) {
			throw new RpcException("Failed to subscribe " + url + " to etcd " + getUrl() + ", cause: " + e.getMessage(),
					e);
		}
	}

	private void doSubscribeForAnyInterfaces(final URL url, final NotifyListener listener) throws Exception {
		String root = toRootPath();
		ConcurrentMap<NotifyListener, ChildListener> listeners = serviceListeners.get(url);
		if (listeners == null) {
			serviceListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
			listeners = serviceListeners.get(url);
		}
		ChildListener etcdListener = listeners.get(listener);
		if (etcdListener == null) {
			listeners.putIfAbsent(listener, new ChildListener() {
				public void childChanged(String parentPath, List<String> currentChilds) {
					for (String child : currentChilds) {
						child = URL.decode(child);
						if (!anyServices.contains(child)) {
							anyServices.add(child);
							subscribe(url.setPath(child).addParameters(Constants.INTERFACE_KEY, child,
									Constants.CHECK_KEY, String.valueOf(false)), listener);
						}
					}
				}
			});
			etcdListener = listeners.get(listener);
		}
		etcdClient.create(root, true);
		List<String> services = etcdClient.addChildListener(root, etcdListener);

		if (services != null && services.size() > 0) {
			for (String service : services) {
				service = URL.decode(service);
				anyServices.add(service);
				subscribe(url.setPath(service).addParameters(Constants.INTERFACE_KEY, service, Constants.CHECK_KEY,
						String.valueOf(false)), listener);
			}
		}
	}

	private String[] toCategoriesPath(URL url) {
		String[] categroies;
		if (Constants.ANY_VALUE.equals(url.getParameter(Constants.CATEGORY_KEY))) {
			categroies = new String[] { Constants.PROVIDERS_CATEGORY, Constants.CONSUMERS_CATEGORY,
					Constants.ROUTERS_CATEGORY, Constants.CONFIGURATORS_CATEGORY };
		} else {
			categroies = url.getParameter(Constants.CATEGORY_KEY, new String[] { Constants.DEFAULT_CATEGORY });
		}
		String[] paths = new String[categroies.length];
		for (int i = 0; i < categroies.length; i++) {
			paths[i] = toServicePath(url) + Constants.PATH_SEPARATOR + categroies[i];
		}
		return paths;
	}

	private List<URL> toUrlsWithEmpty(URL consumer, String path, List<String> providers) {
		List<URL> urls = toUrlsWithoutEmpty(consumer, providers);
		if (urls == null || urls.isEmpty()) {
			int i = path.lastIndexOf('/');
			String category = i < 0 ? path : path.substring(i + 1);
			URL empty = consumer.setProtocol(Constants.EMPTY_PROTOCOL).addParameter(Constants.CATEGORY_KEY, category);
			urls.add(empty);
		}
		return urls;
	}

	private List<URL> toUrlsWithoutEmpty(URL consumer, List<String> providers) {
		List<URL> urls = new ArrayList<URL>();
		if (providers != null && providers.size() > 0) {
			for (String provider : providers) {
				provider = URL.decode(provider);
				if (provider.contains("://")) {
					URL url = URL.valueOf(provider);
					if (UrlUtils.isMatch(consumer, url)) {
						urls.add(url);
					}
				}
			}
		}
		return urls;
	}

	@Override
	protected void doUnsubscribe(URL url, NotifyListener listener) {
		ConcurrentMap<NotifyListener, ChildListener> childListeners = serviceListeners.get(url);
		ChildListener childListener = childListeners.get(listener);
		etcdClient.removeChildListener(childListener);

	}

}