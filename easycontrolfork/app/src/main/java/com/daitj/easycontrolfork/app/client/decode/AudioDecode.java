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
    private MediaCodec decodec;
    private AudioTrack audioTrack;
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int AUDIO_PACKET_SIZE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE * 40 / 1000;

    private final int[] inputQueue = new int[64];
    private int queueHead = 0;
    private int queueTail = 0;
    private int queueSize = 0;

    private final MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inIndex) {
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
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outIndex, @NonNull MediaCodec.BufferInfo bufferInfo) {
            try {
                ByteBuffer buffer = decodec.getOutputBuffer(outIndex);
                if (buffer == null) return;
                audioTrack.write(buffer, bufferInfo.size, AudioTrack.WRITE_NON_BLOCKING);
                decodec.releaseOutputBuffer(outIndex, false);
            } catch (IllegalStateException ignored) {
            }
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {}

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat format) {}
    };

    public AudioDecode(boolean useOpus, ByteBuffer csd0, Handler playHandler) throws IOException {
        setAudioDecodec(useOpus, csd0, playHandler);
        setAudioTrack();
    }

    public void release() {
        try {
            if (decodec != null) {
                decodec.stop();
                decodec.release();
            }
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
            while (queueSize == 0) {
                inputQueue.wait();
            }
            inIndex = inputQueue[queueHead];
            queueHead = (queueHead + 1) % inputQueue.length;
            queueSize--;
        }

        try {
            ByteBuffer inputBuffer = decodec.getInputBuffer(inIndex);
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(data);
                decodec.queueInputBuffer(inIndex, 0, data.capacity(), 0, 0);
            }
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
        
        if (useOpus) {
            ByteBuffer csd12 = ByteBuffer.allocateDirect(8); 
            format.setByteBuffer("csd-1", csd12);
            format.setByteBuffer("csd-2", csd12);
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
        int bufferSize = Math.min(minBufferSize * 8, 16 * AUDIO_PACKET_SIZE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack = new AudioTrack.Builder()
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
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        } else {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, 
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, 
                    bufferSize, AudioTrack.MODE_STREAM);
        }
        audioTrack.play();
    }
}

