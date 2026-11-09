# 微信分身

利用 Android 系统自带的「工作资料」（Work Profile），在同一台手机上同时运行两个微信。

- **不修改微信**：分身里运行的是原版微信，不注入、不重打包，不需要 root
- **数据完全隔离**：两个微信的聊天记录、登录状态、文件各自独立
- **一键创建 / 删除**：自动配置分身空间并把主空间的微信装进去
- **兼容国产系统**：针对小米 HyperOS 等拦截系统接口的 ROM 做了兜底

要求 Android 9（API 28）及以上。

## 下载

到 [Releases](https://github.com/jianfei5/wechat-dual/releases/latest) 下载最新 APK，或直接用仓库 [apk/](apk/) 目录下的安装包。

## 使用

1. 先在主空间装好微信，再安装并打开「微信分身」，点 **一键创建**，按系统提示完成「工作资料」设置。
2. 创建完成后会自动把微信装进分身。若提示分身里还没有微信，点 **管理分身 → 从主空间克隆微信**：
   - 首次可能跳转到「安装未知应用」，打开开关后返回再点一次；
   - 屏幕会在主空间和分身之间跳转一次，属于正常现象；
   - 弹出系统安装页时点 **安装**。
3. 桌面或应用抽屉「工作」标签里带 🧳 角标的微信就是微信 2，登录第二个账号即可。
4. 在系统设置里给微信 2 打开 **自启动**、省电策略设为 **无限制**，否则容易收不到消息。

通知栏的「工作应用」开关可以暂停微信 2（相当于下线）。删除分身：**管理分身 → 删除**。

## 原理

应用以 Device Policy Controller 的身份创建一个托管的工作资料，并成为其 Profile Owner。
Android 会给工作资料独立的用户空间，同一个微信在两个空间里各有一份数据。

把微信装进分身按以下顺序尝试：

1. `DevicePolicyManager.installExistingPackage`：系统原生「克隆」，两个空间共用同一份 APK；
2. 若被 ROM 拦截，就由主空间那份应用查到微信 APK（含 split）的路径，交给分身那份，
   再用 `PackageInstaller` 在分身里装一份。

第 2 步要把路径从主空间传给分身。HyperOS 不执行 DPC 添加的跨资料 intent filter，
Profile Owner 又按系统规定不能申请 `INTERACT_ACROSS_PROFILES`。
所以这里用 `CrossProfileApps.startMainActivity` 在两个空间之间互相拉起，
数据则借默认跨空间互通的剪贴板传递，见 [`ClipboardBridge.kt`](app/src/main/java/com/xth/dualapp/ClipboardBridge.kt)。

| 文件 | 作用 |
| --- | --- |
| `MainActivity.kt` | 界面；按所在空间分别显示主空间页和分身页 |
| `ProfileSetup.kt` | 分身内的配置：限制项、跨资料规则、安装微信 |
| `ClipboardBridge.kt` | 两个空间之间传微信安装包路径 |
| `InstallResultReceiver.kt` | 接收安装结果，在前台弹出系统安装确认页 |
| `ProvisioningActivity.kt` / `AdminReceiver.kt` | 系统创建工作资料流程的回调 |

## 兼容性

| 机型 / 系统 | 结果 |
| --- | --- |
| Xiaomi HyperOS（Android 16） | ✅ 可用（走第 2 步兜底） |

欢迎用 [Issue](https://github.com/jianfei5/wechat-dual/issues/new/choose) 反馈其他机型的结果。
出错时 App 会弹窗显示原因，点「复制」即可粘贴到 Issue 里。

## 已知限制

- **一键创建按钮是灰的**：手机上已有工作资料（如 Shelter、企业 MDM），或系统阉割了该功能。一台手机只能有一个工作资料。
- **跨空间分享在部分 ROM 上无效**：两个微信互相分享文件、微信 2 选主空间相册、调用主空间相机、
  在主空间浏览器打开链接，都依赖跨资料 intent filter，HyperOS 上可能不生效。
- **删除分身会清空微信 2 的全部数据**，请先在微信里备份聊天记录。
- Release 中的 APK 使用 debug 签名，适合自用；从其他来源构建的 APK 签名不同，不能覆盖安装。

## 构建

需要 JDK 17 和 Android SDK（在 `local.properties` 中配置 `sdk.dir`）。

```sh
./gradlew assembleDebug
```

输出位于 `app/build/outputs/apk/debug/`。每次推送都会由 GitHub Actions 自动构建，产物可在 Actions 页面下载。

## 免责声明

本项目与腾讯公司无关。「微信」是腾讯公司的商标。本项目只使用 Android 公开的系统能力，
不修改微信本身；请遵守微信的用户协议，自行承担使用风险。

## 许可证

[MIT](LICENSE)
