package com.daitj.easycontrolfork.app.client.view;

import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import com.daitj.easycontrolfork.app.client.Client;
import com.daitj.easycontrolfork.app.client.tools.ClientController;
import com.daitj.easycontrolfork.app.databinding.ModuleMiniViewBinding;
import com.daitj.easycontrolfork.app.entity.AppData;
import com.daitj.easycontrolfork.app.entity.Device;
import com.daitj.easycontrolfork.app.helper.PublicTools;
import com.daitj.easycontrolfork.app.helper.ViewTools;

public class MiniView {

  private final Device device;
  private ClientController clientController;
  private Thread timeoutListenerThread;
  private long lastTouchTIme = 0;

  // 迷你悬浮窗
  private final ModuleMiniViewBinding miniView = ModuleMiniViewBinding.inflate(LayoutInflater.from(AppData.applicationContext));
  private final WindowManager.LayoutParams miniViewParams = new WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
    PixelFormat.TRANSLUCENT
  );

  public MiniView(String uuid) {
    device = Client.getDevice(uuid);
    clientController = Client.getClientController(uuid);
    if (device == null || clientController == null) return;
    miniViewParams.gravity = Gravity.START | Gravity.TOP;
    miniViewParams.x = 0;
    // 设置监听控制
    setBarListener();
    setButtonListener();
  }

  public void show(ByteBuffer byteBuffer) {
    if (device == null || clientController == null) return;
    miniViewParams.y = device.miniY;
    // 显示
    ViewTools.viewAnim(miniView.getRoot(), true, PublicTools.dp2px(-40f), 0, (isStart -> {
      if (isStart) AppData.windowManager.addView(miniView.getRoot(), miniViewParams);
    }));
    // 超时检测
    if (device.miniTimeoutOnRunning && byteBuffer != null) {
      lastTouchTIme = System.currentTimeMillis();
      timeoutListenerThread = new Thread(() -> timeoutListener(new String(byteBuffer.array())));
      timeoutListenerThread.start();
    }
  }

  public void hide() {
    if (device == null || clientController == null) return;
    try {
      AppData.windowManager.removeView(miniView.getRoot());
      if (timeoutListenerThread != null) timeoutListenerThread.interrupt();
    } catch (Exception ignored) {
    }
  }

  // 超时监听
  private void timeoutListener(String timeoutAction) {
    try {
      long now;
      while (!Thread.interrupted()) {
        Thread.sleep(2);
        now = System.currentTimeMillis();
        if (now - lastTouchTIme > 5000) {
          clientController.handleAction( timeoutAction, null, 0);
          return;
        }
      }
    } catch (Exception ignored) {
    }
  }

  // 设置监听控制
@SuppressLint("ClickableViewAccessibility")
private void setBarListener() {
  final int HOLD_TIME = 500;

  Handler handler = new Handler(Looper.getMainLooper());

  AtomicInteger xx = new AtomicInteger();
  AtomicInteger yy = new AtomicInteger();
  AtomicInteger oldXx = new AtomicInteger();
  AtomicInteger oldYy = new AtomicInteger();

  final boolean[] isDragging = {false};

  Runnable startDragging = () -> {
    isDragging[0] = true;
  };

  miniView.getRoot().setOnTouchListener((v, event) -> {
    switch (event.getActionMasked()) {

      case MotionEvent.ACTION_OUTSIDE:
        lastTouchTIme = System.currentTimeMillis();
        break;

      case MotionEvent.ACTION_DOWN: {
        xx.set((int) event.getRawX());
        yy.set((int) event.getRawY());

        oldXx.set(miniViewParams.x);
        oldYy.set(miniViewParams.y);

        isDragging[0] = false;

        handler.postDelayed(startDragging, HOLD_TIME);

        lastTouchTIme = System.currentTimeMillis();
        return true;
      }

      case MotionEvent.ACTION_MOVE: {
        if (isDragging[0]) {
          miniViewParams.x = oldXx.get()
              + (int) event.getRawX() - xx.get();

          miniViewParams.y = oldYy.get()
              + (int) event.getRawY() - yy.get();

          device.miniY = miniViewParams.y;

          AppData.windowManager.updateViewLayout(
              miniView.getRoot(),
              miniViewParams
          );

          lastTouchTIme = System.currentTimeMillis();
        }

        return true;
      }

      case MotionEvent.ACTION_UP: {
        handler.removeCallbacks(startDragging);

        if (isDragging[0]) {
          int screenWidth = v.getResources()
              .getDisplayMetrics().widthPixels;

          int viewWidth = v.getWidth();

          int viewCenter = miniViewParams.x
              + (viewWidth / 2);

          if (viewCenter < screenWidth / 2) {
            miniViewParams.x = 0;
          } else {
            miniViewParams.x = screenWidth - viewWidth;
          }

          device.miniY = miniViewParams.y;

          AppData.windowManager.updateViewLayout(
              miniView.getRoot(),
              miniViewParams
          );

        } else {
          clientController.handleAction(
              "changeToSmall",
              null,
              0
          );
        }

        isDragging[0] = false;
        lastTouchTIme = System.currentTimeMillis();

        return true;
      }

      case MotionEvent.ACTION_CANCEL: {
        handler.removeCallbacks(startDragging);
        isDragging[0] = false;
        return true;
      }
    }

    return true;
  });
}

  // 设置按钮监听
  private void setButtonListener() {
    miniView.buttonSmall.setOnClickListener(v -> clientController.handleAction( "changeToSmall", null, 0));
  }

}
