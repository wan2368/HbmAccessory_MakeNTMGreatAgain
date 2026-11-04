package com.makentmgreatagain.util;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;

/**
 * netty ByteBuf 的字符串读写。
 *
 * 1.7.10 带的 netty 版本里 ByteBuf 没有 writeUTF/readUTF（那是后来的 API），
 * 这里用长度前缀 + UTF-8 字节自己拼一份，双端一致即可。
 */
public final class ByteBufUtil {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ByteBufUtil() { }

    public static void writeString(ByteBuf buf, String s) {

        byte[] data = s.getBytes(UTF8);

        buf.writeShort(data.length);
        buf.writeBytes(data);
    }

    public static String readString(ByteBuf buf) {

        int len = buf.readShort();
        if (len < 0 || len > buf.readableBytes()) return "";

        byte[] data = new byte[len];
        buf.readBytes(data);

        return new String(data, UTF8);
    }
}
