# 微信分身

利用 Android 工作资料（Work Profile / Managed Profile）创建一个独立的分身空间，在同一台手机上同时运行两个微信。

- 不修改、不重打包微信，分身空间里运行的是系统克隆的原版微信
- 一键创建 / 删除分身空间，从主空间克隆微信
- 要求 Android 9（API 28）及以上

## 构建

```sh
./gradlew assembleDebug
```

输出位于 `app/build/outputs/apk/debug/`。需要在 `local.properties` 中配置 `sdk.dir`。

## 注意

删除分身空间会一并清除「微信 2」的全部数据，请先备份聊天记录。

## 许可证

[MIT](LICENSE)
