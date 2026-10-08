# South+

**自用软件。** 个人使用的小工具，不是通用产品，也不提供任何技术支持或保证。

一个南+（South Plus）论坛的 Android 客户端，用 WebView 壳 + 原生增强的方式实现，
自带用户脚本（油猴）运行环境。

---

## 功能

**浏览**
- 默认电脑版页面 + 桌面 Chrome UA，与论坛站点保持一致
- 内置 10 个站点域名（北+/魂+/南+/白+/Lv+/夏+/春+/雪+/东+/蓝+），
  启动时并行探测，自动切到能直连的那个；也可在设置或右上角菜单里手动指定
- 分层浏览：首页 → 版块/分类 → 帖子，返回键语义与浏览器一致，列表不会丢页
- 「版块」标签页是原生列表，187 个版块按分组展示，支持实时筛选
- 图片查看器：双指缩放、双击放大、放大后拖动、缩略态左右滑动切换、保存到相册

**用户脚本**
- 内置用户脚本运行环境，等价于 Tampermonkey 的核心能力
- 支持 `GM.getValue/setValue/deleteValue/listValues/notification/openInTab/registerMenuCommand/addStyle`
  以及旧式 `GM_*` 全局函数
- 支持 `@match` / `@include` / `@exclude` 与 `@run-at` 的
  `document-start`、`document-end`、`document-idle`
- 在 document-start 注入，时序与油猴一致
- 安装方式：一键更新「凛+」、本地导入 `.user.js`、网页里点 `.user.js` 链接
- 下载走多源并行竞速（国内镜像优先，官方兜底），自动记住可用的源

**阅读体验**
- 夜间配色、无图模式、广告清理
- 长按图片：全屏查看 / 复制图片地址 / 保存图片 / 用浏览器打开
- 长按链接：复制链接地址 / 用浏览器打开；长按文字用系统原生的选择复制
- 站内站外链接默认都在应用内打开，可在设置里分别关掉

## 构建

需要 JDK 21、Android SDK（compileSdk 36 / build-tools 36.0.0）与 Gradle 9.x。

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
gradle :app:assembleRelease
```

签名需要工程根目录下的 `keystore.properties`（**未包含在本仓库中**）：

```properties
storeFile=keystore/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

没有这个文件时 `assembleRelease` 会失败，可以先用 `assembleDebug`。

## 离线测试

不装模拟器也能跑，全部离线：

```bash
# Java 侧：URL 映射 / 站点切换 / 脚本引擎 / 下载源候选
cd urltest
javac -encoding UTF-8 -d out -sourcepath ".;../app/src/main/java" \
      android/net/Uri.java TestMain.java UserscriptTest.java \
      UserscriptSourcesTest.java SiteTest.java
java -cp out TestMain          # 78 条
java -cp out SiteTest          # 54 条
java -cp out UserscriptSourcesTest   # 45 条
java -cp out UserscriptTest    # 33 条

# JS 侧：注入脚本与用户脚本宿主
cd ../tools
node extract_js.py && node --check np_inject.js
node np_test.js    # 16 条
node host_test.js  # 23 条
```

`urltest/android/net/Uri.java` 是个最小替身，用来把工程里的原文件直接编译到 PC 上跑。

## 说明

- 本软件不对应任何官方客户端，与论坛运营方无关。
- 用户脚本以网页权限运行，可读写页面内容与登录状态；只安装你信任来源的脚本。
- Cookie 按域名保存，切换站点域名后需要重新登录。
- 仓库中不包含签名密钥。

## 许可

个人自用项目，未附许可协议。请勿用于任何商业用途。
