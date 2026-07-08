package com.example.gugugaga

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * gugugaga —— 极简音效播放器（启动触发器）
 *
 * # 核心行为
 *
 * ```
 * 用户点击桌面图标
 *   → Activity.onCreate()
 *       └─ startService(AudioPlayService::class.java)  // 启动后台服务
 *   → Activity.finishAndRemoveTask()                    // 无痕退出
 * ```
 *
 * # 为什么这次完全没有「进入界面」的感觉了
 *
 * 1. **使用 `Activity` 而非 `AppCompatActivity`**：
 *    `AppCompatActivity` 在 `super.onCreate()` 中必须创建 AppCompat 窗口装饰，
 *    而纯 `Activity` 配合 `@android:style/Theme.NoDisplay`
 *    可以跳过窗口创建过程，Activity 在系统层面就不被分配窗口。
 *
 * 2. **`finishAndRemoveTask()` 替代 `finish()`**：
 *    `finish()` 只是关闭 Activity，但任务栈仍存在；`finishAndRemoveTask()`
 *    直接把整个任务从系统移除，多任务列表里完全看不到残留。
 *
 * 3. **`overridePendingTransition(0, 0)`**：
 *    抑制所有窗口进出动画，避免任何过渡闪烁。
 *
 * # 完整流程
 *
 * ```
 * 点图标 → onCreate 启动 Service → finishAndRemoveTask → Service 后台播完 → stopSelf
 *  │                                   │
 *  │ 无窗口，无动画                    │ 从多任务列表彻底移除
 *  └←──── 全程不到 1ms ──────────────→│
 * ```
 *
 * @see AudioPlayService 实际执行震动 + 播放的后台服务
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动后台播放服务（震动 + 随机播放音频）
        startService(Intent(this, AudioPlayService::class.java))

        // 无痕退出：关闭 Activity + 移除整个任务 + 抑制动画
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }
}
