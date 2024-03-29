package work.art1st.proxiedproxy.platform.bungeecord.connection;

import lombok.SneakyThrows;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.event.AsyncEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.netty.ChannelWrapper;
import work.art1st.proxiedproxy.platform.bungeecord.packet.HandshakePacket;
import work.art1st.proxiedproxy.platform.common.connection.PLoginInboundConnection;
import work.art1st.proxiedproxy.platform.common.util.PluginChannel;
import work.art1st.proxiedproxy.util.ReflectUtil;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

public final class BLoginInboundConnection implements PLoginInboundConnection {

    private final InitialHandler handler;
    private final ChannelWrapper ch;
    private final PreLoginEvent event;
    private Callback<PreLoginEvent> origEventCallBack;
    private PreLoginEvent cb_args_result;
    private Throwable cb_args_error;
    private final boolean isDirectConnection;

    /* Code from Velocity. */
    static String cleanVhost(String hostname) {
        // Clean out any anything after any zero bytes (this includes BungeeCord forwarding and the
        // legacy Forge handshake indicator).
        String cleaned = hostname;
        int zeroIdx = cleaned.indexOf('\0');
        if (zeroIdx > -1) {
            cleaned = hostname.substring(0, zeroIdx);
        }

        // If we connect through an SRV record, there will be a period at the end (DNS usually elides
        // this ending octet).
        if (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) == '.') {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }


    @SneakyThrows
    public BLoginInboundConnection(PreLoginEvent preLoginEvent) {
        this.handler = (InitialHandler) preLoginEvent.getConnection();
        this.event = preLoginEvent;
        this.ch = ReflectUtil.getDeclaredFieldValue(handler, "ch");
        String origAddress = ((HandshakePacket) handler.getHandshake()).getOriginalHostAddress();
        this.isDirectConnection = isVHostFromClient(cleanVhost(origAddress), origAddress);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @Override
    public void sendLoginPluginMessage(String contents) {
        Field doneField = AsyncEvent.class.getDeclaredField("done");
        ReflectUtil.handleAccessible(doneField);
        origEventCallBack = (Callback<PreLoginEvent>) doneField.get(event);
        Callback<PreLoginEvent> modifiedCallback = (result, error) -> {
            cb_args_result = result;
            cb_args_error = error;
        };
        doneField.set(event, modifiedCallback);
        LoginPayloadResponseHandler payloadResponseHandler = new LoginPayloadResponseHandler(this, ch);
        payloadResponseHandler.sendLoginPayloadRequest(PluginChannel.CHANNEL_ID, PluginChannel.FORWARDING_REQUEST);
    }

    @Override
    public boolean isDirectConnection() {
        return isDirectConnection;
    }

    @SneakyThrows
    @Override
    public void setRemoteAddress(String remoteAddress) {
        ReflectUtil.setDeclaredFieldValue(ch, "remoteAddress", new InetSocketAddress(remoteAddress, 0));
    }

    @Override
    public void disconnect(Component reason) {
        handler.disconnect(BungeeComponentSerializer.get().serialize(reason));
    }

    public InitialHandler getHandler() {
        return handler;
    }

    public void donePreLoginEvent() {
        origEventCallBack.done(cb_args_result, cb_args_error);
    }
}
