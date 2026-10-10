/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package com.daitj.easycontrolfork.server.wrappers;

import android.view.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class InputManager {

  public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

  private static Object manager;
  private static Method injectInputEventMethod;
  private static Method setDisplayIdMethod;
  private static boolean initialized = false;

  private InputManager() {
  }

  public static void init(Object m) throws NoSuchMethodException {
    manager = m;
    injectInputEventMethod = manager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
    
    try {
      setDisplayIdMethod = InputEvent.class.getMethod("setDisplayId", int.class);
    } catch (NoSuchMethodException e) {
      System.err.println("InputManager: setDisplayId method not found. Ignored for older API compatibility.");
    }
    
    initialized = true;
  }

  public static void setDisplayId(InputEvent inputEvent, int displayId) throws InvocationTargetException, IllegalAccessException {
    if (setDisplayIdMethod == null) return;
    setDisplayIdMethod.invoke(inputEvent, displayId);
  }

  public static boolean injectInputEvent(InputEvent inputEvent, int mode) throws InvocationTargetException, IllegalAccessException {
    if (!initialized) {
      throw new IllegalStateException("InputManager must be initialized before injecting events.");
    }
    
    Object result = injectInputEventMethod.invoke(manager, inputEvent, mode);
    return result != null && (Boolean) result;
  }
}

