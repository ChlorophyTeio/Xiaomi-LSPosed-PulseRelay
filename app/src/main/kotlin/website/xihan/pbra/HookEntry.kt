package website.xihan.pbra

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Bundle
import android.widget.ImageView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

class HookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        XposedHelpers.findAndHookMethod(
            Instrumentation::class.java,
            "callApplicationOnCreate",
            Application::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val app = param.args.firstOrNull() as? Application ?: return
                    val key = lpparam.processName ?: TARGET_PACKAGE
                    if (!initializedProcesses.add(key)) return
                    AppContext.init(app)
                    Diagnostics.processName = key
                    Log.i("Mi Health process started: $key")
                    if (key == TARGET_PACKAGE) {
                        installHeartRateHooks(lpparam.classLoader)
                        installSettingsEntry(lpparam.classLoader)
                        installSportOverlayLifecycle()
                    } else {
                        Log.d("Skip secondary Mi Health process: $key")
                    }
                }
            }
        )
    }

    private fun installHeartRateHooks(loader: ClassLoader) {
        val directTargets = listOf(
            "com.xiaomi.fitness.sport_manager.server.SportDataServer",
            "com.xiaomi.fitness.sport.model.CommonSportModel",
            "com.xiaomi.fitness.sport_eco_manager.server.EcoSportDataServer",
            "com.xiaomi.fitness.sport_eco.model.CommonSportModel"
        )
        directTargets.forEach { className ->
            hookOneArgAfter(loader, className, "onPhoneDataChanged") { arg ->
                val bpm = Reflect.intField(arg, "heart_rate")
                    ?: Reflect.intField(arg, "gym_hr")
                    ?: return@hookOneArgAfter
                HeartRateBridge.offer(bpm, className.substringAfterLast('.'))
            }
        }

        hookOneArgAfter(loader, "com.xiaomi.fitness.sport.viewmodel.BaseSportVM", "onSuccess") { sportingData ->
            val phoneSportData = Reflect.fieldOrNull(sportingData, "phoneSportData")
            val direct = phoneSportData?.let { Reflect.intField(it, "heart_rate") }
            if (direct != null && direct in 20..260) {
                HeartRateBridge.offer(direct, "BaseSportVM.phoneSportData")
                return@hookOneArgAfter
            }

            val list = Reflect.fieldOrNull(sportingData, "list") as? Iterable<*> ?: return@hookOneArgAfter
            for (item in list) {
                item ?: continue
                val desc = Reflect.stringField(item, "dataDes").orEmpty()
                if (desc.contains("心率", ignoreCase = true) || desc.contains("heart", ignoreCase = true)) {
                    val bpm = Reflect.stringField(item, "data")?.toIntOrNull()
                        ?: Reflect.intField(item, "data")
                        ?: continue
                    HeartRateBridge.offer(bpm, "BaseSportVM.list")
                    break
                }
            }
        }

        // 被动非运动心率 fallback：不再主动触发设备同步，避免无关逻辑和额外耗电。
        hookOneArgAfter(loader, "com.xiaomi.fit.fitness.export.data.aggregation.DailyHrReport", "setLatestHrRecord") { record ->
            val bpm = Reflect.intField(record, "hr") ?: return@hookOneArgAfter
            val time = Reflect.longField(record, "time") ?: 0L
            val nowSeconds = System.currentTimeMillis() / 1000
            if (time == 0L || time >= nowSeconds - 120) {
                HeartRateBridge.offer(bpm, "DailyHrReport")
            }
        }

        Log.i("Heart-rate hooks installed: ${Diagnostics.hookInstalled.get()}")
        if (Diagnostics.hookInstalled.get() == 0) {
            Log.e("未找到任何心率 Hook 点；Mi Health 版本可能不兼容")
        }
    }

    private fun hookOneArgAfter(
        loader: ClassLoader,
        className: String,
        methodName: String,
        callback: (Any) -> Unit
    ) {
        val clazz = Reflect.classOrNull(className, loader) ?: run {
            Log.d("Hook target missing: $className")
            return
        }
        val methods = clazz.declaredMethods.filter { it.name == methodName && it.parameterTypes.size == 1 }
        if (methods.isEmpty()) {
            Log.w("Hook method missing: $className#$methodName(1)")
            return
        }
        methods.forEach { method ->
            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val arg = param.args.firstOrNull() ?: return
                        runCatching { callback(arg) }.onFailure { Log.e("Hook callback error $className#$methodName: $it") }
                    }
                })
                Diagnostics.hookInstalled.incrementAndGet()
                Log.d("Hooked: $className#${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }.onFailure { Log.e("Hook install failed $className#$methodName: $it") }
        }
    }

    private fun installSportOverlayLifecycle() {
        runCatching {
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java,
                "callActivityOnResume",
                Activity::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.args.firstOrNull() as? Activity ?: return
                        if (SportOverlay.isSportingActivity(activity)) SportOverlay.sync(activity)
                    }
                }
            )
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java,
                "callActivityOnPause",
                Activity::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val activity = param.args.firstOrNull() as? Activity ?: return
                        if (SportOverlay.isSportingActivity(activity)) SportOverlay.detach(activity)
                    }
                }
            )
            Log.i("Sport activity quick-entry hooks installed")
        }.onFailure { Log.e("Sport overlay lifecycle hook failed: $it") }
    }

    private fun installSettingsEntry(loader: ClassLoader) {
        val clazz = Reflect.classOrNull("com.xiaomi.fitness.about.AboutActivity", loader) ?: return
        val methods = clazz.declaredMethods.filter {
            it.name == "onCreate" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Bundle::class.java
        }
        methods.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    activity.window?.decorView?.postDelayed({
                        installLongPress(activity, param.thisObject)
                    }, 500L)
                }
            })
        }
    }

    private fun installLongPress(activity: Activity, aboutObject: Any) {
        val binding = Reflect.fieldOrNull(aboutObject, "mBinding") ?: return
        val images = mutableListOf<ImageView>()
        var clazz: Class<*>? = binding.javaClass
        while (clazz != null) {
            clazz.declaredFields.forEach { field ->
                if (ImageView::class.java.isAssignableFrom(field.type)) {
                    runCatching {
                        field.isAccessible = true
                        (field.get(binding) as? ImageView)?.let(images::add)
                    }
                }
            }
            clazz = clazz.superclass
        }
        val uniqueImages = images.distinct()
        uniqueImages.forEach { image ->
            image.setOnLongClickListener {
                ConfigDialog.show(activity)
                true
            }
        }
        if (uniqueImages.isEmpty()) {
            activity.window?.decorView?.setOnLongClickListener {
                ConfigDialog.show(activity)
                true
            }
            Log.w("未找到 About 页图标，设置入口回退为长按页面空白区域")
        } else {
            Log.d("Settings entry installed on ${uniqueImages.size} image view(s)")
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.mi.health"
        private val initializedProcesses = ConcurrentHashMap.newKeySet<String>()
    }
}
