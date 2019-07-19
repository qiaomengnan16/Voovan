    package org.voovan.tools.pool;

import org.voovan.Global;
import org.voovan.tools.TEnv;
import org.voovan.tools.hashwheeltimer.HashWheelTask;
import org.voovan.tools.json.JSON;
import org.voovan.tools.reflect.TReflect;
import org.voovan.tools.reflect.annotation.NotSerialization;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 对象池
 *      支持超时清理,并且支持指定对象的借出和归还操作
 *      仅仅按照时间长短控制对象的存活周期
 *
 * @author helyho
 * <p>
 * Vestful Framework.
 * WebSite: https://github.com/helyho/Vestful
 * Licence: Apache v2 License
 */
public class ObjectPool<T> {

    //<ID, 缓存的对象>
    private volatile ConcurrentHashMap<Long, PooledObject<T>> objects = new ConcurrentHashMap<Long, PooledObject<T>>();
    //未解出的对象 ID
    private volatile ConcurrentLinkedQueue<Long> unborrowedIdList = new ConcurrentLinkedQueue<Long>();

    private long aliveTime = 0;
    private boolean autoRefreshOnGet = true;
    private Function<T, Boolean> destory;
    private Supplier<T> supplier = null;
    private Function<T, Boolean> validator = null;
    private int minSize = 0;
    private int maxSize = Integer.MAX_VALUE;
    private int interval = 1;

    Object lock = new Object();
    /**
     * 构造一个对象池
     * @param aliveTime 对象存活时间,小于等于0时为一直存活,单位:秒
     * @param autoRefreshOnGet 获取对象时刷新对象存活时间
     */
    public ObjectPool(long aliveTime, boolean autoRefreshOnGet){
        this.aliveTime = aliveTime;
        this.autoRefreshOnGet = autoRefreshOnGet;
    }

    /**
     * 构造一个对象池
     * @param aliveTime 对象存活时间,单位:秒
     */
    public ObjectPool(long aliveTime){
        this.aliveTime = aliveTime;
    }

    /**
     * 构造一个对象池
     */
    public ObjectPool(){
    }

    public long getAliveTime() {
        return aliveTime;
    }

    public ObjectPool autoRefreshOnGet(boolean autoRefreshOnGet) {
        this.autoRefreshOnGet = autoRefreshOnGet;
        return this;
    }

    public int getMinSize() {
        return minSize;
    }

    public ObjectPool minSize(int minSize) {
        this.minSize = minSize;
        return this;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public ObjectPool maxSize(int maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    public int getInterval() {
        return interval;
    }

    public ObjectPool interval(int interval) {
        this.interval = interval;
        return this;
    }

    /**
     * 获取对象构造函数
     *      在对象被构造工作
     * @return 对象构造函数
     */
    public Supplier getSupplier() {
        return supplier;
    }

    /**
     * 设置对象构造函数
     *      对象被构造是用的函数
     * @param supplier 对象构造函数
     * @return ObjectPool 对象
     */
    public ObjectPool supplier(Supplier supplier) {
        this.supplier = supplier;
        return this;
    }

    /**
     * 验证器
     *  在获取对象时验证
     * @return Function 对象
     */
    public Function<T, Boolean> getValidator() {
        return validator;
    }

    /**
     * 设置验证器
     *  在获取对象时验证
     * @param validator Function 对象
     * @return ObjectPool 对象
     */
    public ObjectPool setValidator(Function<T, Boolean> validator) {
        this.validator = validator;
        return this;
    }

    /**
     * 获取对象销毁函数
     *      在对象被销毁前工作
     * @return 对象销毁函数
     */
    public Function destory() {
        return destory;
    }

    /**
     * 设置对象销毁函数
     *      在对象被销毁前工作
     * @param destory 对象销毁函数
     * @return ObjectPool 对象
     */
    public ObjectPool destory(Function destory) {
        this.destory = destory;
        return this;
    }

    /**
     * 设置对象池的对象存活时间
     * @param aliveTime 对象存活时间,单位:秒
     * @return ObjectPool 对象
     */
    public ObjectPool aliveTime(long aliveTime) {
        this.aliveTime = aliveTime;
        return this;
    }

    /**
     * 生成ObjectId
     * @return 生成的ObjectId
     */
    private long genObjectId(){
        return Global.UNIQUE_ID.nextNumber();
    }

    /**
     * 是否获取对象时刷新对象存活时间
     * @return 是否获取对象时刷新对象存活时间
     */
    public boolean isAutoRefreshOnGet(){
        return autoRefreshOnGet;
    }

    /**
     * 获取池中的对象
     * @param id 对象的id
     * @return 池中的对象
     */
    private T get(Long id) {
        if(id != null) {
            PooledObject<T> pooledObject = objects.get(id);
            if (pooledObject != null) {
                pooledObject.setBorrow(true);
                return pooledObject.getObject();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * 增加池中的对象
     * @param obj 增加到池中的对象
     * @return 对象的 id 值
     */
    public Long add(T obj){
        return add(obj, false);
    }

  /**
     * 增加池中的对象
     * @param obj 增加到池中的对象
     * @parma 是否默认为借出状态
     * @return 对象的 id 值
     */
    private Long add(T obj, boolean isBorrow){
        Objects.requireNonNull(obj, "add a null object failed");

        if(obj instanceof PoolObject) {
            if (objects.size() >= maxSize) {
                return null;
            }

            long id = genObjectId();
            ((PoolObject)obj).setPoolObjectId(id);

            PooledObject pooledObject = new PooledObject<T>(this, id, obj);

            objects.put(id, pooledObject);

            //默认借出状态不加入未借出队列
            if(!isBorrow) {
                unborrowedIdList.offer(id);
            }

            synchronized (lock) {
                lock.notify();
            }

            return id;
        } else {
            throw new RuntimeException("the Object is not implement PoolBase interface, please make " + TReflect.getClassName(obj.getClass()) +
                    " implemets PoolObject.class or add use annotation @Pool on  " + TReflect.getClassName(obj.getClass()) +"  and Aop support");
        }
    }

    /**
     * 借出这个对象
     *         如果有提供 supplier 函数, 在没有可借出对象时会构造一个新的对象, 否则返回 null
     * @return 借出的对象的 ID
     */
    public T borrow(){
        Long id = unborrowedIdList.poll();

        //检查是否有重复借出
        if (id != null && objects.get(id).isBorrow()) {
            throw new RuntimeException("Object already borrowed");
        }

        T result = get(id);

        if (result == null && supplier != null) {
            if(objects.size() <= maxSize) {
                synchronized (unborrowedIdList) {
                    result = get(add(supplier.get(), true));
                }
            }
        }

        return result;
    }

    /**
     * 借出对象
     * @param waitTime 超时时间
     * @return 借出的对象, 超时返回 null
     * @throws TimeoutException 超时异常
     */
    public T borrow(long waitTime) throws TimeoutException {
        Long id = null;
        T result = borrow();

        if (result == null) {
            try {

                synchronized (lock) {
                    lock.wait(waitTime);
                    id = unborrowedIdList.poll();
                }

                //检查是否有重复借出
                if (id != null && objects.get(id).isBorrow()) {
                    throw new RuntimeException("Object already borrowed");
                }

                return get(id);
            } catch (InterruptedException e) {
                throw new TimeoutException("borrow failed.");
            }
        } else {
            return result;
        }
    }

    /**
     * 归还借出的对象
     * @param obj 借出的对象
     */
    public void restitution(T obj) {
        //检查是否有重复归还
        Long id = ((PoolObject)obj).getPoolObjectId();

        PooledObject pooledObject = objects.get(id);

        if(!pooledObject.isRemoved() && objects.get(id).setBorrow(false)) {
            unborrowedIdList.offer(id);
            synchronized (lock) {
                lock.notify();
            }
        }
    }

    /**
     * 判断池中是否存在对象
     * @param id 对象的 hash 值
     * @return true: 存在, false: 不存在
     */
    public boolean contains(long id){
        return unborrowedIdList.contains(id);
    }

    /**
     * 移除池中的对象
     * @param id 对象的 hash 值
     */
    public void remove(long id){
        unborrowedIdList.remove(id);

        PooledObject pooledObject = objects.remove(id);
        if(pooledObject!=null) {
            pooledObject.remove();
        }
    }

    /**
     * 获取当前对象池的大小
     * @return 对象池的大小
     */
    public int size(){
        return objects.size();
    }

    /**
     * 出借的对象数
     * @return 出借的对象数
     */
    public int borrowedSize(){
        return objects.size() - unborrowedIdList.size();
    }

    /**
     * 可用的对象数
     * @return 可用的对象数
     */
    public int avaliableSize(){
        return unborrowedIdList.size();
    }


    /**
     * 清理池中所有的对象
     */
    public synchronized void clear(){
        for(PooledObject pooledObject : objects.values()) {
            pooledObject.remove();
        }

        unborrowedIdList.clear();
        objects.clear();
    }

    /**
     * 创建ObjectPool
     * @return ObjectPool 对象
     */
    public ObjectPool create(){
        final ObjectPool finalobjectPool = this;

        Global.getHashWheelTimer().addTask(new HashWheelTask() {
            @Override
            public void run() {
                try {
                    Iterator<PooledObject<T>> iterator = objects.values().iterator();
                    while (iterator.hasNext()) {

                        if(objects.size() <= minSize){
                            return;
                        }

                        PooledObject<T> pooledObject = iterator.next();

                        if (!pooledObject.isAlive()) {
                            if(destory!=null){
                                //如果返回 null 则 清理对象, 如果返回为非 null 则刷新对象
                                if(destory.apply(pooledObject.getObject())){
                                    remove(pooledObject.getId());
                                } else {
                                    pooledObject.refresh();
                                }
                            } else {
                                remove(pooledObject.getId());
                            }
                        }
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }, this.interval, true);

        return this;
    }

    /**
     * 池中缓存的对象模型
     */
    public class PooledObject<T>{
        private long lastVisiediTime;
        private long id;
        @NotSerialization
        private T object;
        @NotSerialization
        private ObjectPool objectCachedPool;
        private AtomicBoolean isBorrow = new AtomicBoolean(false);
        private AtomicBoolean isRemoved = new AtomicBoolean(false);
        private AtomicInteger count = new AtomicInteger();

        public PooledObject(ObjectPool objectCachedPool, long id, T object) {
            this.objectCachedPool = objectCachedPool;
            this.lastVisiediTime = System.currentTimeMillis();
            this.id = id;
            this.object = object;
        }

        protected boolean setBorrow(Boolean isBorrow) {
            if(isBorrow) {
                count.incrementAndGet();
            } else {
                count.decrementAndGet();
            }
            return this.isBorrow.compareAndSet(!isBorrow, isBorrow);
        }

        protected boolean isBorrow() {
            return isBorrow.get();
        }

        public boolean remove() {
            return this.isRemoved.compareAndSet(false, true);
        }

        public boolean isRemoved() {
            return isRemoved.get();
        }

        /**
         * 刷新对象
         */
        public void refresh() {
            lastVisiediTime = System.currentTimeMillis();
        }

        /**
         * 获取对象
         * @return 池中的对象
         */
        public T getObject() {
            if(objectCachedPool.isAutoRefreshOnGet()) {
                refresh();
            }
            return object;
        }

        /**
         * 设置对象
         * @param object 池中的对象
         */
        public void setObject(T object) {
            this.object = object;
        }

        /**
         * 缓存的 id
         * @return 缓存的 id
         */
        public Long getId() {
            return id;
        }

        /**
         * 判断对象是否存活
         * @return true: 对象存活, false: 对象超时
         */
        public boolean isAlive(){
            if(objectCachedPool.aliveTime<=0){
                return true;
            }

            long currentAliveTime = System.currentTimeMillis() - lastVisiediTime;
            if (objectCachedPool.aliveTime>0 && currentAliveTime >= objectCachedPool.aliveTime*1000){
                return false;
            }else{
                return true;
            }
        }

        public String toString(){
            return JSON.toJSON(this).replace("\"","");
        }
    }

    public String toString(){
        return "{Total:" + objects.size() + ", unborrow:" + unborrowedIdList.size()+"}";
    }
}
