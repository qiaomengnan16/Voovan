package org.voovan.tools.threadpool;

import org.voovan.tools.TEnv;
import org.voovan.tools.TProperties;

import java.util.Timer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池
 *
 * @author helyho
 *
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class ThreadPool {
	private static int cpuCoreCount = Runtime.getRuntime().availableProcessors();

	protected static int MIN_POOL_SIZE = cpuCoreCount;
	protected static int MAX_POOL_SIZE = cpuCoreCount;
	protected static int STATUS_INTERVAL = 5000;

	protected static int minPoolTimes = TProperties.getInt("framework", "ThreadPoolMinSize");
	protected static int maxPoolTimes = TProperties.getInt("framework", "ThreadPoolMaxSize");

	protected static ConcurrentHashMap<String, ThreadPoolExecutor> THREAD_POOL_HANDLER = new ConcurrentHashMap<String, ThreadPoolExecutor>();

	/**
	 * 获取线程池最小活动线程数
	 * @return 线程池最小活动线程数
	 */
	public static int getMinPoolSize() {
		MIN_POOL_SIZE = (minPoolTimes == 0 ? 1 : minPoolTimes) * cpuCoreCount;
		MIN_POOL_SIZE = MIN_POOL_SIZE < 8 ? 8 : MIN_POOL_SIZE;
		return MIN_POOL_SIZE;
	}

	/**
	 * 获取线程池最大活动线程数
	 * @return 线程池最大活动线程数
	 */
	public static int getMaxPoolSize() {
		if(minPoolTimes > maxPoolTimes){
			maxPoolTimes = minPoolTimes;
		}
		MAX_POOL_SIZE = (maxPoolTimes == 0 ? 1 : maxPoolTimes) * cpuCoreCount;
		MAX_POOL_SIZE = MAX_POOL_SIZE < 8 ? 8 : MAX_POOL_SIZE;
		return MAX_POOL_SIZE;
	}

	/**
	 * 获取线程池最大活动线程数
	 * @return 线程池最大活动线程数
	 */
	public static int getStatusInterval() {
		STATUS_INTERVAL = TProperties.getInt("framework", "ThreadPoolStatusInterval", 0);
		return STATUS_INTERVAL;
	}

	static{
		getMinPoolSize();
		getMaxPoolSize();
		getStatusInterval();
	}

	private ThreadPool(){
	}

    /**
     * 使用默认配置构造线程池, 配置来自于 fraemwork.properties
     * @param poolName 线程池名称
     * @return 线程池对象
     */
	public static ThreadPoolExecutor createThreadPool(String poolName){
		System.out.println("[THREAD_POOL] " + poolName + " Min size: " + minPoolTimes + "/" + MIN_POOL_SIZE);
		System.out.println("[THREAD_POOL] " + poolName + " Max size: " + maxPoolTimes + "/" + MAX_POOL_SIZE);

		ThreadPoolExecutor threadPoolInstance = createThreadPool(poolName, MIN_POOL_SIZE, MAX_POOL_SIZE, 1000*60);

		//启动线程池自动调整任务
		if(STATUS_INTERVAL>0) {
			Timer timer = new Timer("VOOVAN@THREAD_POOL_TIMER", true);
			ThreadPoolTask threadPoolTask = new ThreadPoolTask(threadPoolInstance);
			timer.schedule(threadPoolTask, 1, 1000);
		}
		return threadPoolInstance;
	}


	/**
	 * 创建线程池
	 * @param poolName 池的名称
	 * @param mimPoolSize 最小线程数
	 * @param maxPoolSize 最大线程数
	 * @param keepAliveTime 线程闲置最大存活时间,单位: 毫秒
	 * @return 线程池对象
	 */
	public static ThreadPoolExecutor createThreadPool(String poolName, int mimPoolSize, int maxPoolSize, int keepAliveTime){

		return createThreadPool(poolName, mimPoolSize, maxPoolSize, keepAliveTime, true , 5);
	}

	/**
	 * 创建线程池
	 * @param poolName 池的名称
	 * @param mimPoolSize 最小线程数
	 * @param maxPoolSize 最大线程数
	 * @param keepAliveTime 线程闲置最大存活时间, 单位: 毫秒
	 * @param daemon 是否是守护线程
	 * @param priority 线程优先级
	 * @return 线程池对象
	 */
	public static ThreadPoolExecutor createThreadPool(String poolName, int mimPoolSize, int maxPoolSize, int keepAliveTime, boolean daemon, int priority){
		ThreadPoolExecutor threadPoolInstance = THREAD_POOL_HANDLER.get(poolName);

		if(threadPoolInstance==null) {
			threadPoolInstance = new ThreadPoolExecutor(mimPoolSize, maxPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(cpuCoreCount * 2000), new DefaultThreadFactory(poolName, daemon, priority));
			//设置allowCoreThreadTimeOut,允许回收超时的线程
			threadPoolInstance.allowCoreThreadTimeOut(true);

			THREAD_POOL_HANDLER.put(poolName, threadPoolInstance);
			ThreadPoolExecutor finalThreadPoolInstance = threadPoolInstance;
			TEnv.addShutDownHook(()->{
				finalThreadPoolInstance.shutdown();
				return true;
			});
		}

		return threadPoolInstance;
	}

	/**
	 * 平滑的关闭线程池
	 * @param threadPoolExecutor 线程池对象
	 */
	public static void gracefulShutdown(ThreadPoolExecutor threadPoolExecutor) {
		threadPoolExecutor.shutdown();

		TEnv.wait(()-> !threadPoolExecutor.isShutdown());
	}
}
