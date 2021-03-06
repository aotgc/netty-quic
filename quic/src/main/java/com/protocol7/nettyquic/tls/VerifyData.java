package com.protocol7.nettyquic.tls;

import static com.protocol7.nettyquic.tls.aead.Labels.FINISHED;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;

public class VerifyData {

  public static byte[] create(byte[] handshakeTrafficSecret, byte[] finishedHash) {
    Preconditions.checkArgument(handshakeTrafficSecret.length == 32);
    Preconditions.checkArgument(finishedHash.length == 32);

    // finished_key = HKDF-Expand-Label(
    //    key = client_handshake_traffic_secret,
    //    label = "finished",
    //    context = "",
    //    len = 32)
    byte[] finishedKey = HKDF.expandLabel(handshakeTrafficSecret, FINISHED, new byte[0], 32);

    // verify_data = HMAC-SHA256(
    //	key = finished_key,
    //	msg = finished_hash)
    return Hashing.hmacSha256(finishedKey).hashBytes(finishedHash).asBytes();
  }

  public static boolean verify(
      byte[] verifyData, byte[] handshakeTrafficSecret, byte[] finishedHash, boolean quic) {
    Preconditions.checkArgument(verifyData.length > 0);
    Preconditions.checkArgument(handshakeTrafficSecret.length == 32);
    Preconditions.checkArgument(finishedHash.length == 32);

    byte[] actual = create(handshakeTrafficSecret, finishedHash);

    return CryptoEquals.isEqual(verifyData, actual);
  }
}
