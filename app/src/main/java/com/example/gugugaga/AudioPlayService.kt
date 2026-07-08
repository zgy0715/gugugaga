package com.example.gugugaga

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * gugugaga —— 后台音效播放服务
 *
 * # 职责
 *
 * 无界面、无窗口，完全在后台执行：
 * 1. 触发 100ms 短震动（触感反馈）
 * 2. 随机播放 `res/raw` 中的一段音频
 * 3. 播放完成后自动调用 stopSelf() 销毁
 *
 * # 生命周期
 *
 * ```
 * onStartCommand
 *   ├─ triggerVibration()     // 100ms 震动
 *   └─ playRandomAudio()      // 随机播放 → onCompletion/onError → stopSelf() → onDestroy
 * ```
 *
 * # 设计意图
 *
 * 将原先与 Activity 生命周期绑定的播放逻辑剥离到 Service 中，
 * 使 MainActivity 只需极快启动 Service 并立即 finish，
 * 用户完全感知不到任何界面「进入—退出」的过程。
 *
 * @see MainActivity 启动本服务的唯一入口
 */
class AudioPlayService : Service() {

    /** MediaPlayer 实例，在 onDestroy 中释放 */
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "Gugugaga"
        /** 震动时长（毫秒） */
        private const val VIBRATION_DURATION_MS = 100L
    }

    // ═══════════════════════ 服务入口 ═══════════════════════

    /**
     * Service 启动回调。
     *
     * 执行顺序：先震动 → 再播放音频（两者互不依赖，可并行）。
     * 返回 `START_NOT_STICKY`：服务被系统杀死后不会自动重建，
     * 避免意外重启播放。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AudioPlayService 启动")

        // 1. 触发震动
        triggerVibration()

        // 2. 随机播放音频
        playRandomAudio()

        return START_NOT_STICKY
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
     * 震动权限 `android.permission.VIBRATE` 为普通权限，安装时自动授予。
     */
    private fun triggerVibration() {
        // ── 步骤1：获取 Vibrator 实例 ──
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+：VibratorManager 是推荐方式
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // ── 步骤2：触发震动 ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    VIBRATION_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_DURATION_MS)
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
     *    - `onCompletion` → stopSelf()  // 正常播完就停止服务
     *    - `onError`     → stopSelf()  // 出错也停止服务
     * 5. 调用 `start()` 开始播放
     *
     * # 异常安全
     *
     * - raw 目录为空 → 日志警告 → 直接 stopSelf
     * - MediaPlayer.create 返回 null（资源损坏）→ 日志错误 → 直接 stopSelf
     * - 播放中途出错 → onErrorListener 捕获 → 日志 + stopSelf
     */
    private fun playRandomAudio() {
        // ── 步骤1：通过反射获取 R.raw 中所有资源字段 ──
        val rawFields = R.raw::class.java.fields

        // 空目录保护
        if (rawFields.isEmpty()) {
            Log.w(TAG, "res/raw 目录为空，没有找到任何音频文件")
            stopSelf()
            return
        }

        // ── 步骤2：随机选择一个音频资源 ──
        val selectedField = rawFields.random()
        val audioResId = selectedField.getInt(null)
        Log.d(TAG, "正在播放: ${selectedField.name} (resId=$audioResId)")

        // ── 步骤3：创建 MediaPlayer 实例 ──
        mediaPlayer = MediaPlayer.create(this, audioResId)

        // 资源加载失败
        if (mediaPlayer == null) {
            Log.e(TAG, "音频文件创建失败，资源可能已损坏: ${selectedField.name}")
            stopSelf()
            return
        }

        // ── 步骤4：配置回调并开始播放 ──
        mediaPlayer?.apply {
            // 播放完成 → 停止服务
            setOnCompletionListener {
                Log.d(TAG, "播放完成: ${selectedField.name}，停止服务")
                stopSelf()
            }

            // 播放出错 → 记录错误后停止服务
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "播放出错: ${selectedField.name} | what=$what | extra=$extra")
                stopSelf()
                true
            }

            // 开始播放
            start()
        }
    }

    // ═══════════════════════ 生命周期清理 ═══════════════════════

    /**
     * 服务销毁时释放 MediaPlayer 的 native 资源。
     *
     * 释放步骤：
     * 1. 如果还在播放则 stop()
     * 2. release() 释放 native 资源
     * 3. mediaPlayer = null 切断引用
     */
    override fun onDestroy() {
        Log.d(TAG, "AudioPlayService 销毁")
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        super.onDestroy()
    }

    // ═══════════════════════ 绑定模式 ═══════════════════════

    /**
     * 本服务不需要绑定模式，返回 null 即可。
     * 通过 `startService()` / `stopSelf()` 控制生命周期。
     */
    override fun onBind(intent: Intent?): IBinder? = null
}
