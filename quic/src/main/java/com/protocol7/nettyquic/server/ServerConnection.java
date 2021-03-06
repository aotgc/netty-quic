package com.protocol7.nettyquic.server;

import static com.protocol7.nettyquic.tls.EncryptionLevel.Initial;

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
import java.security.PrivateKey;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerConnection implements Connection {

  private final Logger log = LoggerFactory.getLogger(ServerConnection.class);

  private Optional<ConnectionId> remoteConnectionId = Optional.empty();
  private Optional<ConnectionId> localConnectionId;
  private final StreamListener handler;
  private final PacketSender packetSender;
  private final AtomicReference<Version> version = new AtomicReference<>(Version.CURRENT);
  private final AtomicReference<PacketNumber> sendPacketNumber =
      new AtomicReference<>(PacketNumber.MIN);
  private final Streams streams;
  private final ServerStateMachine stateMachine;
  private final PacketBuffer packetBuffer;

  private AEADs aeads;

  public ServerConnection(
      final ConnectionId localConnectionId,
      final StreamListener handler,
      final PacketSender packetSender,
      final List<byte[]> certificates,
      final PrivateKey privateKey) {
    this.handler = handler;
    this.packetSender = packetSender;
    this.stateMachine = new ServerStateMachine(this, certificates, privateKey);
    this.streams = new Streams(this);
    this.packetBuffer = new PacketBuffer(this, this::sendPacketUnbuffered, this.streams);

    this.localConnectionId = Optional.of(localConnectionId);

    initAEAD();
  }

  private void initAEAD() {
    this.aeads = new AEADs(InitialAEAD.create(localConnectionId.get(), false));
  }

  public Optional<ConnectionId> getRemoteConnectionId() {
    return remoteConnectionId;
  }

  public Optional<ConnectionId> getLocalConnectionId() {
    return localConnectionId;
  }

  public void setRemoteConnectionId(ConnectionId remoteConnectionId) {
    this.remoteConnectionId = Optional.of(remoteConnectionId);
  }

  public void setLocalConnectionId(ConnectionId localConnectionId) {
    this.localConnectionId = Optional.of(localConnectionId);

    initAEAD();
  }

  public Version getVersion() {
    return version.get();
  }

  public Packet sendPacket(Packet p) {
    packetBuffer.send(p);
    return p;
  }

  public FullPacket sendPacket(Frame... frames) {
    return (FullPacket)
        sendPacket(
            new ShortPacket(
                false, getRemoteConnectionId(), nextSendPacketNumber(), new Payload(frames)));
  }

  private void sendPacketUnbuffered(Packet packet) {
    packetSender.send(packet, getAEAD(Initial)).awaitUninterruptibly(); // TODO fix
    log.debug("Server sent {}", packet);
  }

  public void onPacket(Packet packet) {
    log.debug("Server got {}", packet);

    if (stateMachine.getState() != ServerState.BeforeInitial) {
      packetBuffer.onPacket(packet);
    }
    // with incorrect conn ID
    stateMachine.processPacket(packet);
  }

  @Override
  public AEAD getAEAD(EncryptionLevel level) {
    return aeads.get(level);
  }

  @Override
  public Optional<byte[]> getToken() {
    return Optional.empty();
  }

  public void setHandshakeAead(AEAD handshakeAead) {
    aeads.setHandshakeAead(handshakeAead);
  }

  public void setOneRttAead(AEAD oneRttAead) {
    aeads.setOneRttAead(oneRttAead);
  }

  public Stream getOrCreateStream(StreamId streamId) {
    return streams.getOrCreate(streamId, handler);
  }

  public PacketNumber lastAckedPacketNumber() {
    return packetBuffer.getLargestAcked();
  }

  public PacketNumber nextSendPacketNumber() {
    return sendPacketNumber.updateAndGet(packetNumber -> packetNumber.next());
  }

  public ServerState getState() {
    return stateMachine.getState();
  }

  public Future<Void> close() {
    stateMachine.closeImmediate();

    return packetSender.destroy();
  }

  public Future<Void> closeByPeer() {
    return packetSender.destroy();
  }
}
