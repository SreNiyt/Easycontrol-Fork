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
import android.view.VelocityTracker;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

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

private void animateMiniViewTo(int targetX, int targetY) {
  int startX = miniViewParams.x;
  int startY = miniViewParams.y;

  ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

  animator.setDuration(500);
  animator.setInterpolator(new DecelerateInterpolator());

  animator.addUpdateListener(animation -> {
    float progress = (float) animation.getAnimatedValue();

    miniViewParams.x = startX
        + (int) ((targetX - startX) * progress);

    miniViewParams.y = startY
        + (int) ((targetY - startY) * progress);

    device.miniY = miniViewParams.y;

    AppData.windowManager.updateViewLayout(
        miniView.getRoot(),
        miniViewParams
    );
  });

  animator.start();
}

private void flingMiniView(float velocityX) {
  int screenWidth = AppData.applicationContext
      .getResources()
      .getDisplayMetrics()
      .widthPixels;

  int viewWidth = miniView.getRoot().getWidth();

  int currentX = miniViewParams.x;

  final float friction = 0.92f;

  float flingDistance = velocityX * 0.35f;

  int targetX;

  if (velocityX < 0) {
    targetX = 0;
  } else {
    targetX = screenWidth - viewWidth;
  }

  if (Math.abs(velocityX) < 100) {
    targetX = currentX < screenWidth / 2
        ? 0
        : screenWidth - viewWidth;

    animateMiniViewTo(
        targetX,
        miniViewParams.y
    );

    return;
  }

  final float[] currentVelocity = {velocityX};

  ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

  animator.setDuration(1500);

  animator.addUpdateListener(animation -> {
    float progress = (float) animation.getAnimatedValue();

    currentVelocity[0] *= friction;

    miniViewParams.x +=
        (int) (currentVelocity[0] * 0.016f);

    if (miniViewParams.x <= 0) {
      miniViewParams.x = 0;
      currentVelocity[0] = 0;
      animation.cancel();

    } else if (miniViewParams.x >= screenWidth - viewWidth) {
      miniViewParams.x = screenWidth - viewWidth;
      currentVelocity[0] = 0;
      animation.cancel();
    }

    device.miniY = miniViewParams.y;

    AppData.windowManager.updateViewLayout(
        miniView.getRoot(),
        miniViewParams
    );

    if (Math.abs(currentVelocity[0]) < 10) {
      animation.cancel();

      int finalX = miniViewParams.x < screenWidth / 2
          ? 0
          : screenWidth - viewWidth;

      animateMiniViewTo(
          finalX,
          miniViewParams.y
      );
    }
  });

  animator.start();
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

  // 设置监听控制.
@SuppressLint("ClickableViewAccessibility")
private void setBarListener() {
  final int touchSlop = ViewConfiguration.get(
      AppData.applicationContext
  ).getScaledTouchSlop();

  final VelocityTracker[] velocityTracker = {
      VelocityTracker.obtain()
  };

  AtomicInteger xx = new AtomicInteger();
  AtomicInteger yy = new AtomicInteger();
  AtomicInteger oldXx = new AtomicInteger();
  AtomicInteger oldYy = new AtomicInteger();

  final boolean[] isDragging = {false};

  View.OnTouchListener dragListener = (v, event) -> {

    switch (event.getActionMasked()) {

      case MotionEvent.ACTION_DOWN: {
        velocityTracker[0].clear();
        velocityTracker[0].addMovement(event);

        xx.set((int) event.getRawX());
        yy.set((int) event.getRawY());

        oldXx.set(miniViewParams.x);
        oldYy.set(miniViewParams.y);

        isDragging[0] = false;

        lastTouchTIme = System.currentTimeMillis();

        return true;
      }

      case MotionEvent.ACTION_MOVE: {
        velocityTracker[0].addMovement(event);

        int dx = (int) event.getRawX() - xx.get();
        int dy = (int) event.getRawY() - yy.get();

        if (!isDragging[0]
            && (Math.abs(dx) > touchSlop
            || Math.abs(dy) > touchSlop)) {

          isDragging[0] = true;
        }

        if (isDragging[0]) {

          miniViewParams.x = oldXx.get() + dx;
          miniViewParams.y = oldYy.get() + dy;

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
        velocityTracker[0].addMovement(event);
        velocityTracker[0].computeCurrentVelocity(1000);

        float velocityX = velocityTracker[0].getXVelocity();

        if (isDragging[0]) {

          flingMiniView(velocityX);

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
        velocityTracker[0].clear();

        isDragging[0] = false;

        return true;
      }
    }

    return true;
  };

  miniView.getRoot().setOnTouchListener(dragListener);
  miniView.buttonSmall.setOnTouchListener(dragListener);
}

  // 设置按钮监听
  private void setButtonListener() {
    miniView.buttonSmall.setOnClickListener(v -> clientController.handleAction( "changeToSmall", null, 0));
  }

}
