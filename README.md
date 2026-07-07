# gugugaga 🐧

> 极简 Android 音效播放器 —— 点一下图标，响一声就溜，绝不多停留一秒。

## 功能

- 点击桌面图标，随机播放一段音频
- 同时触发 100ms 短震动，增强触感反馈
- 播放完毕自动退出，**全程无可见界面**
- 支持热更新音频：增减 `res/raw` 中文件无需改代码

## 效果演示

```
用户点击图标
   ↓
震动 100ms  +  随机播放音频
   ↓
播放结束 → 自动关闭（回到桌面）
```

全程不出现任何 Activity 界面 —— 点了就响，响完就消失。

## 实现原理

### 1. 透明主题 —— 彻底消灭 UI

```
app/src/main/res/values/themes.xml
```

整个 Activity 继承 `Theme.AppCompat.NoActionBar`，关键属性：

| 属性 | 作用 |
|------|------|
| `windowIsTranslucent` | 窗口透明，不遮挡桌面 |
| `windowBackground` | 背景透明 |
| `windowDisablePreview` | 禁止启动预览窗口（避免白屏闪现） |
| `windowAnimationStyle` | 移除窗口动画 |
| `windowFullscreen` | 全屏，无状态栏/导航栏 |
| `windowNoTitle` | 无标题栏 |
| `backgroundDimEnabled` | 不显示背景遮罩 |

### 2. 播放流程

```
MainActivity.onCreate()
    ├─ triggerVibration()     ← 100ms 短震
    └─ playRandomAudio()      ← 随机选一个音频播放
           ↓
       MediaPlayer 播放
           ↓
       setOnCompletionListener  →  finish() 关闭自己
       setOnErrorListener       →  finish() 静默退出
```

### 3. 资源发现 —— 反射自动扫描

```kotlin
val rawFields = R.raw::class.java.fields  // 获取 res/raw 中的所有文件
val selectedField = rawFields.random()     // 随机选一个
val audioResId = selectedField.getInt(null) // 获取资源 ID
```

**好处**：你在 `res/raw/` 里增加或删除音频文件，完全不需要改任何代码。

### 4. Activity 配置 —— 防止重复播放

```xml
launchMode="singleInstance"      ← 防止快速连点同时播多个
excludeFromRecents="true"        ← 不在最近任务中出现
finishOnTaskLaunch="true"        ← 从桌面启动时关闭旧实例
stateNotNeeded="true"            ← 不保存状态
```

### 5. 震动兼容

```kotlin
// Android 12+ (API 31+)：VibratorManager 获取震动器
// Android 8+  (API 26+)：VibrationEffect 控制波形
// Android 8 以下：直接 vibrator.vibrate(millis)
```

兼容 API 24～36+，三层 fallback。

## 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 2.1.20 |
| AGP | 8.7.3 |
| Gradle | 8.10.2 |
| compileSdk | 36 |
| minSdk | 24 |
| AppCompat | 1.6.1 |

## 项目结构

```
gugugaga/
├── app/
│   ├── build.gradle.kts              # 应用级构建配置
│   └── src/main/
│       ├── AndroidManifest.xml       # 清单（权限、Activity 配置）
│       ├── java/.../MainActivity.kt  # 唯一代码文件（~150行）
│       └── res/
│           ├── raw/                  # 音频文件（.mp3）
│           ├── values/themes.xml     # 透明主题 + 默认主题
│           ├── values/colors.xml     # 调色板
│           ├── values/strings.xml    # 字符串
│           └── mipmap-*/             # 应用图标（多密度）
├── build.gradle.kts                 # 根构建配置
├── gradle.properties                # Gradle 全局配置
├── settings.gradle.kts              # 项目设置
└── README.md                        # 本文件
```

## 构建 & 运行

### 前置条件

- JDK 17+
- Android SDK（platform 36）
- Android Studio 或命令行 Gradle

### 编译

```bash
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 安装到模拟器

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.gugugaga/.MainActivity
```

## 自定义

### 添加/删除音频

直接往 `app/src/main/res/raw/` 里塞 `.mp3` 或 `.ogg` 文件即可，App 启动时自动发现。文件名不限，格式不限（Android 支持的都行）。

### 修改震动时长

在 `MainActivity.kt` 中找到 `100L`（第 63 行），改成你想要的毫秒数。

### 换个图标

把你想用的图片缩放到以下尺寸，覆盖 `mipmap-*` 目录下的 `ic_launcher.png` 和 `ic_launcher_round.png`：

| 密度 | 尺寸 |
|------|------|
| mdpi | 108×108 |
| hdpi | 162×162 |
| xhdpi | 216×216 |
| xxhdpi | 324×324 |
| xxxhdpi | 432×432 |

## 协议

MIT License —— 随便用，随便改。
