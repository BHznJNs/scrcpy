package com.genymobile.scrcpy.control;

import android.net.LocalSocket;
import com.genymobile.scrcpy.util.Ln;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class ControlChannel {

    private final ControlMessageReader reader;
    private final DeviceMessageWriter writer;
    private final UdpUhidMessageReader udpUhidReader;
    private final BlockingQueue<ControlMessage> receivedQueue;

    public ControlChannel(LocalSocket controlSocket, DatagramSocket udpUhidSocket) throws IOException {
        receivedQueue = new LinkedBlockingQueue<ControlMessage>();
        reader = new ControlMessageReader(controlSocket.getInputStream());
        writer = new DeviceMessageWriter(controlSocket.getOutputStream());
        udpUhidReader = new UdpUhidMessageReader(udpUhidSocket);
        startSocketListeners();
    }

    private void startSocketListeners() {
        Thread localSocketThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ControlMessage msg = reader.read();
                    if (msg == null) break;
                    receivedQueue.put(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                Ln.e("LocalSocket read error", e);
            }
            Ln.d("LocalSocket listener stopped.");
        });
        localSocketThread.setName("LocalSocketListener");
        localSocketThread.start();

        Thread udpSocketThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ControlMessage msg = udpUhidReader.read();
                    if (msg == null) break;
                    receivedQueue.put(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                Ln.e("UDP socket receive error", e);
            }
            Ln.d("UDP listener stopped.");
        });
        udpSocketThread.setName("UdpSocketListener");
        udpSocketThread.start();
    }

    public ControlMessage recv() throws IOException {
        try {
            return receivedQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for message");
        }
    }

    public void send(DeviceMessage msg) throws IOException {
        writer.write(msg);
    }
}
