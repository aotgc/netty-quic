package com.protocol7.nettyquic.protocol.packets;

import com.protocol7.nettyquic.protocol.ConnectionId;
import com.protocol7.nettyquic.protocol.Version;
import com.protocol7.nettyquic.tls.aead.AEADProvider;
import java.util.Optional;

public interface HalfParsedPacket<P extends Packet> {

  Optional<Version> getVersion();

  Optional<ConnectionId> getConnectionId();

  P complete(AEADProvider aeadProvider);
}
