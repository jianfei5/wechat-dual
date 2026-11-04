package com.xth.dualapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 接收 PackageInstaller 的安装结果。 */
class InstallResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
      // 系统要求用户确认：弹出系统安装界面
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
      PackageInstaller.STATUS_SUCCESS -> report(context, "微信 2 已装好")
      else -> report(
        context, "安装失败（$status）：${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", failed = true,
      )
    }
  }

  companion object {
    /** 正在安装时为 true；每次安装结束 [done] 加一，界面据此刷新；[error] 非空时界面弹窗显示。 */
    var installing by mutableStateOf(false)
    var done by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)

    fun sender(ctx: Context, sessionId: Int): IntentSender = PendingIntent.getBroadcast(
      ctx, sessionId, Intent(ctx, InstallResultReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    ).intentSender

    fun report(ctx: Context, msg: String, failed: Boolean = false) = Handler(Looper.getMainLooper()).post {
      installing = false
      done++
      if (failed) error = msg else toast(ctx, msg)
    }

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
  }
}
