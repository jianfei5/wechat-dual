package com.xth.dualapp

import android.app.Activity
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.Build
import android.os.Bundle

/** 运行在主空间，无界面：由分身经 CrossProfileApps 拉起，把微信安装包路径发回分身的 [MainActivity]。 */
class WeChatApkActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val cpa = getSystemService(CrossProfileApps::class.java)
    val profile = cpa.targetUserProfiles.firstOrNull()
    if (profile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // 空数组表示主空间没装微信
      val paths = ProfileSetup.weChatApkPaths(this) ?: emptyArray()
      runCatching { cpa.startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_APKS, paths), profile, this) }
    }
    finish()
  }
}
