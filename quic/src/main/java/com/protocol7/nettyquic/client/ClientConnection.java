package com.protocol7.nettyquic.client;

import static com.protocol7.nettyquic.client.ClientState.Closed;
import static com.protocol7.nettyquic.client.ClientState.Closing;

import com.protocol7.nettyquic.connection.Connection;
import com.protocol7.nettyquic.connection.PacketSender;
import com.protocol7.nettyquic.protocol.*;
import com.protocol7.nettyquic.protocol.frames.Frame;
import com.protocol7.nettyquic.protocol.packets.FullPacket;
import com.protocol7.nettyquic.protocol.packets.Packet;
import com.protocol7.nettyquic.protocol.packets.ShortPacket;
import com.protocol7.nettyquic.streams.Stream;
import com.protocol7.nettyquic.streams.StreamListener;
import com.protocol7.nettyquic.streams.Streams;
import com.protocol7.nettyquic.tls.EncryptionLevel;
import com.protocol7.nettyquic.tls.aead.AEAD;
import com.protocol7.nettyquic.tls.aead.AEADs;
import com.protocol7.nettyquic.tls.aead.InitialAEAD;
import io.netty.util.concurrent.Future;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class ClientConnection implements Connection {

  private final Logger log = LoggerFactory.getLogger(ClientConnection.class);

  private ConnectionId remoteConnectionId;
  private int lastDestConnectionIdLength;
  private Optional<ConnectionId> localConnectionId = Optional.of(ConnectionId.random());
  private final PacketSender packetSender;
  private final StreamListener streamListener;

  private final AtomicReference<Version> version = new AtomicReference<>(Version.CURRENT);
  private final AtomicReference<PacketNumber> sendPacketNumber =
      new AtomicReference<>(new PacketNumber(0));
  private final PacketBuffer packetBuffer;
  private final ClientStateMachine stateMachine;
  private Optional<byte[]> token = Optional.empty();

  private final Streams streams;

  private AEADs aeads;

  public ClientConnection(
      final ConnectionId initialRemoteConnectionId,
      final StreamListener streamListener,
      final PacketSender packetSender) {
    this.remoteConnectionId = initialRemoteConnectionId;
    this.packetSender = packetSender;
    this.streamListener = streamListener;
    this.stateMachine = new ClientStateMachine(this);
    this.streams = new Streams(this);
    this.packetBuffer = new PacketBuffer(this, this::sendPacketUnbuffered, this.streams);

    initAEAD();
  }

  private void initAEAD() {
    this.aeads = new AEADs(InitialAEAD.create(remoteConnectionId, true));
  }

  public Future<Void> handshake() {
    MDC.put("actor", "client");
    return stateMachine.handshake();
  }

  public Packet sendPacket(final Packet p) {
    if (stateMachine.getState() == Closing || stateMachine.getState() == Closed) {
      throw new IllegalStateException("Connection not open");
    }
    packetBuffer.send(p);
    return p;
  }

  public FullPacket sendPacket(final Frame... frames) {
    return (FullPacket)
        sendPacket(
            new ShortPacket(
                false, getRemoteConnectionId(), nextSendPacketNumber(), new Payload(frames)));
  }

  @Override
  public Optional<ConnectionId> getLocalConnectionId() {
    return localConnectionId;
  }

  @Override
  public Optional<ConnectionId> getRemoteConnectionId() {
    return Optional.ofNullable(remoteConnectionId);
  }

  public void setRemoteConnectionId(final ConnectionId remoteConnectionId, boolean retry) {
    this.remoteConnectionId = remoteConnectionId;

    if (retry) {
      initAEAD();
    }
  }

  public Optional<byte[]> getToken() {
    return token;
  }

  public void setToken(byte[] token) {
    this.token = Optional.of(token);
  }

  public int getLastDestConnectionIdLength() {
    return lastDestConnectionIdLength;
  }

  private void sendPacketUnbuffered(final Packet packet) {
    packetSender
        .send(packet, getAEAD(EncryptionLevel.forPacket(packet)))
        .awaitUninterruptibly(); // TODO fix

    log.debug("Client sent {}", packet);
  }

  public void onPacket(final Packet packet) {
    log.debug("Client got {}", packet);

    if (packet.getDestinationConnectionId().isPresent()) {
      lastDestConnectionIdLength = packet.getDestinationConnectionId().get().getLength();
    } else {
      lastDestConnectionIdLength = 0;
    }

    EncryptionLevel encLevel = EncryptionLevel.forPacket(packet);
    if (aeads.available(encLevel)) {
      packetBuffer.onPacket(packet);

      stateMachine.handlePacket(packet);
    } else {
      // TODO handle unencryptable packet
    }
  }

  @Override
  public AEAD getAEAD(final EncryptionLevel level) {
    return aeads.get(level);
  }

  public void setHandshakeAead(final AEAD handshakeAead) {
    aeads.setHandshakeAead(handshakeAead);
  }

  public void setOneRttAead(final AEAD oneRttAead) {
    aeads.setOneRttAead(oneRttAead);
  }

  public Version getVersion() {
    return version.get();
  }

  public PacketNumber lastAckedPacketNumber() {
    return packetBuffer.getLargestAcked();
  }

  public PacketNumber nextSendPacketNumber() {
    return sendPacketNumber.updateAndGet(packetNumber -> packetNumber.next());
  }

  public void resetSendPacketNumber() {
    sendPacketNumber.set(new PacketNumber(0));
  }

  public Stream openStream() {
    return streams.openStream(true, true, streamListener);
  }

  public Stream getOrCreateStream(final StreamId streamId) {
    return streams.getOrCreate(streamId, streamListener);
  }

  public Future<Void> close() {
    stateMachine.closeImmediate();

    return packetSender.destroy();
  }

  public Future<Void> closeByPeer() {
    return packetSender.destroy();
  }

  public ClientState getState() {
    return stateMachine.getState();
  }
}
