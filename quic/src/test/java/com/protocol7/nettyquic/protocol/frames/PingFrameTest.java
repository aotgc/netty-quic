package com.protocol7.nettyquic.protocol.frames;

import static org.junit.Assert.assertEquals;

import com.protocol7.nettyquic.TestUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

public class PingFrameTest {

  public static final byte[] DATA = "hello".getBytes();

  @Test
  public void roundtrip() {
    ByteBuf bb = Unpooled.buffer();
    PingFrame frame = PingFrame.INSTANCE;

    frame.write(bb);

    PingFrame parsed = PingFrame.parse(bb);

    assertEquals(frame, parsed);
  }

  @Test
  public void write() {
    ByteBuf bb = Unpooled.buffer();
    PingFrame frame = PingFrame.INSTANCE;
    frame.write(bb);

    TestUtil.assertBuffer("01", bb);
  }
}
