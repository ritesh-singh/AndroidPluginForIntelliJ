/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.screensharing;

import static android.os.Build.VERSION.SDK_INT;

import android.content.ClipData;
import android.os.PersistableBundle;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Facilitates operations with Android clipboard by calling hidden methods of
 * android.content.IClipboard through reflection.
 */
@SuppressWarnings("unused") // Called through JNI.
public class ClipboardAdapter {
  private static final String PACKAGE_NAME = "com.android.shell";
  private static final String ATTRIBUTION_TAG = "ScreenSharing";
  private static final int USER_ID = 0;
  private static final int DEVICE_ID_DEFAULT = 0; // From android.companion.virtual.VirtualDeviceManager

  private static Object clipboard;
  private static Method getPrimaryClipMethod;
  private static Method setPrimaryClipMethod;
  private static Method addPrimaryClipChangedListenerMethod;
  private static Method removePrimaryClipChangedListenerMethod;
  private static ClipboardListener clipboardListener;
  private static int numberOfExtraParameters;
  private static PersistableBundle overlaySuppressor;

  static {
    clipboard = ServiceManager.getServiceAsInterface("clipboard", "android/content/IClipboard", true);

    try {
      if (clipboard != null) {
        Class<?> clipboardClass = clipboard.getClass();
        Method[] methods = clipboardClass.getDeclaredMethods();
        getPrimaryClipMethod = findMethodAndMakeAccessible(methods, "getPrimaryClip");
        setPrimaryClipMethod = findMethodAndMakeAccessible(methods, "setPrimaryClip");
        addPrimaryClipChangedListenerMethod = findMethodAndMakeAccessible(methods, "addPrimaryClipChangedListener");
        removePrimaryClipChangedListenerMethod = findMethodAndMakeAccessible(methods, "removePrimaryClipChangedListener");
        numberOfExtraParameters = getPrimaryClipMethod.getParameterCount() - 1;
        if (numberOfExtraParameters <= 3) {
          clipboardListener = new ClipboardListener();
          if (SDK_INT >= 33) {
            overlaySuppressor = new PersistableBundle(1);
            overlaySuppressor.putBoolean("com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY", true);
          }
        }
        else {
          Log.e("ScreenSharing", "Unexpected number of getPrimaryClip parameters: " + (numberOfExtraParameters + 1));
        }
      }
    }
    catch (NoSuchMethodException e) {
      Log.e("ScreenSharing", e.getMessage());
      clipboard = null;
    }
  }

  public static String getText() throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return "";
    }

    ClipData clipData = numberOfExtraParameters == 0 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME) :
                        numberOfExtraParameters == 1 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME, USER_ID) :
                        numberOfExtraParameters == 2 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID) :
                        numberOfExtraParameters == 3 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT) :
                        null;
    if (clipData == null || clipData.getItemCount() == 0) {
      return "";
    }
    return clipData.getItemAt(0).getText().toString();
  }

  public static void setText(String text) throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return;
    }

    ClipData clipData = ClipData.newPlainText(text, text);
    if (SDK_INT >= 33) {
      // Suppress clipboard change UI overlay on Android 13+.
      clipData.getDescription().setExtras(overlaySuppressor);
    }

    if (numberOfExtraParameters == 0) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME);
    }
    else if (numberOfExtraParameters == 1) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME, USER_ID);
    }
    else if (numberOfExtraParameters == 2) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID);
    }
    else if (numberOfExtraParameters == 3) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT);
    }
  }

  public static void enablePrimaryClipChangedListener() throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return;
    }

    if (numberOfExtraParameters == 0) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME);
    }
    else if (numberOfExtraParameters == 1) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, USER_ID);
    }
    else if (numberOfExtraParameters == 2) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID);
    }
    else if (numberOfExtraParameters == 3) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT);
    }
  }

  public static void disablePrimaryClipChangedListener() throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return;
    }

    if (numberOfExtraParameters == 0) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME);
    }
    else if (numberOfExtraParameters == 1) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, USER_ID);
    }
    else if (numberOfExtraParameters == 2) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID);
    }
    else if (numberOfExtraParameters == 3) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID,
                                                    DEVICE_ID_DEFAULT);
    }
  }

  private static Method findMethodAndMakeAccessible(Method[] methods, String name) throws NoSuchMethodException {
    for (Method method : methods) {
      if (method.getName().equals(name)) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new NoSuchMethodException(name);
  }
}
