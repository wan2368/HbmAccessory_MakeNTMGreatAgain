package com.makentmgreatagain.packet;

import com.makentmgreatagain.client.AchClientPacketHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 成就同步包（S → C）。
 *
 * 两种载荷共用一个类，用 mode 区分：
 *  - MODE_FULL：登录时推整份进度，客户端直接覆盖本地位图
 *  - MODE_ONE ：刚解锁一条，客户端写入本地并弹提示
 *
 * 客户端处理逻辑刻意放到 AchClientPacketHandler（整类 @SideOnly），
 * 这样专用服务端不会因为引用 Minecraft 类而崩。
 */
public class AchievementPacket implements IMessage {

    public static final byte MODE_FULL = 0;
    public static final byte MODE_ONE = 1;

    public byte mode;
    public String id;
    public boolean[] snapshot;

    public AchievementPacket() { }

    /** 单条解锁 */
    public AchievementPacket(String id) {
        this.mode = MODE_ONE;
        this.id = id;
    }

    /** 全量同步 */
    public AchievementPacket(boolean[] snapshot) {
        this.mode = MODE_FULL;
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {

        this.mode = buf.readByte();

        if (mode == MODE_ONE) {
            this.id = com.makentmgreatagain.util.ByteBufUtil.readString(buf);
        } else {
            int len = buf.readInt();
            this.snapshot = new boolean[len];
            for (int i = 0; i < len; i++) this.snapshot[i] = buf.readBoolean();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {

        buf.writeByte(mode);

        if (mode == MODE_ONE) {
            com.makentmgreatagain.util.ByteBufUtil.writeString(buf, id == null ? "" : id);
        } else {
            int len = snapshot == null ? 0 : snapshot.length;
            buf.writeInt(len);
            for (int i = 0; i < len; i++) buf.writeBoolean(snapshot[i]);
        }
    }

    public static class Handler implements IMessageHandler<AchievementPacket, IMessage> {

        @Override
        public IMessage onMessage(AchievementPacket msg, MessageContext ctx) {

            if (ctx.side == Side.CLIENT) {
                AchClientPacketHandler.handle(msg);
            }

            return null;
        }
    }
}
