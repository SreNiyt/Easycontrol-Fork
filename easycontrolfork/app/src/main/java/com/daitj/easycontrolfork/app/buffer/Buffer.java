package com.daitj.easycontrolfork.app.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Buffer {
    private final int capacity;
    private final byte[] buffer;
    private int head = 0;
    private int tail = 0;

    private final Object writeLock = new Object();
    private final Object readLock = new Object();

    public Buffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new byte[capacity];
    }

    public void write(byte[] data) {
        synchronized (writeLock) {
            int len = data.length;
            if (len > capacity) {
                len = capacity;
            }

            int remainingBytes = capacity - tail;
            if (len <= remainingBytes) {
                System.arraycopy(data, 0, buffer, tail, len);
                tail += len;
            } else {
                System.arraycopy(data, 0, buffer, tail, remainingBytes);
                int remainingData = len - remainingBytes;
                System.arraycopy(data, remainingBytes, buffer, 0, remainingData);
                tail = remainingData;
            }
            
            synchronized (buffer) {
                buffer.notifyAll();
            }
        }
    }

    public void read(ByteBuffer outBuffer, int size) throws InterruptedException, IOException {
        require(size);
        
        outBuffer.clear();
        outBuffer.limit(size);

        synchronized (readLock) {
            int remainingBytes = capacity - head;
            if (size <= remainingBytes) {
                outBuffer.put(buffer, head, size);
                head += size;
            } else {
                outBuffer.put(buffer, head, remainingBytes);
                int remainingSize = size - remainingBytes;
                outBuffer.put(buffer, 0, remainingSize);
                head = remainingSize;
            }
        }
    }

    private void require(long byteCount) throws InterruptedException {
        while (true) {
            if (getSize() >= byteCount) {
                break;
            } else {
                synchronized (buffer) {
                    buffer.wait();
                }
            }
        }
    }

    public int getSize() {
        if (tail >= head) return tail - head;
        else return capacity - head + tail;
    }
}

