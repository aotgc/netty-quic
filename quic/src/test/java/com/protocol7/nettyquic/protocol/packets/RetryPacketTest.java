package com.protocol7.nettyquic.protocol.packets;

import static java.util.Optional.of;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.protocol7.nettyquic.protocol.ConnectionId;
import com.protocol7.nettyquic.protocol.Version;
import com.protocol7.nettyquic.tls.aead.AEAD;
import com.protocol7.nettyquic.tls.aead.InitialAEAD;
import com.protocol7.nettyquic.utils.Rnd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

public class RetryPacketTest {

  private ConnectionId dest = ConnectionId.random();
  private ConnectionId src = ConnectionId.random();
  private ConnectionId org = ConnectionId.random();
  private byte[] token = Rnd.rndBytes(18);
  private RetryPacket packet = new RetryPacket(Version.DRAFT_15, of(dest), of(src), org, token);

  private final AEAD aead = InitialAEAD.create(ConnectionId.random(), true);

  @Test
  public void roundtrip() {
    ByteBuf bb = Unpooled.buffer();
    packet.write(bb, aead);

    RetryPacket parsed = RetryPacket.parse(bb).complete(l -> aead);

    assertEquals(dest, parsed.getDestinationConnectionId().get());
    assertEquals(src, parsed.getSourceConnectionId().get());
    assertEquals(org, parsed.getOriginalConnectionId());
    assertArrayEquals(token, parsed.getRetryToken());
  }
}
