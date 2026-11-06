# 微信分身

利用 Android 工作资料（Work Profile / Managed Profile）创建一个独立的分身空间，在同一台手机上同时运行两个微信。

- 不修改、不重打包微信，分身空间里运行的是系统克隆的原版微信
- 一键创建 / 删除分身空间，从主空间克隆微信
- 要求 Android 9（API 28）及以上

## 下载

[apk/](apk/) 目录下是可直接安装的 APK。

## 小米 / HyperOS

已在 Xiaomi HyperOS（Android 16）上验证可用。该系统会拦掉系统自带的克隆接口和跨空间 intent 转发，
App 会自动改为：借两个空间共享的剪贴板拿到主空间微信的安装包路径，再在分身里重新安装一份。
首次克隆时需要在分身里允许「安装未知应用」，并在系统安装页点「安装」。

## 构建

```sh
./gradlew assembleDebug
```

输出位于 `app/build/outputs/apk/debug/`。需要在 `local.properties` 中配置 `sdk.dir`。

## 注意

删除分身空间会一并清除「微信 2」的全部数据，请先备份聊天记录。

## 许可证

[MIT](LICENSE)
