/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;
import java.io.*;
import java.nio.ByteBuffer;

/**
 *
 * @author veronique
 */

public class BitIO {

    /* =======================
     *  BitOutputStream
     * ======================= */
    public static class BitOutputStream implements Closeable {
        private final OutputStream out;
        private int currentByte = 0;
        private int numBitsFilled = 0;
        private long bitPos = 0;

        public BitOutputStream(OutputStream out) {
            this.out = out;
        }
        
        /** Write a single bit (0 or 1). */
        public void writeBit(int bit) throws IOException {
            if (bit != 0 && bit != 1) throw new IllegalArgumentException("bit must be 0 or 1");
            currentByte = (currentByte << 1) | (bit & 1);
            numBitsFilled++;
            bitPos++;
            if (numBitsFilled == 8) flushCurrentByte();
        }

        /** Pad with zero bits until the next byte boundary. */
        
        public void padToByte() throws IOException {
            if (numBitsFilled > 0) {
                currentByte <<= (8 - numBitsFilled);
                out.write(currentByte);
                currentByte = 0;
                numBitsFilled = 0;
            }
        }
        /** Current bit position (bits written so far) */
        public long getBitPosition() { return bitPos; } 

        
        /** Flush any partial byte and underlying stream */
        public void flush() throws IOException {
            if (numBitsFilled > 0) {
                // pad leftover bits with zeros to form a full byte and write it
                currentByte <<= (8 - numBitsFilled);
                out.write(currentByte);
                currentByte = 0;
                numBitsFilled = 0;
            }
            out.flush();
        }

        /** Return total bits written so far (debug) */
        public long getTotalBitsWritten() {
            return bitPos;
        }

        // Optional: store bytes in memory if needed
        private ByteArrayOutputStream buffer = null;

        public byte[] toByteArray() throws IOException {
            if (buffer == null) throw new IOException("Underlying stream is not ByteArrayOutputStream");
            // flush any remaining bits first
            if (numBitsFilled > 0) flushCurrentByte();
            return buffer.toByteArray();
        }
        /** Write n bits (1 ≤ n ≤ 64) from value */
        public void writeBits(long value, int n) throws IOException {
            for (int i = n - 1; i >= 0; i--) {
                currentByte = (currentByte << 1) | (int) ((value >>> i) & 1L);
                numBitsFilled++;
                bitPos++;
                if (numBitsFilled == 8) flushCurrentByte();
            }
        }

        /** Write a double as an n-bit integer using linear mapping [min, max] → [0, 2^n-1] */
        public void writeFloat(double value, double min, double max, int nBits) throws IOException {
            if (value < min) value = min;
            if (value > max) value = max;
            long maxInt = (1L << nBits) - 1;
            long quantized = Math.round((value - min) / (max - min) * maxInt);
            writeBits(quantized, nBits);
        }

        private void flushCurrentByte() throws IOException {
            out.write(currentByte);
            currentByte = 0;
            numBitsFilled = 0;
        }


        @Override
        public void close() throws IOException {
            if (numBitsFilled > 0) {
                currentByte <<= (8 - numBitsFilled);
                out.write(currentByte);
            }
            out.close();
        }
    }

    /* =======================
     *  BitInputStream
     * ======================= */
    public static class BitBufferInputStream {
        private final ByteBuffer buffer;
        private int currentByte = 0;
        private int numBitsRemaining = 0;
        private long bitPos = 0;

        public BitBufferInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        /** Align to next byte */
        public void alignToByte() {
            while (bitPos % 8 != 0) readBits(1);
        }

        /** Read n bits (1 ≤ n ≤ 64) as long */
        public long readBits(int n) {
            long value = 0;
            for (int i = 0; i < n; i++) {
                if (numBitsRemaining == 0) {
                    if (!buffer.hasRemaining()) throw new RuntimeException("EOF");
                    currentByte = buffer.get() & 0xFF;
                    numBitsRemaining = 8;
                }
                value = (value << 1) | ((currentByte >>> 7) & 1);
                currentByte = (currentByte << 1) & 0xFF;
                numBitsRemaining--;
                bitPos++;
            }
            return value;
        }

        /** Read n-bit float */
        public double readFloat(double min, double max, int nBits) {
            long quantized = readBits(nBits);
            double norm = ((double) quantized) / ((1L << nBits) - 1);
            return min + norm * (max - min);
        }


        /** Current bit position */
        public long getBitPosition() { return bitPos; }

        /** Return true if more bits are available */
        public boolean hasMore() {
            return buffer.hasRemaining() || numBitsRemaining > 0;
        }
    }

    
    public static class BitInputStream implements Closeable {
        private final InputStream in;
        private int currentByte = 0;
        private int numBitsRemaining = 0;
        private long bitPos = 0L;
        private boolean eof = false;

        public BitInputStream(InputStream in) {
            this.in = in;
        }

        public void alignToByte() throws IOException {
            while (bitPos % 8 != 0) {
                readBits(1);
            }
        }

        /** Read n bits (1 ≤ n ≤ 64) and return as long */
        public long readBits(int n) throws IOException {
            long value = 0;
            for (int i = 0; i < n; i++) {
                if (numBitsRemaining == 0) {
                    int read = in.read();
                    if (read == -1) {
                        eof = true;
                        throw new EOFException();
                    }
                    currentByte = read & 0xFF;
                    numBitsRemaining = 8;
                }
                value = (value << 1) | ((currentByte >>> 7) & 1);
                currentByte = (currentByte << 1) & 0xFF;
                numBitsRemaining--;
                bitPos++;
            }
            return value;
        }

        /** Read n-bit float using same mapping as writeFloat */
        public double readFloat(double min, double max, int nBits) throws IOException {
            long quantized = readBits(nBits);
            double norm = ((double) quantized) / ((1L << nBits) - 1);
            return min + norm * (max - min);
        }

        public void skipBits(long n) throws IOException {
            while (n > 0) {
                if (numBitsRemaining == 0) {
                    int read = in.read();
                    if (read == -1) throw new EOFException();
                    currentByte = read & 0xFF;
                    numBitsRemaining = 8;
                }
                long consume = Math.min(n, numBitsRemaining);
                numBitsRemaining -= consume;
                n -= consume;
                bitPos += consume;
            }
        }

        /** Return current bit position */
        public long getBitPosition() { return bitPos; }

        /** Return true if more bits are available */
        public boolean hasMore() throws IOException {
            return !eof && (numBitsRemaining > 0 || in.available() > 0);
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
