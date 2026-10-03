package com.daitj.easycontrolfork.app.client.decode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VideoDecode {
    private static final String TAG = "VideoDecode";
    private MediaCodec decoder;
    private final LinkedBlockingQueue<Integer> inputBufferQueue = new LinkedBlockingQueue<>();

    private final MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inIndex) {
            inputBufferQueue.offer(inIndex);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outIndex, @NonNull MediaCodec.BufferInfo bufferInfo) {
            try {
                mediaCodec.releaseOutputBuffer(outIndex, bufferInfo.presentationTimeUs);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Failed to release output buffer: codec may be in an invalid state.", e);
            }
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            Log.e(TAG, "MediaCodec Error: " + e.getDiagnosticInfo(), e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat format) {
            Log.d(TAG, "Output format changed to: " + format);
        }
    };

    public VideoDecode(Pair<Integer, Integer> videoSize, Surface surface, ByteBuffer csd0, ByteBuffer csd1, Handler playHandler) throws IOException, InterruptedException {
        setupVideoDecoder(videoSize, surface, csd0, csd1, playHandler);
    }

    public void release() {
        if (decoder != null) {
            try {
                decoder.stop();
                decoder.release();
            } catch (Exception e) {
                Log.w(TAG, "Error during decoder release", e);
            } finally {
                decoder = null;
            }
        }
        inputBufferQueue.clear();
    }

    public void decodeIn(ByteBuffer data) throws InterruptedException {
        if (decoder == null) return;

        try {
            long pts = data.getLong();
            int size = data.remaining();

            Integer inIndex = inputBufferQueue.poll(100, TimeUnit.MILLISECONDS);
            if (inIndex == null) {
                Log.w(TAG, "Input buffer poll timeout. Dropping frame.");
                return;
            }

            ByteBuffer inputBuffer = decoder.getInputBuffer(inIndex);
            if (inputBuffer == null) {
                return;
            }

            inputBuffer.clear();
            inputBuffer.put(data);

            decoder.queueInputBuffer(inIndex, 0, size, pts, 0);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state while queuing input buffer", e);
        }
    }

    private void setupVideoDecoder(Pair<Integer, Integer> videoSize, Surface surface, ByteBuffer csd0, ByteBuffer csd1, Handler playHandler) throws IOException, InterruptedException {
        boolean useH265 = csd1 == null;
        String codecMime = useH265 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
        
        try {
            String codecName = DecodecTools.getVideoDecoder(useH265);
            if (Objects.equals(codecName, "")) {
                decoder = MediaCodec.createDecoderByType(codecMime);
            } else {
                decoder = MediaCodec.createByCodecName(codecName);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to create decoder by name, falling back to type", e);
            decoder = MediaCodec.createDecoderByType(codecMime);
        }

        MediaFormat decodeFormat = MediaFormat.createVideoFormat(codecMime, videoSize.first, videoSize.second);
        
        ByteBuffer csd0Format = csd0.duplicate();
        csd0Format.position(8);
        decodeFormat.setByteBuffer("csd-0", csd0Format);

        if (!useH265) {
            ByteBuffer csd1Format = csd1.duplicate();
            csd1Format.position(8);
            decodeFormat.setByteBuffer("csd-1", csd1Format);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playHandler != null) {
            decoder.setCallback(callback, playHandler);
        } else {
            decoder.setCallback(callback);
        }

        decoder.configure(decodeFormat, surface, null, 0);
        decoder.start();

        csd0.position(0);
        decodeIn(csd0);
        if (!useH265) {
            csd1.position(0);
            decodeIn(csd1);
        }
    }
}

