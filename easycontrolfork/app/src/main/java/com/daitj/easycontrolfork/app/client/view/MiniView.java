package com.daitj.easycontrolfork.app.client.view;

import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewConfiguration;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
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
  private boolean isShow = false;

  // 迷你悬浮窗
  private final ModuleMiniViewBinding miniView =
      ModuleMiniViewBinding.inflate(
          LayoutInflater.from(AppData.applicationContext)
      );

  private final WindowManager.LayoutParams miniViewParams =
      new WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
              ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
              : WindowManager.LayoutParams.TYPE_PHONE,
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
          PixelFormat.TRANSLUCENT
      );

  public MiniView(String uuid) {
    device = Client.getDevice(uuid);
    clientController = Client.getClientController(uuid);

    if (device == null || clientController == null) return;

    miniViewParams.gravity = Gravity.START | Gravity.TOP;

    setBarListener();
    setButtonListener();
  }

  public void show(ByteBuffer byteBuffer) {
    if (device == null || clientController == null || isShow) return;

    miniViewParams.y = device.miniY;

    ViewTools.viewAnim(miniView.getRoot(), true, PublicTools.dp2px(-40f), 0, (isStart -> {
          if (isStart && miniView.getRoot().getParent() == null) {
            try {
                AppData.windowManager.addView(miniView.getRoot(), miniViewParams);
            } catch (Exception ignored) {}
          }
        })
    );

    isShow = true;

    if (device.miniTimeoutOnRunning && byteBuffer != null) {
      lastTouchTIme = System.currentTimeMillis();
      timeoutListenerThread = new Thread(() -> timeoutListener(new String(byteBuffer.array())));
      timeoutListenerThread.start();
    }
  }

  public void hide() {
    if (device == null || clientController == null || !isShow) return;
    try {
      if (miniView.getRoot().getParent() != null) {
          AppData.windowManager.removeView(miniView.getRoot());
      }
      if (timeoutListenerThread != null) timeoutListenerThread.interrupt();
    } catch (Exception ignored) {
    } finally {
        isShow = false;
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
          clientController.handleAction(
              timeoutAction,
              null,
              0
          );

          return;
        }
      }

    } catch (Exception ignored) {
    }
  }

  // 设置监听控制.
  @SuppressLint("ClickableViewAccessibility")
  private void setBarListener() {

    final int touchSlop =
        ViewConfiguration.get(
            AppData.applicationContext
        ).getScaledTouchSlop();

    AtomicInteger startY = new AtomicInteger();
    AtomicInteger oldY = new AtomicInteger();

    final boolean[] isDragging = {false};

    View.OnTouchListener dragListener = (v, event) -> {

      switch (event.getActionMasked()) {

        case MotionEvent.ACTION_OUTSIDE:
          lastTouchTIme = System.currentTimeMillis();
          break;

        case MotionEvent.ACTION_DOWN: {

          startY.set(
              (int) event.getRawY()
          );

          oldY.set(
              miniViewParams.y
          );

          isDragging[0] = false;

          lastTouchTIme =
              System.currentTimeMillis();

          return true;
        }

        case MotionEvent.ACTION_MOVE: {

          int dy =
              (int) event.getRawY()
                  - startY.get();

          if (!isDragging[0]
              && Math.abs(dy) > touchSlop) {

            isDragging[0] = true;
          }

          if (isDragging[0]) {

            miniViewParams.y =
                oldY.get() + dy;

            device.miniY =
                miniViewParams.y;

            AppData.windowManager.updateViewLayout(
                miniView.getRoot(),
                miniViewParams
            );

            lastTouchTIme =
                System.currentTimeMillis();
          }

          return true;
        }

        case MotionEvent.ACTION_UP: {

          if (!isDragging[0]) {

            clientController.handleAction(
                "changeToSmall",
                null,
                0
            );
          }

          isDragging[0] = false;

          lastTouchTIme =
              System.currentTimeMillis();

          return true;
        }

        case MotionEvent.ACTION_CANCEL: {

          isDragging[0] = false;

          return true;
        }
      }

      return true;
    };

    miniView.getRoot()
        .setOnTouchListener(dragListener);

    miniView.buttonSmall
        .setOnTouchListener(dragListener);
  }

  // 设置按钮监听
  private void setButtonListener() {
    miniView.buttonSmall.setOnClickListener(
        v -> clientController.handleAction(
            "changeToSmall",
            null,
            0
        )
    );
  }
}


