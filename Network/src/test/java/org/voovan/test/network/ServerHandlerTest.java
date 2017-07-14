package org.voovan.test.network;


import org.voovan.network.ConnectModel;
import org.voovan.network.HeartBeat;
import org.voovan.network.IoHandler;
import org.voovan.network.IoSession;
import org.voovan.tools.ByteBufferChannel;
import org.voovan.tools.TByteBuffer;
import org.voovan.tools.TEnv;
import org.voovan.tools.log.Logger;

import java.nio.ByteBuffer;

public class ServerHandlerTest implements IoHandler {

	@Override
	public Object onConnect(IoSession session) {
		Logger.simple("onConnect");
		return null;
	}

	@Override
	public void onDisconnect(IoSession session) {
		Logger.simple("onDisconnect");
	}

	@Override
	public Object onReceive(IoSession session, Object obj) {
		Logger.simple(session.remoteAddress()+":"+session.remotePort());
        Logger.simple("Server onRecive: "+obj.toString());
        return "==== "+obj.toString().trim()+" ===== \r\n";
    }

    @Override
    public void onException(IoSession session, Exception e) {
		e.printStackTrace();
        Logger.error("Server Exception",e);
        session.close();
	}

	private HeartBeat heartBeat;
	@Override
	public void onIdle(IoSession session) {
		Logger.info("idle");
		HeartBeat.attachSession(session, ConnectModel.SERVER, 2, "PINGq", "PONGq");
		heartBeat.start(session);
	}

	@Override
	public void onSent(IoSession session, Object obj) {
		String sad = (String)obj;
		Logger.simple("Server onSent: " + sad);
		//jmeter 测试是需要打开,和客户端测试时关闭
//		session.close();
	}

}
