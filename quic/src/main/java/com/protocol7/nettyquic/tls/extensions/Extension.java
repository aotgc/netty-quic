package com.protocol7.nettyquic.tls.extensions;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface Extension {

  static List<Extension> parseAll(ByteBuf bb, boolean isClient) {
    List<Extension> extensions = new ArrayList<>();

    while (bb.isReadable()) {
      Extension ext = Extension.parse(bb, isClient);

      extensions.add(ext);
    }

    return extensions;
  }

  static Extension parse(ByteBuf bb, boolean isClient) {
    ExtensionType type = ExtensionType.fromValue(bb.readShort() & 0xFFFF);

    int len = bb.readShort();
    final ByteBuf b = bb.readBytes(len);
    try {
      if (type == ExtensionType.QUIC) {
        return TransportParameters.parse(b, isClient);
      } else if (type == ExtensionType.key_share) {
        return KeyShare.parse(b, isClient);
      } else if (type == ExtensionType.supported_versions) {
        return SupportedVersions.parse(b, isClient);
      } else if (type == ExtensionType.supported_groups) {
        return SupportedGroups.parse(b);
      } else {
        return RawExtension.parse(type, b);
      }
    } finally {
      b.release();
    }
  }

  static void writeAll(Collection<Extension> extensions, ByteBuf bb, boolean isClient) {
    for (Extension extension : extensions) {
      bb.writeShort(extension.getType().getValue());

      int lenPos = bb.writerIndex();
      bb.writeShort(0);

      extension.write(bb, isClient);

      bb.setShort(lenPos, bb.writerIndex() - lenPos - 2);
    }
  }

  ExtensionType getType();

  void write(ByteBuf bb, boolean isClient);
}
