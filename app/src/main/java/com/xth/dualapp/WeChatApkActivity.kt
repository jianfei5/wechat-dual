package com.xth.dualapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** 运行在主空间，无界面：把微信安装包路径回传给分身。 */
class WeChatApkActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val paths = ProfileSetup.weChatApkPaths(this)
    if (paths == null) setResult(RESULT_CANCELED) else setResult(RESULT_OK, Intent().putExtra(EXTRA_APKS, paths))
    finish()
  }
}
