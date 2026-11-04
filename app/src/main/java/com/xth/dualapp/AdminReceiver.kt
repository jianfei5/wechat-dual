package com.xth.dualapp

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
  // Android 7 及以前只会走这里；8+ 也会收到，ProfileSetup.apply 可重复调用
  override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
    ProfileSetup.apply(context)
  }
}
