package com.protocol7.nettyquic.tls.aead;

import static com.protocol7.nettyquic.tls.aead.Labels.*;

import com.protocol7.nettyquic.tls.HKDF;

public class OneRttAEAD {

  private static final byte[] ZEROS = new byte[32];
  private static final byte[] EMPTY = new byte[0];

  public static AEAD create(byte[] handshakeSecret, byte[] handshakeHash, boolean isClient) {

    // derived_secret = HKDF-Expand-Label(
    //                key = handshake_secret,
    //                label = "derived",
    //                context = empty_hash,
    //                len = 32)
    byte[] derivedSecret = HKDF.expandLabel(handshakeSecret, DERIVED, HKDF.EMPTY_HASH, 32);

    //        master_secret = HKDF-Extract(
    //                salt=derived_secret,
    //                key=00...)
    byte[] masterSecret = HKDF.hkdf.extract(derivedSecret, ZEROS);

    // client_application_traffic_secret = HKDF-Expand-Label(
    //    key = master_secret,
    //    label = "c ap traffic",
    //    context = handshake_hash,
    //    len = 32)
    byte[] clientApplicationTrafficSecret =
        HKDF.expandLabel(masterSecret, CLIENT_APPLICATION_TRAFFIC_SECRET, handshakeHash, 32);

    // server_application_traffic_secret = HKDF-Expand-Label(
    //    key = master_secret,
    //    label = "s ap traffic",
    //    context = handshake_hash,
    //    len = 32)
    byte[] serverApplicationTrafficSecret =
        HKDF.expandLabel(masterSecret, SERVER_APPLICATION_TRAFFIC_SECRET, handshakeHash, 32);

    // client_application_key = HKDF-Expand-Label(
    //    key = client_application_traffic_secret,
    //    label = "key",
    //    context = "",
    //    len = 16)
    byte[] clientApplicationKey = HKDF.expandLabel(clientApplicationTrafficSecret, KEY, EMPTY, 16);

    // server_application_key = HKDF-Expand-Label(
    //    key = server_application_traffic_secret,
    //    label = "key",
    //    context = "",
    //    len = 16)
    byte[] serverApplicationKey = HKDF.expandLabel(serverApplicationTrafficSecret, KEY, EMPTY, 16);

    // client_application_iv = HKDF-Expand-Label(
    //    key = client_application_traffic_secret,
    //    label = "iv",
    //    context = "",
    //    len = 12)
    byte[] clientApplicationIV = HKDF.expandLabel(clientApplicationTrafficSecret, IV, EMPTY, 12);

    // server_application_iv = HKDF-Expand-Label(
    //    key = server_application_traffic_secret,
    //    label = "iv",
    //    context = "",
    //    len = 12)
    byte[] serverApplicationIV = HKDF.expandLabel(serverApplicationTrafficSecret, IV, EMPTY, 12);

    byte[] clientPnKey = HKDF.expandLabel(clientApplicationTrafficSecret, HP_KEY, EMPTY, 16);
    byte[] serverPnKey = HKDF.expandLabel(serverApplicationTrafficSecret, HP_KEY, EMPTY, 16);

    if (isClient) {
      return new AEAD(
          clientApplicationKey,
          serverApplicationKey,
          clientApplicationIV,
          serverApplicationIV,
          clientPnKey,
          serverPnKey);
    } else {
      return new AEAD(
          serverApplicationKey,
          clientApplicationKey,
          serverApplicationIV,
          clientApplicationIV,
          serverPnKey,
          clientPnKey);
    }
  }
}
