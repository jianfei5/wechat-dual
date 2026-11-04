package com.xth.dualapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.provider.MediaStore

const val WECHAT = "com.tencent.mm"

/** 工作资料内的配置；只有本应用是 Profile Owner（即运行在分身空间里）时才生效，可重复调用。 */
object ProfileSetup {

  fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

  fun dpm(ctx: Context): DevicePolicyManager = ctx.getSystemService(DevicePolicyManager::class.java)

  fun isProfileOwner(ctx: Context) = dpm(ctx).isProfileOwnerApp(ctx.packageName)

  /** 返回微信是否已在分身空间里就绪。 */
  fun apply(ctx: Context): Boolean {
    if (!isProfileOwner(ctx)) return false
    val dpm = dpm(ctx)
    val admin = admin(ctx)

    dpm.setProfileName(admin, ctx.getString(R.string.app_name))
    // 主空间没装微信时，允许在分身里直接装 APK
    dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
    // 分身里的来电/通讯录信息在主空间可见
    runCatching { dpm.setCrossProfileCallerIdDisabled(admin, false) }
    addCrossProfileFilters(dpm, admin)
    dpm.setProfileEnabled(admin)

    return installWeChat(ctx)
  }

  /** 把主空间已安装的微信"复制"进分身空间（共用同一份 APK，数据各自独立）。 */
  fun installWeChat(ctx: Context): Boolean =
    runCatching { dpm(ctx).installExistingPackage(admin(ctx), WECHAT) }.getOrDefault(false)

  private fun addCrossProfileFilters(dpm: DevicePolicyManager, admin: ComponentName) {
    val both = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT or
      DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
    val toParent = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT

    fun filter(vararg actions: String, mime: String? = null) = IntentFilter().apply {
      actions.forEach(::addAction)
      addCategory(Intent.CATEGORY_DEFAULT)
      addCategory(Intent.CATEGORY_OPENABLE)
      if (mime != null) addDataType(mime)
    }

    // 两个微信之间互相分享图片、文件
    dpm.addCrossProfileIntentFilter(admin, filter(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, mime = "*/*"), both)
    // 分身微信发图时能选主空间的相册和文件
    dpm.addCrossProfileIntentFilter(
      admin, filter(Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_PICK, mime = "*/*"), toParent,
    )
    // 分身微信拍照/录像用主空间的相机
    dpm.addCrossProfileIntentFilter(
      admin, filter(MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_VIDEO_CAPTURE), toParent,
    )
    // 分身里点链接用主空间的浏览器打开
    dpm.addCrossProfileIntentFilter(admin, IntentFilter(Intent.ACTION_VIEW).apply {
      addCategory(Intent.CATEGORY_DEFAULT)
      addCategory(Intent.CATEGORY_BROWSABLE)
      addDataScheme("http")
      addDataScheme("https")
    }, toParent)
  }
}
