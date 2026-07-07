package com.example.gugugaga

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * gugugaga —— 极简音效播放器
 *
 * # 核心行为
 *
 * ```
 * 用户点击桌面图标
 *   → onCreate()
 *       ├─ triggerVibration()      // 100ms 短震，触感反馈
 *       └─ playRandomAudio()       // 随机播放 res/raw 中的音频
 *           ├─ 成功 → 播放完成 → finish()
 *           └─ 失败 → 打印日志 → finish()
 *   → onDestroy() 释放 MediaPlayer
 *   → 回到桌面
 * ```
 *
 * # 为什么看不到界面
 *
 * 1. 透明主题 `Theme.Gugugaga.Transparent`：
 *    - windowIsTranslucent = true      → 窗口透明
 *    - windowBackground = transparent  → 背景透明
 *    - windowDisablePreview = true     → 禁止启动预览闪屏
 *    - windowAnimationStyle = @null    → 无窗口动画
 *    - windowFullscreen = true         → 全屏
 *
 * 2. 不调用 `setContentView()`：没有加载任何布局文件。
 *
 * 3. 主题继承 `Theme.AppCompat.NoActionBar`：连 ActionBar 都没有。
 *
 * # 资源自动发现
 *
 * 通过反射获取 `R.raw` 中所有资源 ID，因此增减 `res/raw/` 中的音频文件无需修改代码。
 * `R.raw` 是编译时由 Android 构建系统自动生成的资源索引类。
 *
 * # Activity 生命周期
 *
 * ```
 * onCreate → onStart → onResume → [播放中] → finish → onPause → onStop → onDestroy
 * ```
 *
 * 整个可见生命周期（onResume → onPause）就是音频播放的时长。
 *
 * @see R.raw R类中的raw资源，编译时自动生成
 */
class MainActivity : AppCompatActivity() {

    /** MediaPlayer 实例 —— 必须在 onDestroy 中释放，否则内存泄漏 */
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Activity 创建入口。
     *
     * 不调用 setContentView —— 整个 APP 没有界面。
     * 执行顺序：先震动，再播放（顺序不影响体验，两者异步）。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⚠️ 不调用 setContentView —— 无 UI

        // 1️⃣ 触发 100ms 短震动
        triggerVibration()

        // 2️⃣ 随机播放一段音频（播完自动 finish）
        playRandomAudio()
    }

    // ═══════════════════════ 震动模块 ═══════════════════════

    /**
     * 触发 100ms 短震动，模拟「按下即响」的触感反馈。
     *
     * # 兼容性策略（三层 fallback）
     *
     * | API 级别 | 获取震动器 | 触发方式 |
     * |---------|-----------|---------|
     * | 31+ (Android 12) | VibratorManager.defaultVibrator | VibrationEffect |
     * | 26–30 | Vibrator (deprecated) | VibrationEffect |
     * | 24–25 | Vibrator (deprecated) | vibrate(millis) |
     *
     * 手机不需要震动权限（`android.permission.VIBRATE`），
     * 该权限在 AndroidManifest 中声明即可，属于普通权限自动授予。
     */
    private fun triggerVibration() {
        // ── 步骤1：获取 Vibrator 实例 ──
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+：推荐使用 VibratorManager 获取默认震动器
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            // Android 8–11：直接通过 Context 获取 Vibrator 服务
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // ── 步骤2：触发震动 ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+：使用 VibrationEffect 精确控制震动波形
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    100L,                             // 震动时长（毫秒）
                    VibrationEffect.DEFAULT_AMPLITUDE // 使用系统默认振幅
                )
            )
        } else {
            // Android 7.x 及以下：直接调 vibrate(long)（已废弃但兼容）
            @Suppress("DEPRECATION")
            vibrator.vibrate(100L)
        }
    }

    // ═══════════════════════ 音频播放模块 ═══════════════════════

    /**
     * 从 `res/raw` 中**随机**选取一个音频文件并播放。
     *
     * # 实现原理
     *
     * 1. 通过 Java 反射获取 `R.raw` 类中所有字段（每个字段 = 一个音频文件）
     * 2. 随机选一个字段，通过 `Field.getInt(null)` 获取其资源 ID
     * 3. 调用 `MediaPlayer.create(context, resId)` 创建播放器
     * 4. 注册回调：
     *    - `onCompletion` → finish()  // 正常播完就退出
     *    - `onError`     → finish()  // 出错也退出（不会闪退）
     * 5. 调用 `start()` 开始播放
     *
     * # 为什么用反射而不是手动列举
     *
     * 如果写死资源 ID：
     * ```kotlin
     * val audioList = listOf(R.raw.a, R.raw.b, R.raw.c)
     * ```
     * 每次增减音频文件都需要改代码重新编译。用反射自动发现，
     * 丢文件进 res/raw 即可，无需碰代码。
     *
     * # 异常安全
     *
     * - raw 目录为空 → 日志警告 → 直接 finish
     * - MediaPlayer.create 返回 null（资源损坏）→ 日志错误 → 直接 finish
     * - 播放中途出错 → onErrorListener 捕获 → 日志 + finish
     */
    private fun playRandomAudio() {
        // ── 步骤1：通过反射获取 R.raw 中所有资源字段 ──
        // R.raw 是编译时生成的静态类，每个 raw 资源对应一个 public static int 字段
        val rawFields = R.raw::class.java.fields

        // 空目录保护：res/raw 中没有任何文件时直接退出
        if (rawFields.isEmpty()) {
            Log.w(TAG, "res/raw 目录为空，没有找到任何音频文件")
            finish()
            return
        }

        // ── 步骤2：随机选择一个音频资源 ──
        val selectedField = rawFields.random()
        val audioResId = selectedField.getInt(null) // 从静态字段读取 int 值（资源 ID）
        Log.d(TAG, "正在播放: ${selectedField.name} (resId=$audioResId)")

        // ── 步骤3：创建 MediaPlayer 实例 ──
        // MediaPlayer.create() 内部会完成 prepare()，返回就绪的播放器
        // 如果资源格式不支持（如损坏的 mp3），返回 null
        mediaPlayer = MediaPlayer.create(this, audioResId)

        // 资源加载失败：日志报错，直接退出
        if (mediaPlayer == null) {
            Log.e(TAG, "音频文件创建失败，资源可能已损坏: ${selectedField.name}")
            finish()
            return
        }

        // ── 步骤4：配置回调并开始播放 ──
        mediaPlayer?.apply {
            // ✅ 播放完成 → 关闭 Activity
            setOnCompletionListener { mp ->
                Log.d(TAG, "播放完成: ${selectedField.name}，关闭 Activity")
                finish()
            }

            // ❌ 播放出错 → 记录错误信息后关闭（静默处理，不会闪退崩溃）
            setOnErrorListener { _, what, extra ->
                Log.e(
                    TAG,
                    "播放出错: ${selectedField.name} | what=$what | extra=$extra | " +
                    "MEDIA_ERROR_UNKNOWN=${MediaPlayer.MEDIA_ERROR_UNKNOWN} | " +
                    "MEDIA_ERROR_SERVER_DIED=${MediaPlayer.MEDIA_ERROR_SERVER_DIED}"
                )
                // 返回 true = 错误已处理，MediaPlayer 不会往上抛异常
                finish()
                true
            }

            // ▶️ 开始播放
            start()
        }
    }

    // ═══════════════════════ 生命周期清理 ═══════════════════════

    /**
     * Activity 销毁回调 —— 释放 MediaPlayer 的 native 资源。
     *
     * # 为什么必须手动释放
     *
     * MediaPlayer 底层持有 native 的内存缓冲区和解码器实例，
     * 这些资源不受 JVM GC 管理。如果不在 onDestroy 中调用 release()，
     * 每次播放都会泄漏一块 native 内存，反复启动会导致 OOM。
     *
     * # 释放步骤
     *
     * 1. isPlaying → stop()    // 如果还在播，先停止
     * 2. release()             // 释放 native 资源
     * 3. mediaPlayer = null    // 切断引用，帮助 GC
     */
    override fun onDestroy() {
        mediaPlayer?.apply {
            // 如果播放尚未结束（用户可能手动划掉应用），先停止播放
            if (isPlaying) {
                stop()
            }
            // 释放原生资源：音频缓冲区、解码器、文件句柄等
            release()
        }
        // 切断 Java 层引用，让 GC 可以回收 MediaPlayer 的 Java 外壳对象
        mediaPlayer = null
        super.onDestroy()
    }

    companion object {
        /** Logcat 过滤标签，方便调试时筛选：`adb logcat -s Gugugaga:D` */
        private const val TAG = "Gugugaga"
    }
}
