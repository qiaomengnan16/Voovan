package org.voovan.network.udp;

import org.voovan.Global;
import org.voovan.network.ConnectModel;
import org.voovan.network.SocketContext;
import org.voovan.network.SynchronousHandler;
import org.voovan.network.exception.ReadMessageException;
import org.voovan.network.exception.SendMessageException;
import org.voovan.network.messagesplitter.TimeOutMesssageSplitter;
import org.voovan.tools.log.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

/**
 * UdpSocket 连接
 *
 * @author helyho
 *
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class UdpSocket extends SocketContext{

    private SelectorProvider provider;
    private Selector selector;
    private DatagramChannel datagramChannel;
    private UdpSession session;


    /**
     * socket 连接
     * @param host      监听地址
     * @param port		监听端口
     * @param readTimeout   超时事件
     * @throws IOException	IO异常
     */
    public UdpSocket(String host, int port, int readTimeout,ConnectModel connectModel) throws IOException{
        super(host, port, readTimeout);
        this.readTimeout = readTimeout;
        provider = SelectorProvider.provider();
        datagramChannel = provider.openDatagramChannel();
        datagramChannel.socket().setSoTimeout(this.readTimeout);
        this.connectModel = connectModel;
        if(connectModel == ConnectModel.CLIENT) {
            datagramChannel.connect(new InetSocketAddress(this.host, this.port));
        }else{
            datagramChannel.bind(new InetSocketAddress(this.host, this.port));
        }
        datagramChannel.configureBlocking(false);
        session = new UdpSession(this);
        this.handler = new SynchronousHandler();
        init();
    }

    /**
     * 构造函数
     * @param parentSocketContext 父 SocketChannel 对象
     * @param socketAddress SocketAddress 对象
     */
    protected UdpSocket(SocketContext parentSocketContext,SocketAddress socketAddress){
        try {
            provider = SelectorProvider.provider();
            this.datagramChannel = provider.openDatagramChannel();
            datagramChannel.socket().setSoTimeout(this.readTimeout);
            datagramChannel.configureBlocking(false);
            datagramChannel.connect(socketAddress);
            this.copyFrom(parentSocketContext);
            this.datagramChannel().socket().setSoTimeout(this.readTimeout);
            session = new UdpSession(this);
            connectModel = ConnectModel.SERVER;
            init();
        } catch (IOException e) {
            Logger.error("Create socket channel failed",e);
        }
    }

    /**
     * 初始化函数
     */
    private void init()  {
        try{
            selector = provider.openSelector();
            datagramChannel.register(selector, SelectionKey.OP_READ);
        }catch(IOException e){
            Logger.error("init SocketChannel failed by openSelector",e);
        }
    }


    /**
     * 获取 Session 对象
     * @return Session 对象
     */
    public UdpSession getSession(){
        return session;
    }

    public DatagramChannel datagramChannel(){
        return this.datagramChannel;
    }

    @Override
    public void start() throws IOException {
        //如果没有消息分割器默认使用读取超时时间作为分割器
        if(messageSplitter == null){
            messageSplitter = new TimeOutMesssageSplitter();
        }

        if(datagramChannel!=null && datagramChannel.isOpen()){
            UdpSelector udpSelector = new UdpSelector(selector,this);

            //循环放入独立的线程中处理
            Global.getThreadPool().execute( () -> {
                udpSelector.eventChose();
            });
        }
    }

    /**
     * 同步读取消息
     * @return 读取出的对象
     * @throws ReadMessageException 读取消息异常
     */
    public Object synchronouRead() throws ReadMessageException {
        return session.synchronouRead();
    }

    /**
     * 同步发送消息
     * @param obj  要发送的对象
     * @throws SendMessageException  消息发送异常
     */
    public void synchronouSend(Object obj) throws SendMessageException {
        session.synchronouSend(obj);
    }

    /**
     * 直接从缓冲区读取数据
     * @param data 字节缓冲对象ByteBuffer
     * @return 读取的字节数
     * */
    public int directReadBuffer(ByteBuffer data) throws IOException {
        return  session.getByteBufferChannel().read(data);
    }

    /**
     * 直接从缓冲区读取数据
     * @return 字节缓冲对象ByteBuffer
     * */
    public ByteBuffer directBufferRead() throws IOException {
        return  session.getMessageLoader().directRead();
    }

    @Override
    public boolean isConnect() {
        if(datagramChannel!=null){
            return datagramChannel.isOpen();
        }else{
            return false;
        }
    }

    @Override
    public boolean close() {
        if(datagramChannel!=null){
            try{
                //关闭直接读取模式
                session.closeDirectBufferRead();

                datagramChannel.close();
                return true;
            } catch(IOException e){
                Logger.error("Close SocketChannel failed",e);
                return false;
            }
        }else{
            return true;
        }
    }
}
