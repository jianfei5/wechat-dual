package com.xth.dualapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.CrossProfileApps

/**
 * 借共享剪贴板在主空间和分身两份应用之间传微信安装包路径。
 * HyperOS 不认 DPC 加的跨资料 intent filter，而 Profile Owner 又无权申请 INTERACT_ACROSS_PROFILES，
 * 剩下可用的只有 CrossProfileApps.startMainActivity（不能带数据）和默认跨空间互通的剪贴板。
 *
 * 流程：分身写入 [REQUEST] 并打开主空间 → 主空间写入 [APKS]+路径并打开分身 → 分身读到路径后安装。
 * 剪贴板只有拿到焦点的前台应用能读，所以由 [MainActivity.onWindowFocusChanged] 调 [handle]。
 */
object ClipboardBridge {
  private const val REQUEST = "dualapp:request-wechat-apks"
  private const val APKS = "dualapp:wechat-apks"

  /** 分身调用：请主空间发来微信安装包路径。返回失败原因，null 表示已发出。 */
  fun request(ctx: Context): String? {
    put(ctx, REQUEST)
    return openOtherSide(ctx)
  }

  /** 读剪贴板里的请求或回应并处理；两份应用都在拿到焦点时调用。 */
  fun handle(activity: Activity) {
    val text = clipboard(activity).primaryClip?.takeIf { it.itemCount > 0 }
      ?.getItemAt(0)?.text?.toString() ?: return
    val inProfile = ProfileSetup.isProfileOwner(activity)
    when {
      text == REQUEST && !inProfile -> {
        // 空列表表示主空间没装微信
        val paths = ProfileSetup.weChatApkPaths(activity).orEmpty()
        put(activity, (listOf(APKS) + paths).joinToString("\n"))
        openOtherSide(activity)?.let { InstallResultReceiver.error = "无法回到分身：$it" }
      }
      text.startsWith(APKS) && inProfile -> {
        clipboard(activity).clearPrimaryClip()
        val paths = text.lines().drop(1).filter { it.isNotBlank() }.toTypedArray()
        val failure = if (paths.isEmpty()) "主空间没有找到微信" else ProfileSetup.installWeChatFromApk(activity, paths)
        if (failure == null) {
          InstallResultReceiver.installing = true
          InstallResultReceiver.toast(activity, "正在复制微信安装包，约需半分钟…")
        } else {
          InstallResultReceiver.error = "克隆失败：$failure"
        }
      }
    }
  }

  private fun openOtherSide(ctx: Context): String? = runCatching {
    val cpa = ctx.getSystemService(CrossProfileApps::class.java)
    val other = cpa.targetUserProfiles.firstOrNull() ?: return "找不到另一个空间里的本应用"
    cpa.startMainActivity(ComponentName(ctx, MainActivity::class.java), other)
    null
  }.getOrElse { it.toString() }

  private fun put(ctx: Context, text: String) =
    clipboard(ctx).setPrimaryClip(ClipData.newPlainText("微信分身", text))

  private fun clipboard(ctx: Context) = ctx.getSystemService(ClipboardManager::class.java)
}
