package com.daitj.easycontrolfork.app.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferNew {
    private boolean isClosed = false;
    private final ConcurrentLinkedDeque<ByteBuffer> dataQueue = new ConcurrentLinkedDeque<>();
    private final AtomicInteger totalBytes = new AtomicInteger(0);
    private final Object waitLock = new Object();

    private final ByteBuffer reusableBuffer = ByteBuffer.allocate(256 * 1024);

    public void write(ByteBuffer data) {
        if (isClosed || data == null) return;
        ByteBuffer clone = ByteBuffer.allocate(data.remaining());
        clone.put(data);
        clone.flip();
        totalBytes.addAndGet(clone.remaining());
        dataQueue.offerLast(clone);
        synchronized (waitLock) {
            waitLock.notifyAll();
        }
    }

    public synchronized ByteBuffer read(int len) throws InterruptedException, IOException {
        if (len < 0 || isClosed) throw new IOException("BufferNew error");
        if (len > reusableBuffer.capacity()) {
            throw new IOException("Frame size exceeds buffer capacity: " + len);
        }

        while (totalBytes.get() < len && !isClosed) {
            synchronized (waitLock) {
                if (totalBytes.get() < len && !isClosed) {
                    waitLock.wait(200);
                }
            }
        }

        if (isClosed) throw new IOException("BufferNew error");

        reusableBuffer.clear();
        reusableBuffer.limit(len);

        int bytesRemaining = len;
        while (bytesRemaining > 0 && !dataQueue.isEmpty()) {
            ByteBuffer head = dataQueue.peekFirst();
            if (head == null) {
                dataQueue.pollFirst();
                continue;
            }

            int chunkRemaining = head.remaining();
            if (chunkRemaining <= bytesRemaining) {
                bytesRemaining -= chunkRemaining;
                totalBytes.addAndGet(-chunkRemaining);
                reusableBuffer.put(head);
                dataQueue.pollFirst();
            } else {
                int oldLimit = head.limit();
                head.limit(head.position() + bytesRemaining);
                reusableBuffer.put(head);
                head.limit(oldLimit);
                totalBytes.addAndGet(-bytesRemaining);
                bytesRemaining = 0;
            }
        }

        reusableBuffer.flip();
        return reusableBuffer;
    }

    public synchronized ByteBuffer readNext() throws InterruptedException, IOException {
        if (isClosed) throw new IOException("BufferNew error");
        ByteBuffer byteBuffer = dataQueue.pollFirst();
        if (byteBuffer != null) {
            totalBytes.addAndGet(-byteBuffer.remaining());
        }
        if (isClosed || byteBuffer == null) throw new IOException("BufferNew error");
        return byteBuffer;
    }

    public ByteBuffer readByteArrayBeforeClose() {
        int size = getSize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(Math.max(size, 1));
        for (ByteBuffer tmpBuffer : dataQueue) {
            byteBuffer.put(tmpBuffer.duplicate());
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    public boolean isEmpty() {
        return dataQueue.isEmpty();
    }

    public int getSize() {
        return totalBytes.get();
    }

    public void close() {
        if (isClosed) return;
        isClosed = true;
        dataQueue.offer(ByteBuffer.allocate(1));
        synchronized (waitLock) {
            waitLock.notifyAll();
        }
    }
}

