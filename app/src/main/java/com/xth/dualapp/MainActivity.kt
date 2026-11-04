package com.xth.dualapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

private val Green = Color(0xFF07C160)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme(colorScheme = lightColorScheme(primary = Green)) {
        Surface(Modifier.fillMaxSize()) {
          Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text("微信分身", style = MaterialTheme.typography.headlineMedium)
            // 同一个应用在主空间和分身空间各有一份，界面按所在空间区分
            if (ProfileSetup.isProfileOwner(this@MainActivity)) ProfileScreen() else ParentScreen()
          }
        }
      }
    }
  }
}

/** 某个空间里的微信实例。 */
private data class Instance(
  val user: UserHandle,
  val title: String,
  val ours: Boolean,      // 是本应用创建、可管理的分身空间
  val hasWeChat: Boolean,
  val paused: Boolean,
)

private fun scan(ctx: Context): List<Instance> {
  val launcher = ctx.getSystemService(LauncherApps::class.java)
  val um = ctx.getSystemService(UserManager::class.java)
  val ourProfiles = ctx.getSystemService(CrossProfileApps::class.java).targetUserProfiles
  val me = Process.myUserHandle()
  return launcher.profiles.map { user ->
    Instance(
      user = user,
      title = when {
        user == me -> "微信 1 · 主空间"
        user in ourProfiles -> "微信 2 · 分身空间"
        else -> "其他空间"
      },
      ours = user in ourProfiles,
      hasWeChat = launcher.getActivityList(WECHAT, user).isNotEmpty(),
      paused = user != me && um.isQuietModeEnabled(user),
    )
  }.sortedBy { it.user != me }
}

private fun launchWeChat(ctx: Context, user: UserHandle) {
  val launcher = ctx.getSystemService(LauncherApps::class.java)
  val activity = launcher.getActivityList(WECHAT, user).firstOrNull() ?: return
  // 分身空间被暂停时，系统会自动弹框询问是否开启
  runCatching { launcher.startMainActivity(activity.componentName, user, null, null) }
    .onFailure { toast(ctx, "启动失败：${it.message}") }
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

@Composable
private fun ParentScreen() {
  val ctx = LocalContext.current
  var refresh by remember { mutableIntStateOf(0) }
  LifecycleResumeEffect(Unit) {
    refresh++
    onPauseOrDispose {}
  }
  val instances = remember(refresh) { scan(ctx) }
  val dpm = ProfileSetup.dpm(ctx)

  val provision = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    if (it.resultCode == android.app.Activity.RESULT_OK) {
      toast(ctx, "分身空间已创建，桌面上带🧳角标的微信就是第二个")
    } else {
      toast(ctx, "已取消创建")
    }
    refresh++
  }

  instances.forEach { inst ->
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(inst.title, style = MaterialTheme.typography.titleMedium)
        Text(
          when {
            !inst.hasWeChat -> "未安装微信"
            inst.paused -> "空间已暂停（启动时会提示开启）"
            else -> "可用"
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (inst.hasWeChat) {
            Button(onClick = { launchWeChat(ctx, inst.user) }) { Text("打开微信") }
          }
          if (inst.ours) {
            OutlinedButton(onClick = {
              ctx.getSystemService(CrossProfileApps::class.java)
                .startMainActivity(ComponentName(ctx, MainActivity::class.java), inst.user)
            }) { Text("管理分身") }
          }
        }
      }
    }
  }

  if (instances.none { it.ours }) {
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("创建分身空间", style = MaterialTheme.typography.titleMedium)
        Text(
          "使用 Android 系统自带的「工作资料」功能，生成一个独立空间运行第二个微信。" +
            "不修改微信，两个账号数据完全隔离。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val allowed = dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        Button(
          enabled = allowed,
          onClick = {
            provision.launch(
              Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, ProfileSetup.admin(ctx))
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true),
            )
          },
        ) { Text("一键创建") }
        if (!allowed) {
          Text(
            "当前设备不允许创建：可能已经有工作资料（例如 Shelter、企业 MDM），或系统阉割了此功能。",
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }

  Tips()
}

@Composable
private fun ProfileScreen() {
  val ctx = LocalContext.current
  var refresh by remember { mutableIntStateOf(0) }
  var confirmDelete by remember { mutableStateOf(false) }
  LifecycleResumeEffect(Unit) {
    refresh++
    onPauseOrDispose {}
  }
  val apkRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val paths = result.data?.getStringArrayExtra(EXTRA_APKS)
    val error = if (paths == null) "主空间没有找到微信" else ProfileSetup.installWeChatFromApk(ctx, paths)
    if (error == null) {
      InstallResultReceiver.installing = true
      toast(ctx, "正在复制微信安装包，约需半分钟…")
    } else {
      toast(ctx, "克隆失败：$error")
    }
  }
  val hasWeChat = remember(refresh, InstallResultReceiver.done) {
    ctx.getSystemService(LauncherApps::class.java).getActivityList(WECHAT, Process.myUserHandle()).isNotEmpty()
  }

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("你现在在分身空间里", style = MaterialTheme.typography.titleMedium)
      Text(if (hasWeChat) "微信 2 已就绪" else "分身里还没有微信")
      if (hasWeChat) {
        Button(onClick = { launchWeChat(ctx, Process.myUserHandle()) }) { Text("打开微信 2") }
      } else {
        Button(
          enabled = !InstallResultReceiver.installing,
          onClick = {
            if (ProfileSetup.installWeChat(ctx)) {
              toast(ctx, "已克隆微信")
            } else {
              // 系统克隆接口被拦截，改为向主空间要安装包自己装；先确保转发规则已就位
              ProfileSetup.apply(ctx)
              runCatching { apkRequest.launch(Intent(ACTION_GET_WECHAT_APKS).addCategory(Intent.CATEGORY_DEFAULT)) }
                .onFailure { toast(ctx, "克隆失败：无法连接主空间（${it.message}）") }
            }
            refresh++
          },
        ) { Text(if (InstallResultReceiver.installing) "正在安装…" else "从主空间克隆微信") }
        Text(
          "也可以在这个空间里直接安装微信官方 APK。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OutlinedButton(onClick = {
        val ready = ProfileSetup.apply(ctx)
        toast(ctx, if (ready) "已重新应用配置" else "已重新应用配置，但微信还没装进分身")
        refresh++
      }) { Text("重新应用配置") }
    }
  }

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("删除分身空间", style = MaterialTheme.typography.titleMedium)
      Text(
        "会删除整个分身空间及其中微信 2 的全部聊天记录，不可恢复。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = { confirmDelete = true },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
      ) { Text("删除") }
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("确定删除分身空间？") },
      text = { Text("微信 2 的聊天记录会一起删除。建议先在微信里备份聊天记录。") },
      confirmButton = {
        TextButton(onClick = { ProfileSetup.dpm(ctx).wipeData(0) }) {
          Text("删除", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
  }
}

@Composable
private fun Tips() {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("使用提示", style = MaterialTheme.typography.titleSmall)
    listOf(
      "桌面或应用抽屉「工作」标签里带🧳角标的微信就是微信 2。",
      "收不到微信 2 的消息时，在系统设置里给它开启自启动和后台运行，并关闭电池优化。",
      "下拉通知栏里的「工作应用」开关可以暂停微信 2（相当于下线）。",
      "两个微信可以互相分享文件；微信 2 发图时可以选主空间的相册。",
    ).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}
