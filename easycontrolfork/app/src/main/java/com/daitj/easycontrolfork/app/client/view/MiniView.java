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
  private boolean isAdded = false;

  // 迷你悬浮窗
  private final ModuleMiniViewBinding miniView = ModuleMiniViewBinding.inflate(LayoutInflater.from(AppData.applicationContext));
  private final WindowManager.LayoutParams miniViewParams = new WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
    PixelFormat.TRANSLUCENT
  );

  public MiniView(String uuid) {
      device = Client.getDevice(uuid);
      clientController = Client.getClientController(uuid);
      if (device == null || clientController == null) return;
      miniViewParams.gravity = Gravity.START | Gravity.TOP;
      miniViewParams.x = 0;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          miniViewParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      }

      // 设置监听控制
      setBarListener();

      miniView.getRoot().addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
          if (!isAdded) return;

          int screenHeight = v.getResources().getDisplayMetrics().heightPixels;
          int viewHeight = v.getHeight();
          int clampedY = Math.max(0, Math.min(miniViewParams.y, screenHeight - viewHeight));

          if (miniViewParams.y != clampedY) {
              miniViewParams.y = clampedY;
              device.miniY = clampedY;
              
              try {
                  AppData.windowManager.updateViewLayout(miniView.getRoot(), miniViewParams);
              } catch (Exception ignored) {}
          }
      });

  }

  public void show(ByteBuffer byteBuffer) {
      if (device == null || clientController == null) return;

      View root = miniView.getRoot();

      root.animate().cancel();

      if (!isAdded) {
          miniViewParams.y = device.miniY;

          root.setAlpha(0f);

          try {
              AppData.windowManager.addView(root, miniViewParams);
              isAdded = true;
          } catch (Exception ignored) {
              return;
          }
      }

      root.animate()
          .alpha(1f)
          .setDuration(250)
          .start();

      // 超时检测
      if (device.miniTimeoutOnRunning && byteBuffer != null) {
          lastTouchTIme = System.currentTimeMillis();

          if (timeoutListenerThread != null) {
              timeoutListenerThread.interrupt();
          }

          timeoutListenerThread = new Thread(
              () -> timeoutListener(new String(byteBuffer.array()))
          );

          timeoutListenerThread.start();
      }
  }

  public void hide() {
      if (device == null || clientController == null) return;

      View root = miniView.getRoot();

      root.animate().cancel();

      if (!isAdded) {
          return;
      }

      root.animate()
          .alpha(0f)
          .setDuration(250)
          .withEndAction(() -> {
              if (!isAdded) return;

              try {
                  AppData.windowManager.removeView(root);
                  isAdded = false;
              } catch (Exception ignored) {
              }

              if (timeoutListenerThread != null) {
                  timeoutListenerThread.interrupt();
                  timeoutListenerThread = null;
              }
          })
          .start();
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

    // 设置监听控制.
    @SuppressLint("ClickableViewAccessibility")
    private void setBarListener() {
        final int touchSlop = ViewConfiguration.get(
            AppData.applicationContext
        ).getScaledTouchSlop();

        final int[] startX = {0};
        final int[] startY = {0};
        final int[] lastY = {0};
        final int[] gestureType = {0};

        View.OnTouchListener listener = (v, event) -> {
            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN: {
                    startX[0] = (int) event.getRawX();
                    startY[0] = (int) event.getRawY();
                    lastY[0] = startY[0];

                    gestureType[0] = 0;

                    lastTouchTIme = System.currentTimeMillis();
                    return true;
                }

                case MotionEvent.ACTION_MOVE: {
                    int currentX = (int) event.getRawX();
                    int currentY = (int) event.getRawY();

                    int totalDx = currentX - startX[0];
                    int totalDy = currentY - startY[0];

                    if (gestureType[0] == 0) {
                        if (Math.abs(totalDx) > touchSlop
                                || Math.abs(totalDy) > touchSlop) {

                            if (Math.abs(totalDx) > Math.abs(totalDy)) {
                                gestureType[0] = 1;
                            } else {
                                gestureType[0] = 2;
                            }
                        }
                    }

                    if (gestureType[0] == 1) {
                        return true;
                    }

                    if (gestureType[0] == 2) {
                        int dy = currentY - lastY[0];

                        miniViewParams.x = 0;
                        miniViewParams.y += dy;

                        int screenHeight = v.getResources()
                            .getDisplayMetrics()
                            .heightPixels;

                        int viewHeight = miniView.getRoot().getHeight();

                        miniViewParams.y = Math.max(
                            0,
                            Math.min(
                                miniViewParams.y,
                                screenHeight - viewHeight
                            )
                        );

                        device.miniY = miniViewParams.y;

                        AppData.windowManager.updateViewLayout(
                            miniView.getRoot(),
                            miniViewParams
                        );

                        lastY[0] = currentY;
                        lastTouchTIme = System.currentTimeMillis();
                    }

                    return true;
                }

                case MotionEvent.ACTION_UP: {
                    int totalDx =
                        (int) event.getRawX() - startX[0];

                    if (gestureType[0] == 1
                            && totalDx > touchSlop * 2) {

                        clientController.handleAction(
                            "changeToSmall",
                            null,
                            0
                        );
                    }

                    gestureType[0] = 0;
                    lastTouchTIme = System.currentTimeMillis();

                    return true;
                }

                case MotionEvent.ACTION_CANCEL: {
                    gestureType[0] = 0;
                    return true;
                }
            }

            return true;
        };

        miniView.getRoot().setOnTouchListener(listener);
    }
}
