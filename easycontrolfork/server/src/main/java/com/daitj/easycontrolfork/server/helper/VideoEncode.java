package com.daitj.easycontrolfork.server.helper;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.system.ErrnoException;
import android.view.Surface;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import com.daitj.easycontrolfork.server.Server;
import com.daitj.easycontrolfork.server.entity.Device;
import com.daitj.easycontrolfork.server.entity.Options;
import com.daitj.easycontrolfork.server.wrappers.SurfaceControl;

public final class VideoEncode {
    private static final String TAG = "VideoEncode";
    private static MediaCodec encoder;
    private static MediaFormat encoderFormat;
    public static boolean isHasChangeConfig = false;
    private static boolean useH265;

    private static IBinder display;
    private static Surface surface;
    private static final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    public static void init() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
        useH265 = Options.supportH265 && EncodecTools.isSupportH265();
        ByteBuffer byteBuffer = ByteBuffer.allocate(9);
        byteBuffer.put((byte) (useH265 ? 1 : 0));
        byteBuffer.putInt(Device.videoSize.first);
        byteBuffer.putInt(Device.videoSize.second);
        byteBuffer.flip();
        Server.writeVideo(byteBuffer);
        
        display = SurfaceControl.createDisplay("easycontrol", 
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R || 
                (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !"S".equals(Build.VERSION.CODENAME)));
        
        createEncoderFormat();
        startEncode();
    }

    private static void createEncoderFormat() throws IOException {
        String codecMime = useH265 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
        encoder = MediaCodec.createEncoderByType(codecMime);
        encoderFormat = new MediaFormat();
        encoderFormat.setString(MediaFormat.KEY_MIME, codecMime);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, Options.maxVideoBit);
        encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, Options.maxFps);
        encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        encoderFormat.setFloat("max-fps-to-encoder", Options.maxFps);
        encoderFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Options.maxFps);
        encoderFormat.setInteger(MediaFormat.KEY_PRIORITY, 0);
        encoderFormat.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 50_000);
        encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    }

    public static void startEncode() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
        ControlPacket.sendVideoSizeEvent();
        encoderFormat.setInteger(MediaFormat.KEY_WIDTH, Device.videoSize.first);
        encoderFormat.setInteger(MediaFormat.KEY_HEIGHT, Device.videoSize.second);
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        
        surface = encoder.createInputSurface();
        setDisplaySurface(display, surface);
        encoder.start();
    }

    public static void stopEncode() {
        if (encoder != null) {
            encoder.stop();
            encoder.reset();
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    private static void setDisplaySurface(IBinder display, Surface surface) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        SurfaceControl.openTransaction();
        try {
            SurfaceControl.setDisplaySurface(display, surface);
            SurfaceControl.setDisplayProjection(display, 0, 
                    new Rect(0, 0, Device.displayInfo.width, Device.displayInfo.height), 
                    new Rect(0, 0, Device.videoSize.first, Device.videoSize.second));
            SurfaceControl.setDisplayLayerStack(display, Device.displayInfo.layerStack);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    public static void encodeOut() throws IOException {
        if (encoder == null) return;

        try {
            int outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000);
            
            if (outIndex >= 0) {
                ByteBuffer buffer = encoder.getOutputBuffer(outIndex);
                if (buffer != null) {
                    long pts = bufferInfo.presentationTimeUs;
                    ControlPacket.sendVideoEvent(pts, buffer);
                }
                encoder.releaseOutputBuffer(outIndex, false);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Codec in illegal state during dequeueOutputBuffer", e);
        }
    }

    public static void release() {
        try {
            stopEncode();
            if (encoder != null) {
                encoder.release();
                encoder = null;
            }
            if (display != null) {
                SurfaceControl.destroyDisplay(display);
                display = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing video encoder resources", e);
        }
    }
}

