package work.art1st.proxiedproxy.platform.bungeecord.connection;

import lombok.SneakyThrows;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.event.AsyncEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.netty.ChannelWrapper;
import work.art1st.proxiedproxy.platform.common.connection.PLoginInboundConnection;
import work.art1st.proxiedproxy.platform.common.util.PluginChannel;
import work.art1st.proxiedproxy.util.ReflectUtil;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

public final class BLoginInboundConnection implements PLoginInboundConnection {

    private final InitialHandler handler;
    private final ChannelWrapper ch;
    private final Callback<PreLoginEvent> origEventCallBack;
    private PreLoginEvent cb_args_result;
    private Throwable cb_args_error;
    @SneakyThrows
    public BLoginInboundConnection(PreLoginEvent preLoginEvent) {
        this.handler = (InitialHandler) preLoginEvent.getConnection();
        this.ch = ReflectUtil.getDeclaredFieldValue(handler, "ch");
        Field doneField = AsyncEvent.class.getDeclaredField("done");
        ReflectUtil.handleAccessible(doneField);
        origEventCallBack = (Callback<PreLoginEvent>) doneField.get(preLoginEvent);
        Callback<PreLoginEvent> modifiedCallback = (result, error) -> {
            cb_args_result = result;
            cb_args_error = error;
        };
        doneField.set(preLoginEvent, modifiedCallback);
    }

    @Override
    public void sendLoginPluginMessage(String contents) {
        LoginPayloadResponseHandler payloadResponseHandler = new LoginPayloadResponseHandler(this, ch);
        payloadResponseHandler.sendLoginPayloadRequest(PluginChannel.CHANNEL_ID, PluginChannel.FORWARDING_REQUEST);
    }

    @Override
    public boolean isDirectConnection() {
        return false;
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

    public void preLoginEventCallback() {
        origEventCallBack.done(cb_args_result, cb_args_error);
    }
}
