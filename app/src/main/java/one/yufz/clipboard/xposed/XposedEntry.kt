package one.yufz.clipboard.xposed

import android.app.AppOpsManager
import android.os.Build
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.yufz.clipboard.BuildConfig
import one.yufz.clipboard.Prefs

class XposedEntry : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "ClipboardWhitelist"
    }

    private val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, Prefs.PREF_NAME)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "android") {
            hookClipboardServices(lpparam)
        }
    }

    private fun hookClipboardServices(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classClipboardService = XposedHelpers.findClass(
            "com.android.server.clipboard.ClipboardService",
            lpparam.classLoader
        )

        // Samsung-specific: suppress clipboard access notification for whitelisted apps
        try {
            XposedHelpers.findAndHookMethod(
                classClipboardService,
                "showAccessNotificationLocked",
                String::class.java,    // callingPackage (str)
                Int::class.java,       // callerUid (i)
                Int::class.java,       // userId (i2)
                XposedHelpers.findClass(
                    "com.android.server.clipboard.PerUserClipboard",
                    lpparam.classLoader
                ), // perUserClipboard
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as String
                        prefs.reload()
                        if (prefs.getBoolean(pkgName, false)) {
                            Log.d(TAG, "Suppressing Samsung clipboard notification for $pkgName")
                            param.result = null // Prevent notification from being shown
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            // Ignore if method doesn't exist
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // OneUI 7 and Samsung devices may change this method signature
            try {
                XposedHelpers.findAndHookMethod(
                    classClipboardService,
                    "clipboardAccessAllowed",
                    Int::class.java, Int::class.java, Int::class.java, Int::class.java,
                    String::class.java, String::class.java, Boolean::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val allowed = clipboardAccessAllowed(
                                XposedHelpers.getObjectField(param.thisObject, "mAppOps") as AppOpsManager,
                                param.args[0] as Int,
                                param.args[4] as String,
                                param.args[1] as Int
                            )
                            if (allowed) {
                                param.result = true
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                // ignore
            }
            try {
                XposedHelpers.findAndHookMethod(
                    classClipboardService,
                    "clipboardAccessAllowed",
                    Int::class.java, String::class.java, String::class.java, Int::class.java,
                    Int::class.java, Int::class.java, Boolean::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val allowed = clipboardAccessAllowed(
                                XposedHelpers.getObjectField(param.thisObject, "mAppOps") as AppOpsManager,
                                param.args[0] as Int,
                                param.args[1] as String,
                                param.args[3] as Int
                            )
                            if (allowed) {
                                param.result = true
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                // ignore
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            XposedHelpers.findAndHookMethod(
                classClipboardService,
                "clipboardAccessAllowed",
                Int::class.java, String::class.java, String::class.java, Int::class.java,
                Int::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val allowed = clipboardAccessAllowed(
                            XposedHelpers.getObjectField(param.thisObject, "mAppOps") as AppOpsManager,
                            param.args[0] as Int,
                            param.args[1] as String,
                            param.args[3] as Int
                        )
                        if (allowed) {
                            param.result = true
                        }
                    }
                }
            )
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            XposedHelpers.findAndHookMethod(
                classClipboardService,
                "clipboardAccessAllowed",
                Int::class.java, String::class.java, Int::class.java, Int::class.java, Boolean::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val allowed = clipboardAccessAllowed(
                            XposedHelpers.getObjectField(param.thisObject, "mAppOps") as AppOpsManager,
                            param.args[0] as Int,
                            param.args[1] as String,
                            param.args[2] as Int
                        )
                        if (allowed) {
                            param.result = true
                        }
                    }
                }
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            XposedHelpers.findAndHookMethod(
                classClipboardService,
                "clipboardAccessAllowed",
                Int::class.java, String::class.java, Int::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val allowed = clipboardAccessAllowed(
                            XposedHelpers.getObjectField(param.thisObject, "mAppOps") as AppOpsManager,
                            param.args[0] as Int,
                            param.args[1] as String,
                            param.args[2] as Int
                        )
                        if (allowed) {
                            param.result = true
                        }
                    }
                }
            )
        }
    }

    private fun clipboardAccessAllowed(
        appOps: AppOpsManager,
        op: Int,
        pkgName: String,
        uid: Int
    ): Boolean {
        try {
            appOps.checkPackage(uid, pkgName)
        } catch (t: Throwable) {
            return false
        }

        prefs.reload()
        if (prefs.getBoolean(pkgName, false)) {
            Log.d(TAG, "pkgName = $pkgName, uid = $uid, op = $op, allowed")
            return true
        }

        return false
    }
}
