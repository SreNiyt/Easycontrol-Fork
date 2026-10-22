package com.daitj.easycontrolfork.app.client.decode;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioDecode {
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_FRAME = CHANNELS * 2; // 16-bit PCM
    private static final int AUDIO_PACKET_SIZE = SAMPLE_RATE * BYTES_PER_FRAME * 40 / 1000;
    private static final int MAX_LATENCY_MS = 80;
    private static final long MAX_QUEUED_FRAMES = (long) SAMPLE_RATE * MAX_LATENCY_MS / 1000;
    private static final long INPUT_WAIT_MS = 10;

    private MediaCodec decodec;
    private AudioTrack audioTrack;
    private volatile boolean released = false;

    private long framesWritten = 0;
    private final int[] inputQueue = new int[64];
    private int queueHead = 0;
    private int queueTail = 0;
    private int queueSize = 0;

    private final MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int inIndex) {
            synchronized (inputQueue) {
                if (queueSize < inputQueue.length) {
                    inputQueue[queueTail] = inIndex;
                    queueTail = (queueTail + 1) % inputQueue.length;
                    queueSize++;
                    inputQueue.notifyAll();
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int outIndex, @NonNull MediaCodec.BufferInfo info) {
            try {
                ByteBuffer buffer = codec.getOutputBuffer(outIndex);
                if (buffer != null && info.size > 0) {
                    long played = audioTrack.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                    long queued = framesWritten - played;
                    if (queued <= MAX_QUEUED_FRAMES) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        int written = audioTrack.write(buffer, info.size, AudioTrack.WRITE_NON_BLOCKING);
                        if (written > 0) framesWritten += written / BYTES_PER_FRAME;
                    }
                }
            } catch (IllegalStateException ignored) {
            } finally {
                try {
                    codec.releaseOutputBuffer(outIndex, false);
                } catch (IllegalStateException ignored) {
                }
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {}

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {}
    };

    public AudioDecode(boolean useOpus, ByteBuffer csd0, Handler playHandler) throws IOException {
        setAudioTrack();
        setAudioDecodec(useOpus, csd0, playHandler);
    }

    public void release() {
        released = true;
        synchronized (inputQueue) {
            inputQueue.notifyAll();
        }
        try {
            if (decodec != null) {
                decodec.stop();
                decodec.release();
            }
        } catch (Exception ignored) {
        }
        try {
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
            }
        } catch (Exception ignored) {
        }
    }

    public void decodeIn(ByteBuffer data) throws InterruptedException {
        int inIndex;
        synchronized (inputQueue) {
            long deadline = System.nanoTime() + INPUT_WAIT_MS * 1_000_000L;
            while (queueSize == 0) {
                if (released) return;
                long remainMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainMs <= 0) return;
                inputQueue.wait(remainMs);
            }
            inIndex = inputQueue[queueHead];
            queueHead = (queueHead + 1) % inputQueue.length;
            queueSize--;
        }

        try {
            ByteBuffer inputBuffer = decodec.getInputBuffer(inIndex);
            if (inputBuffer == null) return;
            inputBuffer.clear();
            int size = Math.min(data.remaining(), inputBuffer.capacity());
            ByteBuffer src = data.duplicate();
            src.limit(src.position() + size);
            inputBuffer.put(src);
            data.position(data.position() + size);
            decodec.queueInputBuffer(inIndex, 0, size, System.nanoTime() / 1000, 0);
        } catch (IllegalStateException ignored) {
        }
    }

    private void setAudioDecodec(boolean useOpus, ByteBuffer csd0, Handler playHandler) throws IOException {
        String codecMime = useOpus ? MediaFormat.MIMETYPE_AUDIO_OPUS : MediaFormat.MIMETYPE_AUDIO_AAC;
        decodec = MediaCodec.createDecoderByType(codecMime);

        MediaFormat format = MediaFormat.createAudioFormat(codecMime, SAMPLE_RATE, CHANNELS);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_PACKET_SIZE);
        format.setByteBuffer("csd-0", csd0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        }

        if (useOpus) {
            format.setByteBuffer("csd-1", ByteBuffer.allocateDirect(8));
            format.setByteBuffer("csd-2", ByteBuffer.allocateDirect(8));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playHandler != null) {
            decodec.setCallback(callback, playHandler);
        } else {
            decodec.setCallback(callback);
        }

        decodec.configure(format, null, null, 0);
        decodec.start();
    }

    private void setAudioTrack() {
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBufferSize, (int) MAX_QUEUED_FRAMES * BYTES_PER_FRAME + AUDIO_PACKET_SIZE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder builder = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }
            audioTrack = builder.build();
        } else {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize, AudioTrack.MODE_STREAM);
        }
        audioTrack.play();
    }
}

