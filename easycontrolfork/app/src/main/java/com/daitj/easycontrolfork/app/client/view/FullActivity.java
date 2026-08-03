package com.daitj.easycontrolfork.app.client.view;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.util.Objects;

import com.daitj.easycontrolfork.app.R;
import com.daitj.easycontrolfork.app.client.Client;
import com.daitj.easycontrolfork.app.client.tools.ClientController;
import com.daitj.easycontrolfork.app.client.tools.ControlPacket;
import com.daitj.easycontrolfork.app.databinding.ActivityFullBinding;
import com.daitj.easycontrolfork.app.entity.AppData;
import com.daitj.easycontrolfork.app.entity.Device;
import com.daitj.easycontrolfork.app.helper.PublicTools;
import com.daitj.easycontrolfork.app.helper.ViewTools;

public class FullActivity extends Activity {
  private boolean isClose = false;
  private Device device;
  private ClientController clientController;
  private ActivityFullBinding activityFullBinding;
  private boolean autoRotate;
  private boolean light = true;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ViewTools.setStatusAndNavBar(this);
    ViewTools.setFullScreen(this);
    activityFullBinding = ActivityFullBinding.inflate(this.getLayoutInflater());
    setContentView(activityFullBinding.getRoot());
    String uuid = getIntent().getStringExtra("uuid");
    device = Client.getDevice(uuid);
    clientController = Client.getClientController(uuid);
    if (device == null || clientController == null) return;
    clientController.setFullView(this);
    // 初始化
    activityFullBinding.barView.setVisibility(View.GONE);
    setNavBarHide(device.showNavBarOnConnect);
    autoRotate = AppData.setting.getAutoRotate();
    activityFullBinding.buttonAutoRotate.setImageResource(autoRotate ? R.drawable.un_auto : R.drawable.auto);
    if (!Objects.equals(device.startApp, "")) {
      activityFullBinding.buttonHome.setVisibility(View.GONE);
      activityFullBinding.buttonSwitch.setVisibility(View.GONE);
      activityFullBinding.buttonApp.setVisibility(View.GONE);
    }
    // 按键监听
    setButtonListener();
    setKeyEvent();
    // 更新textureView
    activityFullBinding.textureViewLayout.addView(clientController.getTextureView(), 0);
    activityFullBinding.textureViewLayout.post(this::updateMaxSize);
  }

  @Override
  protected void onPause() {
    if (isChangingConfigurations()) activityFullBinding.textureViewLayout.removeView(clientController.getTextureView());
    else if (!isClose) clientController.handleAction(device.fullToMiniOnRunning ? "changeToMini" : "changeToSmall", ByteBuffer.wrap("changeToFull".getBytes()), 0);
    super.onPause();
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
    activityFullBinding.textureViewLayout.post(this::updateMaxSize);
    super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
  }

  @Override
  public void onBackPressed() {
  }

  private void updateMaxSize() {
    int width = activityFullBinding.textureViewLayout.getMeasuredWidth();
    int height = activityFullBinding.textureViewLayout.getMeasuredHeight();
    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
    byteBuffer.putInt(width);
    byteBuffer.putInt(height);
    byteBuffer.flip();
    clientController.handleAction("updateMaxSize", byteBuffer, 0);
    if (!device.customResolutionOnConnect && device.changeResolutionOnRunning) clientController.handleAction("writeByteBuffer", ControlPacket.createChangeResolutionEvent((float) width / height), 0);
  }

  public void hide() {
    if (device == null || clientController == null) return;
    try {
      isClose = true;
      activityFullBinding.textureViewLayout.removeView(clientController.getTextureView());
      finish();
    } catch (Exception ignored) {
    }
  }

  // 设置按钮监听
  private void setButtonListener() {
    activityFullBinding.buttonBack.setOnClickListener(v -> clientController.handleAction("buttonBack", null, 0));
    activityFullBinding.buttonHome.setOnClickListener(v -> clientController.handleAction("buttonHome", null, 0));
    activityFullBinding.buttonSwitch.setOnClickListener(v -> clientController.handleAction("buttonSwitch", null, 0));
    activityFullBinding.buttonApp.setOnClickListener(v -> {
      clientController.handleAction("changeToApp", null, 0);
      changeBarView();
    });
    activityFullBinding.buttonMini.setOnClickListener(v -> clientController.handleAction("changeToMini", null, 0));
    activityFullBinding.buttonSmall.setOnClickListener(v -> clientController.handleAction("changeToSmall", null, 0));
    activityFullBinding.buttonClose.setOnClickListener(v -> Client.sendAction(device.uuid, "close", null, 0));
    activityFullBinding.buttonRotate.setOnClickListener(v -> {
      clientController.handleAction("buttonRotate", null, 0);
      changeBarView();
    });
    activityFullBinding.buttonNavBar.setOnClickListener(v -> {
      setNavBarHide(activityFullBinding.navBar.getVisibility() == View.GONE);
      changeBarView();
    });
    activityFullBinding.buttonPower.setOnClickListener(v -> {
      clientController.handleAction("buttonPower", null, 0);
      changeBarView();
    });
    activityFullBinding.buttonLight.setOnClickListener(v -> {
      light = !light;
      activityFullBinding.buttonLight.setImageResource(light ? R.drawable.lightbulb_off : R.drawable.lightbulb);
      clientController.handleAction(light ? "buttonLight" : "buttonLightOff", null, 0);
      changeBarView();
    });
    setupDraggableMoreButton();
    activityFullBinding.buttonAutoRotate.setOnClickListener(v -> {
      autoRotate = !autoRotate;
      AppData.setting.setAutoRotate(autoRotate);
      activityFullBinding.buttonAutoRotate.setImageResource(autoRotate ? R.drawable.un_auto : R.drawable.auto);
    });
  }

private void setupDraggableMoreButton() {
  View button = activityFullBinding.buttonMore;

  final float[] downX = {0};
  final float[] downY = {0};
  final float[] startX = {0};
  final float[] startY = {0};
  final boolean[] isDragging = {false};

  button.setOnTouchListener((v, event) -> {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX[0] = event.getRawX();
        downY[0] = event.getRawY();
        startX[0] = v.getTranslationX();
        startY[0] = v.getTranslationY();
        isDragging[0] = false;
        return true;

      case MotionEvent.ACTION_MOVE:
        float dx = event.getRawX() - downX[0];
        float dy = event.getRawY() - downY[0];

        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
          isDragging[0] = true;
        }

        if (isDragging[0]) {
          v.setTranslationX(startX[0] + dx);
          v.setTranslationY(startY[0] + dy);
        }
        return true;

      case MotionEvent.ACTION_UP:
        if (!isDragging[0]) {
          changeBarView();
        }
        return true;

      case MotionEvent.ACTION_CANCEL:
        return true;
    }

    return false;
  });
}

  // 导航栏隐藏
  private void setNavBarHide(boolean isShow) {
    activityFullBinding.navBar.setVisibility(isShow ? View.VISIBLE : View.GONE);
    activityFullBinding.buttonNavBar.setImageResource(isShow ? R.drawable.not_equal : R.drawable.equals);
    activityFullBinding.textureViewLayout.post(this::updateMaxSize);
    activityFullBinding.buttonMore.setImageTintList(ColorStateList.valueOf(getResources().getColor(isShow ? R.color.onCardBackground : R.color.onBlackBacnground)));
  }

  private void changeBarView() {
    boolean toShowView = activityFullBinding.barView.getVisibility() == View.GONE;
    boolean isLandscape = lastOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || lastOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
    ViewTools.viewAnim(activityFullBinding.barView, toShowView, 0, PublicTools.dp2px(40f) * (isLandscape ? -1 : 1), (isStart -> {
      if (isStart && toShowView) activityFullBinding.barView.setVisibility(View.VISIBLE);
      else if (!isStart && !toShowView) activityFullBinding.barView.setVisibility(View.GONE);
    }));
  }

private int lastOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

  // 设置键盘监听
  private void setKeyEvent() {
    activityFullBinding.editText.requestFocus();
    activityFullBinding.editText.setInputType(InputType.TYPE_NULL);
    activityFullBinding.editText.setOnKeyListener((v, keyCode, event) -> {
      if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
        clientController.handleAction("writeByteBuffer", ControlPacket.createKeyEvent(event.getKeyCode(), event.getMetaState()), 0);
        return true;
      }
      return false;
    });
  }
}
