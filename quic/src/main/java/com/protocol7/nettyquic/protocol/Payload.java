package com.protocol7.nettyquic.protocol;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.protocol7.nettyquic.protocol.frames.Frame;
import com.protocol7.nettyquic.tls.aead.AEAD;
import com.protocol7.nettyquic.utils.Bytes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class Payload {

  public static Payload parse(
      final ByteBuf bb,
      final int length,
      final AEAD aead,
      final PacketNumber pn,
      final byte[] aad) {
    final byte[] cipherText = new byte[length];
    bb.readBytes(cipherText);

    final byte[] raw;
    try {
      raw = aead.open(cipherText, pn.asLong(), aad);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }

    final List<Frame> frames = new ArrayList<>();
    final ByteBuf frameBuf = Unpooled.wrappedBuffer(raw);

    while (frameBuf.isReadable()) {
      final Frame frame = Frame.parse(frameBuf);
      // TODO ignore padding frames?
      frames.add(frame);
    }
    return new Payload(frames);
  }

  public Payload addFrame(final Frame frame) {
    List<Frame> newFrames = new ArrayList<>(frames);
    newFrames.add(frame);
    return new Payload(newFrames);
  }

  private final List<Frame> frames;

  public Payload(final List<Frame> frames) {
    requireNonNull(frames);
    checkArgument(!frames.isEmpty());

    this.frames = List.copyOf(frames);
  }

  public Payload(final Frame... frames) {
    this(List.of(frames));
  }

  public List<Frame> getFrames() {
    return frames;
  }

  public int calculateLength() {
    return frames.stream().mapToInt(f -> f.calculateLength()).sum() + AEAD.OVERHEAD;
  }

  public void write(final ByteBuf bb, final AEAD aead, final PacketNumber pn, final byte[] aad) {
    final ByteBuf raw = Unpooled.buffer();
    for (final Frame frame : frames) {
      frame.write(raw);
    }
    final byte[] b = Bytes.drainToArray(raw);

    try {
      final byte[] sealed = aead.seal(b, pn.asLong(), aad);
      bb.writeBytes(sealed);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final Payload payload = (Payload) o;

    return frames != null ? frames.equals(payload.frames) : payload.frames == null;
  }

  @Override
  public int hashCode() {
    return frames != null ? frames.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "Payload{" + frames + '}';
  }
}
