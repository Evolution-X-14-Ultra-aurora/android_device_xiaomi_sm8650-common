/*
 * Copyright (C) 2023-2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Display.HdrCapabilities;
import vendor.xiaomi.hw.touchfeature.ITouchFeature;

import com.xiaomi.settings.display.ColorModeService;
import com.xiaomi.settings.edgesuppression.EdgeSuppressionService;
import com.xiaomi.settings.touch.TouchOrientationService;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "XiaomiParts";
    private static final boolean DEBUG = true;
    private static final int DOUBLE_TAP_TO_WAKE_MODE = 14;

    private ITouchFeature xiaomiTouchFeatureAidl;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (DEBUG) Log.i(TAG, "Received intent: " + intent.getAction());
        switch (intent.getAction()) {
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                onLockedBootCompleted(context);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
                onBootCompleted(context);
                break;
        }
    }

    /**
     * Handles actions to perform after a locked boot is completed.
     * This method is now non-static, allowing access to instance methods.
     */
    private void onLockedBootCompleted(Context context) {
        // Start ColorModeService
        context.startServiceAsUser(new Intent(context, ColorModeService.class),
                UserHandle.CURRENT);

        // Override HDR types to enable Dolby Vision
        final DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        if (displayManager != null) {
            displayManager.overrideHdrTypes(Display.DEFAULT_DISPLAY,
                    new int[] {
                        HdrCapabilities.HDR_TYPE_DOLBY_VISION,
                        HdrCapabilities.HDR_TYPE_HDR10,
                        HdrCapabilities.HDR_TYPE_HLG,
                        HdrCapabilities.HDR_TYPE_HDR10_PLUS
                    });
        } else {
            Log.e(TAG, "DisplayManager service not available");
        }

        // Create and register ContentObserver for DOUBLE_TAP_TO_WAKE setting
        ContentObserver observer = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateTapToWakeStatus(context);
            }
        };
        // Touchscreen
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOUBLE_TAP_TO_WAKE), true, observer);

        // Start EdgeSuppressionService and TouchOrientationService
        context.startServiceAsUser(new Intent(context, EdgeSuppressionService.class),
                UserHandle.CURRENT);
        context.startServiceAsUser(new Intent(context, TouchOrientationService.class),
                UserHandle.CURRENT);

        // Update Tap to Wake status initially
        updateTapToWakeStatus(context);
    }

    /**
     * Updates the Tap to Wake status based on the system settings.
     * This is an instance method and can access instance variables.
     */
    private void updateTapToWakeStatus(Context context) {
        try {
            if (xiaomiTouchFeatureAidl == null) {
                try {
                    String name = "default";
                    String fqName = ITouchFeature.DESCRIPTOR + "/" + name;
                    IBinder binder = android.os.Binder.allowBlocking(
                            android.os.ServiceManager.waitForDeclaredService(fqName));
                    xiaomiTouchFeatureAidl = ITouchFeature.Stub.asInterface(binder);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to initialize Touch Feature service", e);
                    return; // Exit early if initialization fails
                }
            }

            boolean enabled = Settings.Secure.getInt(
                                      context.getContentResolver(),
                                      Settings.Secure.DOUBLE_TAP_TO_WAKE, 0) == 1;

            if (xiaomiTouchFeatureAidl != null) {
                xiaomiTouchFeatureAidl.setTouchMode(0, DOUBLE_TAP_TO_WAKE_MODE, enabled ? 1 : 0);
                if (DEBUG) {
                    Log.i(TAG, "Tap to Wake set to " + (enabled ? "enabled" : "disabled"));
                }
            } else {
                Log.e(TAG, "Touch Feature AIDL interface is not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update Tap to Wake status", e);
        }
    }

    /**
     * Handles actions to perform after a standard boot is completed.
     * This method is now non-static.
     */
    private void onBootCompleted(Context context) {
    }
}
