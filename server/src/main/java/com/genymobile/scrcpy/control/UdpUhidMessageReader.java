package com.genymobile.scrcpy.control;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class UdpUhidMessageReader {
    private static class SeqValidator {
        private static final int SEQ_BITS = 16;
        private static final int SEQ_MOD = 1 << SEQ_BITS;           // 65536
        private static final int WINDOW_HALF = 1 << (SEQ_BITS - 1); // 32768

        private int maxSeq = -1; // -1 表示还没收到任何包

        /**
         * 返回 true  -> 接受此序号，同时内部 maxSeq 会被更新
         * 返回 false -> 丢弃（过旧或绕回后重号）
         */
        public boolean shouldAccept(int seq) {
            seq &= 0xFFFF;         // 确保是 16 位无符号
            if (maxSeq == -1) {    // 第一个包
                maxSeq = seq;
                return true;
            }
            int diff = (seq - maxSeq) & (SEQ_MOD - 1);
            if (diff == 0) {
                return false;      // 完全重复
            }
            if (diff < WINDOW_HALF) {
                maxSeq = seq;      // 正常递增或绕回后“新”
                return true;
            } else {
                return false;      // 绕回后“旧”
            }
        }
    }

    // --- --- --- --- --- ---

    private final DatagramSocket socket;
    private final SeqValidator validator = new SeqValidator();

    public UdpUhidMessageReader(DatagramSocket socket) {
        this.socket = socket;
    }

    public ControlMessage read() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        while (true) {
            socket.receive(receivePacket);
            ControlMessage msg = parse(receivePacket);
            if (msg != null) return msg;
        }
    }

    private ControlMessage parse(DatagramPacket packet) throws IOException {
        DataInputStream in = new DataInputStream(
            new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()));
        int seq = in.readUnsignedShort();
        if (!validator.shouldAccept(seq)) {
            return null;
        }

        int type = in.readUnsignedByte();
        if (type != ControlMessage.TYPE_UHID_INPUT) {
            throw new IOException("Invalid message type: " + type);
        }
        int id = in.readUnsignedShort();
        byte[] data = parseByteArray(in, 2);
        return ControlMessage.createUhidInput(id, data);
    }

    private byte[] parseByteArray(DataInputStream in, int sizeBytes) throws IOException {
        int len = parseBufferLength(in, sizeBytes);
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    private int parseBufferLength(DataInputStream in, int sizeBytes) throws IOException {
        assert sizeBytes > 0 && sizeBytes <= 4;
        int value = 0;
        for (int i = 0; i < sizeBytes; ++i) {
            value = (value << 8) | in.readUnsignedByte();
        }
        return value;
    }
}
