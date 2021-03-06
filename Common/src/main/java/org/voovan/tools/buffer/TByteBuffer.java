package org.voovan.tools.buffer;

import org.voovan.Global;
import org.voovan.tools.TByte;
import org.voovan.tools.collection.ObjectThreadPool;
import org.voovan.tools.TUnsafe;
import org.voovan.tools.log.Logger;
import org.voovan.tools.reflect.TReflect;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * ByteBuffer 工具类
 *
 * @author helyho
 *
 * Java Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class TByteBuffer {
    public static ObjectThreadPool<ByteBuffer> BYTE_BUFFER_THREAD_POOL = new ObjectThreadPool<ByteBuffer>(System.getProperty("ThreadBufferPoolSize")!=null ? Integer.valueOf(System.getProperty("ThreadBufferPoolSize")) : 32);

    public static int DEFAULT_BYTE_BUFFER_SIZE = System.getProperty("DefaultBufferSize")!=null ? Integer.valueOf(System.getProperty("BufferSize")) : 1024*4;

    static {
        System.out.println("[SYTSEM] ThreadBufferPoolSize: " + BYTE_BUFFER_THREAD_POOL.getThreadLocalMaxSize());
        System.out.println("[SYTSEM] BufferSize: " + DEFAULT_BYTE_BUFFER_SIZE);
    }

    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocateDirect(0);

    public static Class DIRECT_BYTE_BUFFER_CLASS = EMPTY_BYTE_BUFFER.getClass();

    public static Constructor DIRECT_BYTE_BUFFER_CONSTURCTOR = getConsturctor();
    static {
        DIRECT_BYTE_BUFFER_CONSTURCTOR.setAccessible(true);
    }

    public static Field addressField = ByteBufferField("address");
    public static Field capacityField = ByteBufferField("capacity");
    public static Field attField = ByteBufferField("att");

    private static Constructor getConsturctor(){
        try {
            Constructor constructor = DIRECT_BYTE_BUFFER_CLASS.getDeclaredConstructor(long.class, int.class, Object.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Field ByteBufferField(String fieldName){
        Field field = TReflect.findField(DIRECT_BYTE_BUFFER_CLASS, fieldName);
        field.setAccessible(true);
        return field;
    }

    /**
     * 分配可能手工进行释放的 ByteBuffer
     * @param capacity 容量
     * @return ByteBuffer 对象
     */
    protected static ByteBuffer allocateManualReleaseBuffer(int capacity){
        try {
            long address = (TUnsafe.getUnsafe().allocateMemory(capacity));

            Deallocator deallocator = new Deallocator(new Long(address));

            ByteBuffer byteBuffer =  (ByteBuffer) DIRECT_BYTE_BUFFER_CONSTURCTOR.newInstance(address, capacity, deallocator);

            Cleaner.create(byteBuffer, deallocator);

            return byteBuffer;

        } catch (Exception e) {
            Logger.error("Create ByteBufferChannel error. ", e);
            return null;
        }
    }

    /**
     * 根据框架的非堆内存配置, 分配 ByteBuffer
     * @return ByteBuffer 对象
     */
    public static ByteBuffer allocateDirect() {
        return allocateDirect(DEFAULT_BYTE_BUFFER_SIZE);
    }

    /**
     * 根据框架的非堆内存配置, 分配 ByteBuffer
     * @param capacity 容量
     * @return ByteBuffer 对象
     */
    public static ByteBuffer allocateDirect(int capacity) {

        ByteBuffer byteBuffer = BYTE_BUFFER_THREAD_POOL.get(()->allocateManualReleaseBuffer(capacity));

        if(capacity <= byteBuffer.capacity()) {
            byteBuffer.limit(capacity);
        } else {
            reallocate(byteBuffer, capacity);
        }

        byteBuffer.position(0);
        byteBuffer.limit(capacity);

        return byteBuffer;

    }

    /**
     * 重新分配 byteBuffer 中的空间大小
     * @param byteBuffer byteBuffer对象
     * @param newSize  重新分配的空间大小
     * @return true:成功, false:失败
     */
    public static boolean reallocate(ByteBuffer byteBuffer, int newSize) {

        if(isReleased(byteBuffer)) {
            return false;
        }

        try {
            if(byteBuffer.capacity() > newSize){
                byteBuffer.limit(newSize);
                return true;
            }

            if(!byteBuffer.hasArray()) {
                if(getAtt(byteBuffer) == null){
                    throw new UnsupportedOperationException("JDK's ByteBuffer can't reallocate");
                }
                long address = getAddress(byteBuffer);
                long newAddress = TUnsafe.getUnsafe().reallocateMemory(address, newSize);
                setAddress(byteBuffer, newAddress);
            }else{
                byte[] hb = byteBuffer.array();
                byte[] newHb = Arrays.copyOf(hb, newSize);
                TReflect.setFieldValue(byteBuffer, "hb", newHb);
            }

            //重置容量
            capacityField.set(byteBuffer, newSize);

            return true;

        }catch (ReflectiveOperationException e){
            Logger.error("TByteBuffer.reallocate() Error. ", e);
        }
        return false;
    }

    /**
     * 移动 Bytebuffer 中的数据
     *       以Bytebuffer.position()为原点,移动 offset 个位置
     * @param byteBuffer byteBuffer对象
     * @param offset 相对当前 ByteBuffer.position 的偏移量
     * @return true:成功, false:失败
     */
    public static boolean moveData(ByteBuffer byteBuffer, int offset) {
        try {

            if(offset==0){
                return true;
            }

            int newLimit = byteBuffer.limit()+offset;
            int newPosition = byteBuffer.position() + offset;

            if(newPosition < 0){
                return false;
            }

            if(newLimit > byteBuffer.capacity()){
                reallocate(byteBuffer, newLimit);
            }

            if(!byteBuffer.hasArray()) {
                long address = getAddress(byteBuffer);
                if(address!=0) {
                    long startAddress = address + byteBuffer.position();
                    long targetAddress = address + newPosition;
                    if (address > targetAddress) {
                        targetAddress = address;
                    }
                    TUnsafe.getUnsafe().copyMemory(startAddress, targetAddress, byteBuffer.remaining());
                }
            }else{
                byte[] hb = byteBuffer.array();
                System.arraycopy(hb, byteBuffer.position(), hb, newPosition, byteBuffer.remaining());
            }

            byteBuffer.limit(newLimit);
            byteBuffer.position(newPosition);
            return true;
        }catch (ReflectiveOperationException e){
            Logger.error("TByteBuffer.moveData() Error.", e);
        }
        return false;
    }

    /**
     * 复制一个 Bytebuffer 对象
     * @param byteBuffer 原 ByteBuffer 对象
     * @return 复制出的对象
     * @throws ReflectiveOperationException 反射错误
     */
    public static ByteBuffer copy(ByteBuffer byteBuffer) throws ReflectiveOperationException {

        ByteBuffer newByteBuffer = TByteBuffer.allocateDirect(byteBuffer.capacity());

        if(byteBuffer.hasRemaining()) {
            long address = getAddress(byteBuffer);
            long newAddress = getAddress(newByteBuffer);
            TUnsafe.getUnsafe().copyMemory(address, newAddress + byteBuffer.position(), byteBuffer.remaining());
        }

        newByteBuffer.position(byteBuffer.position());
        newByteBuffer.limit(byteBuffer.limit());

        return newByteBuffer;
    }

    /**
     * 释放byteBuffer
     *      释放对外的 bytebuffer
     * @param byteBuffer bytebuffer 对象
     */
    public static void release(ByteBuffer byteBuffer) {
        if(byteBuffer == null){
            return;
        }

        try {
            if (byteBuffer != null) {
                long address = getAddress(byteBuffer);
                Object att = getAtt(byteBuffer);
                if (address!=0 && att!=null && att.getClass() == Deallocator.class) {
                    if(address!=0) {
                        byteBuffer.clear();

                        if(byteBuffer.capacity() > DEFAULT_BYTE_BUFFER_SIZE){
                            reallocate(byteBuffer, DEFAULT_BYTE_BUFFER_SIZE);
                        }

                        BYTE_BUFFER_THREAD_POOL.release(byteBuffer, ()->{
                            try {
                                synchronized (byteBuffer) {
                                    TUnsafe.getUnsafe().freeMemory(address);
                                    setAddress(byteBuffer, 0);
                                    return true;
                                }
                            } catch (ReflectiveOperationException e) {
                                e.printStackTrace();
                            }

                            return false;
                        });


                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }


    /**
     * 判断是否已经释放
     * @param byteBuffer ByteBuffer 对象
     * @return true: 已释放, false: 未释放
     */
    public static boolean isReleased(ByteBuffer byteBuffer){

        if(byteBuffer==null){
            return true;
        }

        try {
            return getAddress(byteBuffer) == 0;
        }catch (ReflectiveOperationException e){
            return true;
        }
    }

    /**
     * 将ByteBuffer转换成 byte 数组
     * @param bytebuffer ByteBuffer 对象
     * @return byte 数组
     */
    public static byte[] toArray(ByteBuffer bytebuffer){
        if(!bytebuffer.hasArray()) {
            if(isReleased(bytebuffer)) {
                return new byte[0];
            }

            bytebuffer.mark();
            int position = bytebuffer.position();
            int limit = bytebuffer.limit();
            byte[] buffers = new byte[limit-position];
            bytebuffer.get(buffers);
            bytebuffer.reset();
            return buffers;
        }else{
            return Arrays.copyOfRange(bytebuffer.array(), 0, bytebuffer.limit());
        }
    }

    /**
     * 将 Bytebuffer 转换成 字符串
     * @param bytebuffer Bytebuffer 对象
     * @param charset 字符集
     * @return 字符串对象
     */
    public static String toString(ByteBuffer bytebuffer,String charset) {
        try {
            return new String(toArray(bytebuffer), charset);
        } catch (UnsupportedEncodingException e) {
            Logger.error(charset+" is not supported",e);
            return null;
        }
    }

    /**
     * 将 Bytebuffer 转换成 字符串
     * @param bytebuffer Bytebuffer 对象
     * @return 字符串对象
     */
    public static String toString(ByteBuffer bytebuffer) {
        return toString(bytebuffer, "UTF-8");
    }

    /**
     * 查找特定 byte 标识的位置
     *     byte 标识数组第一个字节的索引位置
     * @param byteBuffer Bytebuffer 对象
     * @param mark byte 标识数组
     * @return 第一个字节的索引位置
     */
    public static int indexOf(ByteBuffer byteBuffer, byte[] mark){

        if(isReleased(byteBuffer)) {
            return -1;
        }

        if(byteBuffer.remaining() == 0){
            return -1;
        }

        int originPosition = byteBuffer.position();
        int length = byteBuffer.remaining();

        if(length < mark.length){
            return -1;
        }

        int index = -1;

        int i = byteBuffer.position();
        int j = 0;

        while(i <= (byteBuffer.limit() - mark.length + j )  ){
            if(byteBuffer.get(i) != mark[j] ){
                if(i == (byteBuffer.limit() - mark.length + j )){
                    break;
                }
                int pos = TByte.byteIndexOf(mark, byteBuffer.get(i+mark.length-j));
                if( pos== -1){
                    i = i + mark.length + 1 - j;
                    j = 0 ;
                }else{
                    i = i + mark.length - pos - j;
                    j = 0;
                }
            }else{
                if(j == (mark.length - 1)){
                    i = i - j + 1 ;
                    j = 0;
                    index  = i-j - 1;
                    break;
                }else{
                    i++;
                    j++;
                }
            }
        }

        byteBuffer.position(originPosition);

        return index;
    }


    /**
     * 获取内存地址
     * @param byteBuffer bytebuffer 对象
     * @return 内存地址
     * @throws ReflectiveOperationException 反射异常
     */
    public static Long getAddress(ByteBuffer byteBuffer) throws ReflectiveOperationException {
        return (Long) addressField.get(byteBuffer);
    }

    /**
     * 设置内存地址
     * @param byteBuffer bytebuffer 对象
     * @param address 内存地址
     * @throws ReflectiveOperationException 反射异常
     */
    public static void setAddress(ByteBuffer byteBuffer, long address) throws ReflectiveOperationException {
        addressField.set(byteBuffer, address);
        Object att = getAtt(byteBuffer);
        if(att!=null && att.getClass() == Deallocator.class){
            ((Deallocator) att).setAddress(address);
        }
    }

    /**
     * 获取附加对象
     * @param byteBuffer bytebuffer 对象
     * @return 附加对象
     * @throws ReflectiveOperationException 反射异常
     */
    public static Object getAtt(ByteBuffer byteBuffer) throws ReflectiveOperationException {
        return attField.get(byteBuffer);
    }

    /**
     * 设置附加对象
     * @param byteBuffer bytebuffer 对象
     * @param attr 附加对象
     * @throws ReflectiveOperationException 反射异常
     */
    public static void setAttr(ByteBuffer byteBuffer, Object attr) throws ReflectiveOperationException {
        attField.set(byteBuffer, attr);
    }


    /**
     * 自动跟踪 GC 销毁的类
     */
    public final static class Deallocator implements Runnable {
        private long address;

        Deallocator(long address) {
            this.address = address;
        }

        public void setAddress(long address){
            this.address = address;
        }

        public long getAddress() {
            return address;
        }

        public void run() {

            if (this.address == 0) {
                return;
            }
            TUnsafe.getUnsafe().freeMemory(address);
            address = 0;
        }
    }
}
