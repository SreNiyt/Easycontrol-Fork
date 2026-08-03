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
import android.view.Choreographer;

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

private void flingMiniView(
    float velocityX,
    float velocityY
) {
  final int screenWidth =
      AppData.applicationContext
          .getResources()
          .getDisplayMetrics()
          .widthPixels;

  final int screenHeight =
      AppData.applicationContext
          .getResources()
          .getDisplayMetrics()
          .heightPixels;

  final int viewWidth =
      miniView.getRoot().getWidth();

  final int viewHeight =
      miniView.getRoot().getHeight();

  final float[] vx = {velocityX};
  final float[] vy = {velocityY};

  final long[] lastTime = {
      System.nanoTime()
  };

  // Friction.
  // Smaller = stops faster.
  final float friction = 0.90f;

  Choreographer.FrameCallback frameCallback =
      new Choreographer.FrameCallback() {

    @Override
    public void doFrame(long frameTimeNanos) {

      long now = frameTimeNanos;

      float dt =
          (now - lastTime[0]) / 1_000_000_000f;

      lastTime[0] = now;

      // Prevent huge jumps if the app was paused.
      dt = Math.min(dt, 0.05f);

      // Apply friction based on frame time.
      float frictionFactor =
          (float) Math.pow(friction, dt * 60f);

      vx[0] *= frictionFactor;
      vy[0] *= frictionFactor;

      // Move using velocity.
      miniViewParams.x +=
          (int) (vx[0] * dt);

      miniViewParams.y +=
          (int) (vy[0] * dt);

      boolean hitLeft =
          miniViewParams.x <= 0;

      boolean hitRight =
          miniViewParams.x >= screenWidth - viewWidth;

      boolean hitTop =
          miniViewParams.y <= 0;

      boolean hitBottom =
          miniViewParams.y >= screenHeight - viewHeight;

      // Stop at horizontal edges.
      if (hitLeft) {
        miniViewParams.x = 0;
        vx[0] = 0;
      }

      if (hitRight) {
        miniViewParams.x =
            screenWidth - viewWidth;

        vx[0] = 0;
      }

      // Keep vertical movement inside screen.
      if (hitTop) {
        miniViewParams.y = 0;
        vy[0] = 0;
      }

      if (hitBottom) {
        miniViewParams.y =
            screenHeight - viewHeight;

        vy[0] = 0;
      }

      device.miniY =
          miniViewParams.y;

      AppData.windowManager.updateViewLayout(
          miniView.getRoot(),
          miniViewParams
      );

      // Stop when the velocity is almost zero.
      if (Math.abs(vx[0]) < 5
          && Math.abs(vy[0]) < 5) {

        // Choose the nearest horizontal edge.
        int targetX;

        if (miniViewParams.x
            < screenWidth / 2) {

          targetX = 0;

        } else {

          targetX =
              screenWidth - viewWidth;
        }

        animateMiniViewTo(
            targetX,
            miniViewParams.y
        );

        return;
      }

      // Continue next frame.
      Choreographer
          .getInstance()
          .postFrameCallback(this);
    }
  };

  Choreographer
      .getInstance()
      .postFrameCallback(frameCallback);
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
        float velocityY = velocityTracker[0].getYVelocity();

        if (isDragging[0]) {

          flingMiniView(
              velocityX,
              velocityY
          );

        } else {

          clientController.handleAction(
              "changeToSmall",
              null,
              0
          );
        }

        isDragging[0] = false;

        velocityTracker[0].clear();

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
