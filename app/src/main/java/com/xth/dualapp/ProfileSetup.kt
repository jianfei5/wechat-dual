package com.xth.dualapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.CrossProfileApps
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

const val WECHAT = "com.tencent.mm"

/** 主空间回传给分身的微信安装包路径，见 [WeChatApkActivity]。 */
const val EXTRA_APKS = "apks"

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
    // 允许本应用申请「关联个人应用和工作应用」，用于两个空间之间传数据
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      dpm.setCrossProfilePackages(admin, setOf(ctx.packageName))
    }
    dpm.setProfileEnabled(admin)

    return installWeChat(ctx)
  }

  /** 把主空间已安装的微信"复制"进分身空间（共用同一份 APK，数据各自独立）。 */
  fun installWeChat(ctx: Context): Boolean =
    runCatching { dpm(ctx).installExistingPackage(admin(ctx), WECHAT) }.getOrDefault(false)

  /**
   * 请主空间那份应用把微信安装包路径发回来（经 CrossProfileApps，部分 ROM 不认跨资料 intent filter）。
   * 返回 null 表示请求已发出或已跳转去授权，否则为失败原因。
   */
  fun requestWeChatFromParent(activity: Activity): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "需要 Android 11 及以上"
    val cpa = activity.getSystemService(CrossProfileApps::class.java)
    val parent = cpa.targetUserProfiles.firstOrNull() ?: return "找不到主空间里的本应用"
    if (!cpa.canInteractAcrossProfiles()) {
      if (!cpa.canRequestInteractAcrossProfiles()) return "系统不允许本应用跨空间通信"
      return runCatching {
        activity.startActivity(cpa.createRequestInteractAcrossProfilesIntent())
        Toast.makeText(activity, "请打开「关联」开关，然后返回再点一次", Toast.LENGTH_LONG).show()
        null
      }.getOrElse { "无法打开关联设置：$it" }
    }
    return runCatching {
      cpa.startActivity(Intent(activity, WeChatApkActivity::class.java), parent, activity)
      null
    }.getOrElse { "无法连接主空间：$it" }
  }

  /** 本空间微信的 APK 路径（含 split）；在主空间调用。 */
  fun weChatApkPaths(ctx: Context): Array<String>? = runCatching {
    val info = ctx.packageManager.getApplicationInfo(WECHAT, 0)
    arrayOf(info.sourceDir, *info.splitSourceDirs.orEmpty())
  }.getOrNull()

  /**
   * installExistingPackage 被部分 ROM（如 HyperOS）拦掉时的兜底：
   * 拿主空间微信的 APK 路径，在分身里用 PackageInstaller 重新装一份。
   * 异步执行，结果由 [InstallResultReceiver] 回报；返回 null 表示已开始，否则为失败原因。
   */
  fun installWeChatFromApk(ctx: Context, paths: Array<String>): String? {
    val apks = paths.map(::File)
    if (apks.any { !it.canRead() }) return "无法读取微信安装包"

    val app = ctx.applicationContext
    thread {
      val installer = app.packageManager.packageInstaller
      val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        .apply { setAppPackageName(WECHAT) }
      val id = installer.createSession(params)
      runCatching {
        installer.openSession(id).use { session ->
          apks.forEach { apk ->
            session.openWrite(apk.name, 0, apk.length()).use { out ->
              apk.inputStream().use { it.copyTo(out, 1 shl 20) }
              session.fsync(out)
            }
          }
          session.commit(InstallResultReceiver.sender(app, id))
        }
      }.onFailure {
        installer.abandonSession(id)
        InstallResultReceiver.report(app, "安装失败：${it.message}", failed = true)
      }
    }
    return null
  }

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
