package com.albert.net.remote.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RemotingMessage {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemotingMessage.class);
    /* 请求id生成 */
    private static final AtomicInteger idGenerator = new AtomicInteger(0);
    private static final SerialType DEFAULT_TYPE = SerialType.CUSTOME;
    /* 消息包类型 */
    private static final int REQUEST_OR_RESPONSE = 0;   // flag 第0bit表示；0为请求，1为响应
    private static final int ONE_WAY = 1;   // flag中所在第几位 单向发送，不需要回应

    private static final int SERIAL_TYPE_BIT = 30;      // headerLen 最高位(除第32位符号位)不用

    /* 业务码 */
    private int code;
    /* 版本号 */
    private int version = 0;
    /* 请求id */
    private int requestId = idGenerator.getAndIncrement();
    /* 请求标识 */
    private int flag;
    /* 请求可以携带消息 */
    private String info;
    private transient byte[] body;

    private Map<String, String> extFields;          //拓展信息

    private SerialType serialType = SerialType.CUSTOME;


    public static RemotingMessage createRequest(int code) {
        RemotingMessage msg = new RemotingMessage();
        msg.setCode(code);
        return msg;
    }

    public static RemotingMessage createResponse(int code, String info) {
        return createResponse(code, info, null);
    }
    public static RemotingMessage createResponse(int code, String info,
                                                 Map<String, String> extFields) {
        RemotingMessage msg = new RemotingMessage();
        msg.markResponseType();
        msg.setInfo(info);
        msg.setCode(code);
        if(extFields != null) {
            msg.setExtFields(extFields);
        }
        return msg;
    }


    public static byte[] encode(RemotingMessage remotingMessage) {
        int len = 4;            // The Total Length

        byte[] header = encodeHeader(remotingMessage);

        len += 4;           // header length
        len += header != null ? header.length : 0;

        if(remotingMessage.getBody() != null) {
            len += remotingMessage.getBody().length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(len);

        buffer.putInt(len);
        /* header 单独编码，header头含有单独的headerLen */
        if(header != null) {
            buffer.putInt(markSerialType(header.length, remotingMessage.getSerialType()));
            buffer.put(header);
        }

        if(remotingMessage.getBody() != null) {
            buffer.put(remotingMessage.getBody());
        }
        return buffer.array();
    }

    private static byte[] encodeHeader(RemotingMessage remotingMessage) {
        if(remotingMessage.getSerialType() == SerialType.CUSTOME) {
            return CustomHeaderSerializer.encodeHeader(remotingMessage);
        } else if(remotingMessage.getSerialType() == SerialType.JSON) {
            return JSONSerializer.encode(remotingMessage);
        }
        return null;
    }
    private static RemotingMessage decodeHeader(ByteBuf buf, int len, SerialType serialType) {
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        switch (serialType) {
            case CUSTOME:
                return CustomHeaderSerializer.decodeCustomHeader(bytes);
            case JSON:
                return JSONSerializer.decode(bytes, RemotingMessage.class);
            default:
                break;
        }
        return null;
    }


    public static RemotingMessage decode(final byte[] bytes) {
        return decode(ByteBuffer.wrap(bytes));
    }

    public static RemotingMessage decode(final ByteBuffer buffer) {
        return decode(Unpooled.wrappedBuffer(buffer));
    }
    public static RemotingMessage decode(final ByteBuf buf) {
        int totalLen = buf.readInt();
        int headerLen = buf.readInt();

        RemotingMessage header = decodeHeader(buf, getTrueLength(headerLen), getTypeFromHeaderLength(headerLen));
        int bodyLen = totalLen - 4 - 4 - getTrueLength(headerLen);
        if(bodyLen > 0) {
            byte[] bodyBytes = new byte[bodyLen];
            buf.readBytes(bodyBytes);
            header.setBody(bodyBytes);
        }
        return header;
    }

    private static int markSerialType(int source, SerialType serialType) {
        if(serialType == SerialType.CUSTOME) {
            int bits = 1 << SERIAL_TYPE_BIT;
            source |= bits;
        }
        return source;
    }
    private static int getTrueLength(int headerLen) {
        int bits = 1 << SERIAL_TYPE_BIT;
        headerLen = headerLen & (~bits);              // 将该bit置0
        return headerLen;
    }
    private static SerialType getTypeFromHeaderLength(int source) {
        int bits = 1 << SERIAL_TYPE_BIT;
        int isOne = ((source & bits) == bits)? 1 : 0;
        return SerialType.fromKey((byte) isOne);
    }



    //============= instance method =================//
    public void markResponseType() {
        int bits = 1 << REQUEST_OR_RESPONSE;
        this.flag |= bits;
    }

    public void markOneWayType() {
        int bits = 1 << ONE_WAY;
        this.flag |= bits;
    }


    public boolean isResponseType() {
        int bits = 1 << REQUEST_OR_RESPONSE;
        return ((flag & bits) == bits);
    }
    public MessageType getType() {
        if(isResponseType()) {
            return MessageType.RESPONSE;
        }
        return MessageType.REQUEST;
    }


    public boolean isOneWayType() {
        int bits = 1 << ONE_WAY;
        return ((flag & bits) == bits);
    }

    public int getCode() {
        return code;
    }

    public int getRequestId() {
        return requestId;
    }

    public int getFlag() {
        return flag;
    }

    public byte[] getBody() {
        return body;
    }

    public Map<String, String> getExtFields() {
        return extFields;
    }

    public SerialType getSerialType() {
        return serialType;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public void setExtFields(Map<String, String> extFields) {
        this.extFields = extFields;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setSerialType(SerialType serialType) {
        this.serialType = serialType;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    @Override
    public String toString() {
        return "RemotingMessage{" +
                "code=" + code +
                ", version=" + version +
                ", requestId=" + requestId +
                ", flag=" + flag +
                ", info='" + info + '\'' +
                '}';
    }
}
