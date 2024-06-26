package work.art1st.proxiedproxy.platform.velocity.connection;

import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.HandshakePacket;
import com.velocitypowered.proxy.protocol.packet.StatusRequestPacket;
import com.velocitypowered.proxy.protocol.packet.StatusResponsePacket;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/* Originally com.velocitypowered.proxy.server.PingSessionHandler */
public class VPingSessionHandler implements MinecraftSessionHandler {
    private final CompletableFuture<ServerPing> result;
    private final RegisteredServer server;
    private final MinecraftConnection connection;
    private final ProtocolVersion version;
    private final String vHost;
    private boolean completed = false;

    /* Use vHost here */
    VPingSessionHandler(CompletableFuture<ServerPing> result, RegisteredServer server, MinecraftConnection connection, ProtocolVersion version, String vHost) {
        this.result = result;
        this.server = server;
        this.connection = connection;
        this.version = version;
        this.vHost = vHost;
    }

    public void activated() {
        HandshakePacket handshake = new HandshakePacket();
        handshake.setIntent(HandshakeIntent.STATUS);
        handshake.setServerAddress(vHost);
        handshake.setPort(this.server.getServerInfo().getAddress().getPort());
        handshake.setProtocolVersion(this.version);
        this.connection.delayedWrite(handshake);
        this.connection.setState(StateRegistry.STATUS);
        this.connection.delayedWrite(StatusRequestPacket.INSTANCE);
        this.connection.flush();
    }

    public boolean handle(StatusResponsePacket packet) {
        this.completed = true;
        this.connection.close(true);
        ServerPing ping = VelocityServer.getPingGsonInstance(this.version).fromJson(packet.getStatus(), ServerPing.class);
        this.result.complete(ping);
        return true;
    }

    public void disconnected() {
        if (!this.completed) {
            this.result.completeExceptionally(new IOException("Unexpectedly disconnected from remote server"));
        }

    }

    public void exception(Throwable throwable) {
        this.completed = true;
        this.result.completeExceptionally(throwable);
    }

}
