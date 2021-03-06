package com.protocol7.nettyquic.client;

import com.google.common.annotations.VisibleForTesting;
import com.protocol7.nettyquic.protocol.TransportError;
import com.protocol7.nettyquic.protocol.Version;
import com.protocol7.nettyquic.protocol.frames.*;
import com.protocol7.nettyquic.protocol.packets.*;
import com.protocol7.nettyquic.streams.Stream;
import com.protocol7.nettyquic.tls.ClientTlsSession;
import com.protocol7.nettyquic.tls.aead.AEAD;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientStateMachine {

  private final Logger log = LoggerFactory.getLogger(ClientStateMachine.class);

  private ClientState state = ClientState.BeforeInitial;
  private final ClientConnection connection;
  private final DefaultPromise<Void> handshakeFuture =
      new DefaultPromise(GlobalEventExecutor.INSTANCE); // TODO use what event executor?
  private final ClientTlsSession tlsEngine = new ClientTlsSession();

  public ClientStateMachine(final ClientConnection connection) {
    this.connection = connection;
  }

  public Future<Void> handshake() {
    synchronized (this) {
      // send initial packet
      if (state == ClientState.BeforeInitial) {

        sendInitialPacket();
        state = ClientState.WaitingForServerHello;
        log.info("Client connection state initial sent");
      } else {
        throw new IllegalStateException("Can't handshake in state " + state);
      }
    }
    return handshakeFuture;
  }

  private void sendInitialPacket() {
    final List<Frame> frames = new ArrayList<>();

    int len = 1200;

    final CryptoFrame clientHello = new CryptoFrame(0, tlsEngine.startHandshake());
    len -= clientHello.calculateLength();
    frames.add(clientHello);
    frames.add(new PaddingFrame(len));

    connection.sendPacket(
        InitialPacket.create(
            connection.getRemoteConnectionId(),
            connection.getLocalConnectionId(),
            connection.nextSendPacketNumber(),
            connection.getVersion(),
            connection.getToken(),
            frames));
  }

  public void handlePacket(Packet packet) {
    log.info("Client got {} in state {}: {}", packet.getClass().getCanonicalName(), state, packet);

    synchronized (this) { // TODO refactor to make non-synchronized
      // TODO validate connection ID
      if (state == ClientState.WaitingForServerHello) {
        if (packet instanceof InitialPacket) {

          connection.setRemoteConnectionId(packet.getSourceConnectionId().get(), false);

          for (final Frame frame : ((InitialPacket) packet).getPayload().getFrames()) {
            if (frame instanceof CryptoFrame) {
              final CryptoFrame cf = (CryptoFrame) frame;

              final AEAD handshakeAead = tlsEngine.handleServerHello(cf.getCryptoData());
              connection.setHandshakeAead(handshakeAead);
              state = ClientState.WaitingForHandshake;
            }
          }
          log.info("Client connection state ready");
        } else if (packet instanceof RetryPacket) {
          final RetryPacket retryPacket = (RetryPacket) packet;
          connection.setRemoteConnectionId(packet.getSourceConnectionId().get(), true);
          connection.resetSendPacketNumber();
          connection.setToken(retryPacket.getRetryToken());

          tlsEngine.reset();

          sendInitialPacket();
        } else if (packet instanceof VersionNegotiationPacket) {
          // we only support a single version, so nothing more to do
          log.debug("Incompatible versions, closing connection");
          state = ClientState.Closing;
          connection.closeByPeer().awaitUninterruptibly(); // TODO fix, make async
          log.debug("Connection closed");
          state = ClientState.Closed;
        } else {
          log.warn("Got packet in an unexpected state: {} - {}", state, packet);
        }
      } else if (state == ClientState.WaitingForHandshake) {
        if (packet instanceof HandshakePacket) {
          handleHandshake((HandshakePacket) packet);
        } else {
          log.warn("Got handshake packet in an unexpected state: {} - {}", state, packet);
        }

      } else if (state == ClientState.Ready
          || state == ClientState.Closing
          || state == ClientState.Closed) { // TODO don't allow when closed
        for (Frame frame : ((FullPacket) packet).getPayload().getFrames()) {
          handleFrame(frame);
        }
      } else {
        log.warn("Got packet in an unexpected state {} {}", state, packet);
      }
    }
  }

  private void handleHandshake(final HandshakePacket packet) {
    for (final Frame frame : packet.getPayload().getFrames()) {
      if (frame instanceof CryptoFrame) {
        final CryptoFrame cf = (CryptoFrame) frame;

        final Optional<ClientTlsSession.HandshakeResult> result =
            tlsEngine.handleHandshake(cf.getCryptoData());

        if (result.isPresent()) {
          connection.setOneRttAead(result.get().getOneRttAead());

          connection.sendPacket(
              HandshakePacket.create(
                  connection.getRemoteConnectionId(),
                  connection.getLocalConnectionId(),
                  connection.nextSendPacketNumber(),
                  Version.QUIC_GO,
                  new CryptoFrame(0, result.get().getFin())));

          state = ClientState.Ready;
          handshakeFuture.setSuccess(null);
        }
      }
    }
  }

  private void handleFrame(final Frame frame) {
    if (frame instanceof StreamFrame) {
      final StreamFrame sf = (StreamFrame) frame;

      Stream stream = connection.getOrCreateStream(sf.getStreamId());
      stream.onData(sf.getOffset(), sf.isFin(), sf.getData());
    } else if (frame instanceof ResetStreamFrame) {
      final ResetStreamFrame rsf = (ResetStreamFrame) frame;
      final Stream stream = connection.getOrCreateStream(rsf.getStreamId());
      stream.onReset(rsf.getApplicationErrorCode(), rsf.getOffset());
    } else if (frame instanceof PingFrame) {
      // do nothing, will be acked
    } else if (frame instanceof ConnectionCloseFrame) {
      handlePeerClose();
    }
  }

  private void handlePeerClose() {
    log.debug("Peer closing connection");
    state = ClientState.Closing;
    connection.closeByPeer().awaitUninterruptibly(); // TODO fix, make async
    log.debug("Connection closed");
    state = ClientState.Closed;
  }

  public void closeImmediate() {
    connection.sendPacket(
        ConnectionCloseFrame.connection(
            TransportError.NO_ERROR.getValue(), 0, "Closing connection"));

    state = ClientState.Closing;

    state = ClientState.Closed;
  }

  @VisibleForTesting
  protected ClientState getState() {
    return state;
  }
}
