package com.protocol7.nettyquic.protocol.frames;

import static org.junit.Assert.assertEquals;

import com.protocol7.nettyquic.utils.Bytes;
import com.protocol7.nettyquic.utils.Hex;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.Test;

public class AckFrameTest {

  @Test
  public void roundtrip() {
    List<AckBlock> blocks =
        List.of(AckBlock.fromLongs(1, 5), AckBlock.fromLongs(7, 8), AckBlock.fromLongs(12, 100));
    AckFrame frame = new AckFrame(1234, blocks);

    ByteBuf bb = Unpooled.buffer();
    frame.write(bb);

    AckFrame parsed = AckFrame.parse(bb);

    assertEquals(frame.getAckDelay(), parsed.getAckDelay());
    assertEquals(frame.getBlocks(), parsed.getBlocks());
  }

  @Test
  public void roundtripSinglePacket() {
    List<AckBlock> blocks = List.of(AckBlock.fromLongs(100, 100));
    AckFrame frame = new AckFrame(1234, blocks);

    ByteBuf bb = Unpooled.buffer();
    frame.write(bb);

    AckFrame parsed = AckFrame.parse(bb);

    assertEquals(frame.getAckDelay(), parsed.getAckDelay());
    assertEquals(frame.getBlocks(), parsed.getBlocks());
  }

  @Test
  public void writeSinglePacket() {
    List<AckBlock> blocks = List.of(AckBlock.fromLongs(100, 100));
    AckFrame frame = new AckFrame(1234, blocks);

    ByteBuf bb = Unpooled.buffer();
    frame.write(bb);

    assertEquals("02406444d20000", Hex.hex(Bytes.drainToArray(bb)));
  }
}
