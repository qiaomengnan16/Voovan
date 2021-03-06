package org.voovan.http.client;

import org.voovan.http.HttpRequestType;
import org.voovan.http.HttpSessionParam;
import org.voovan.http.message.HttpParser;
import org.voovan.http.message.Response;
import org.voovan.http.server.HttpRequest;
import org.voovan.http.server.WebServerHandler;
import org.voovan.http.server.exception.HttpParserException;
import org.voovan.http.websocket.WebSocketFrame;
import org.voovan.network.IoFilter;
import org.voovan.network.IoSession;
import org.voovan.network.exception.IoFilterException;
import org.voovan.tools.buffer.ByteBufferChannel;
import org.voovan.tools.buffer.TByteBuffer;
import org.voovan.tools.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * HTTP 请求过滤器
 * @author helyho
 *
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class HttpClientFilter implements IoFilter {

	private HttpClient httpClient;

	public HttpClientFilter(HttpClient httpClient){
		this.httpClient = httpClient;
	}

	@Override
	public Object encode(IoSession session, Object object) {
		if(object instanceof WebSocketFrame){
			return ((WebSocketFrame)object).toByteBuffer();
		}
		if(object instanceof HttpRequest){
			HttpRequest httpRequest = (HttpRequest)object;
			try {
				httpRequest.send();
			} catch (IOException e) {
				Logger.error(e);
			}
			return TByteBuffer.EMPTY_BYTE_BUFFER;
		}
		return null;
	}

	@Override
	public Object decode(IoSession session,Object object) throws IoFilterException {
		ByteBufferChannel byteBufferChannel = session.getReadByteBufferChannel();

		try{
			if(object instanceof ByteBuffer){
				ByteBuffer byteBuffer = (ByteBuffer)object;

				if(byteBuffer.limit()==0){
					session.enabledMessageSpliter(false);
				}

				if(HttpRequestType.WEBSOCKET.equals(WebServerHandler.getAttribute(session, HttpSessionParam.TYPE))){
					return WebSocketFrame.parse((ByteBuffer)object);
				}else {
					Response response = HttpParser.parseResponse(session, byteBufferChannel, session.socketContext().getReadTimeout());
					HttpParser.resetThreadLocal();
					if(response.protocol().getStatus() == 101 &&
							response.header().get("Sec-WebSocket-Accept").equals("F2D56gI8wPj3dJw+vgY0KFJEtIM=")){

						//初始化 WebSocket
						httpClient.initWebSocket();

					}
					return response;
				}
			}
		}catch(Exception e) {
			byteBufferChannel.clear();

			if(e instanceof HttpParserException) {
				session.close();
			}

			throw new IoFilterException("HttpClientFilter decode Error. "+e.getMessage(),e);
		}finally {
			session.enabledMessageSpliter(true);
		}
		return null;
	}
}
