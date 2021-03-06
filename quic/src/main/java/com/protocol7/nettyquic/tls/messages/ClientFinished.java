package com.protocol7.nettyquic.tls.messages;

import com.protocol7.nettyquic.Writeable;
import com.protocol7.nettyquic.tls.VerifyData;
import com.protocol7.nettyquic.utils.Bytes;
import io.netty.buffer.ByteBuf;

public class ClientFinished implements Writeable {

  public static ClientFinished create(byte[] clientHandshakeTrafficSecret, byte[] finHash) {
    byte[] verifyData = VerifyData.create(clientHandshakeTrafficSecret, finHash);

    return new ClientFinished(verifyData);
  }

  public static ClientFinished parse(ByteBuf bb) {
    int type = bb.readByte();
    if (type != 0x14) {
      throw new IllegalArgumentException("Invalid type: " + type);
    }

    int len = Bytes.read24(bb);
    byte[] verifyData = new byte[len];
    bb.readBytes(verifyData);

    return new ClientFinished(verifyData);
  }

  private final byte[] verificationData;

  public ClientFinished(byte[] verificationData) {
    this.verificationData = verificationData;
  }

  public byte[] getVerificationData() {
    return verificationData;
  }

  public void write(ByteBuf bb) {
    bb.writeByte(0x14);

    Bytes.write24(bb, verificationData.length);
    bb.writeBytes(verificationData);
  }
}
