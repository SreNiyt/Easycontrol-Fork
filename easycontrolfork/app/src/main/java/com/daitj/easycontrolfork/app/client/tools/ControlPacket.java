package com.daitj.easycontrolfork.app.client.tools;

import android.view.MotionEvent;                                                                     
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class ControlPacket {

public static ByteBuffer createTouchEvent(int action, int p, float x, float y, int offsetTime) {
    if (x < 0 || x > 1 || y < 0 || y > 1) {
        x = Math.max(0, Math.min(1, x));
        y = Math.max(0, Math.min(1, y));
        action = MotionEvent.ACTION_UP;
    }

    ByteBuffer byteBuffer = ByteBuffer.allocate(15);
    byteBuffer.put((byte) 1);
    byteBuffer.put((byte) action);
    byteBuffer.put((byte) p);
    byteBuffer.putFloat(x);
    byteBuffer.putFloat(y);
    byteBuffer.putInt(offsetTime);
    byteBuffer.flip();
    return byteBuffer;
}

  public static ByteBuffer createKeyEvent(int key, int meta) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(9);
    byteBuffer.put((byte) 2);
    byteBuffer.putInt(key);
    byteBuffer.putInt(meta);
    byteBuffer.flip();
    return byteBuffer;
  }

  public static ByteBuffer createClipboardEvent(String text) {
    byte[] tmpTextByte = text.getBytes(StandardCharsets.UTF_8);
    if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) return null;
    ByteBuffer byteBuffer = ByteBuffer.allocate(5 + tmpTextByte.length);
    byteBuffer.put((byte) 3);
    byteBuffer.putInt(tmpTextByte.length);
    byteBuffer.put(tmpTextByte);
    byteBuffer.flip();
    return byteBuffer;
  }

  public static ByteBuffer createKeepAlive() {
    return ByteBuffer.wrap(new byte[]{4});
  }

  public static ByteBuffer createChangeResolutionEvent(float newSize) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(5);
    byteBuffer.put((byte) 5);
    byteBuffer.putFloat(newSize);
    byteBuffer.flip();
    return byteBuffer;
  }

  public static ByteBuffer createChangeResolutionEvent(int width, int height) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(9);
    byteBuffer.put((byte) 9);
    byteBuffer.putInt(width);
    byteBuffer.putInt(height);
    byteBuffer.flip();
    return byteBuffer;
  }

  public static ByteBuffer createRotateEvent() {                                                         
    return ByteBuffer.wrap(new byte[]{6});
  }

  public static ByteBuffer createLightEvent(int mode) {
    return ByteBuffer.wrap(new byte[]{7, (byte) mode});
  }

  public static ByteBuffer createPowerEvent(int mode) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(5);
    byteBuffer.put((byte) 8);
    byteBuffer.putInt(mode);
    byteBuffer.flip();
    return byteBuffer;
  }

}

