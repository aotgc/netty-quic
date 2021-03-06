package com.protocol7.nettyquic.protocol.packets;

import com.protocol7.nettyquic.protocol.ConnectionId;
import com.protocol7.nettyquic.protocol.Version;
import com.protocol7.nettyquic.tls.aead.AEAD;
import io.netty.buffer.ByteBuf;
import java.util.Optional;

public interface Packet {

  int PACKET_TYPE_MASK = 0b10000000;

  static boolean isLongHeader(int b) {
    return (PACKET_TYPE_MASK & b) == PACKET_TYPE_MASK;
  }

  static HalfParsedPacket parse(ByteBuf bb, int connidLength) {
    bb.markReaderIndex();
    int firstByte = bb.readByte() & 0xFF;

    if (isLongHeader(firstByte)) {
      // Long header packet

      int packetType = (firstByte & 0x30) >> 4;

      // might be a ver neg packet, so we must check the version
      Version version = Version.read(bb);
      bb.resetReaderIndex();

      if (version == Version.VERSION_NEGOTIATION) {
        return VersionNegotiationPacket.parse(bb);
      } else if (packetType == PacketType.Initial.getType()) {
        return InitialPacket.parse(bb);
      } else if (packetType == PacketType.Handshake.getType()) {
        return HandshakePacket.parse(bb);
      } else if (packetType == PacketType.Retry.getType()) {
        return RetryPacket.parse(bb);
      } else {
        throw new RuntimeException("Unknown long header packet");
      }
    } else {
      // short header packet
      bb.resetReaderIndex();
      return ShortPacket.parse(bb, connidLength);
    }
  }

  void write(ByteBuf bb, AEAD aead);

  Optional<ConnectionId> getSourceConnectionId();

  Optional<ConnectionId> getDestinationConnectionId();
}
