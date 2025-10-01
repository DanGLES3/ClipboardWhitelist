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
        val classClipboardService = XposedHelpers.findClass("com.android.server.clipboard.ClipboardService", lpparam.classLoader)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // OneUI 7 and Samsung devices may change this method signature
            // Example signatures:
            // public final boolean clipboardAccessAllowed(
            //     int op,
            //     int uid,
            //     int userId,
            //     int intendingDeviceId,
            //     String pkg,
            //     String attributionTag,
            //     boolean shouldNoteOp)
            try {
                XposedHelpers.findAndHookMethod(
                    classClipboardService,
                    "clipboardAccessAllowed",
                    Int::class.java, Int::class.java, Int::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java,
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
            // Another possible Samsung signature:
            // private boolean clipboardAccessAllowed(
            //     int op,
            //     String callingPackage,
            //     String attributionTag,
            //     int uid,
            //     @UserIdInt int userId,
            //     int intendingDeviceId,
            //     boolean shouldNoteOp)
            try {
                XposedHelpers.findAndHookMethod(
                    classClipboardService,
                    "clipboardAccessAllowed",
                    Int::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java, Int::class.java, Boolean::class.java,
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
            // Android 13+ (API 33+): 6-parameter signature
            // boolean clipboardAccessAllowed(int op, String callingPackage, String attributionTag, int uid, @UserIdInt int userId, int intendingDeviceId)
            XposedHelpers.findAndHookMethod(
                classClipboardService,
                "clipboardAccessAllowed",
                Int::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java, Int::class.java,
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
            // Android 12 (API 31): 5-parameter and 4-parameter signatures

            // 5-parameter: clipboardAccessAllowed(int op, String callingPackage, int uid, int userId, boolean shouldNoteOp)
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
            // Android 10/11 (Q/R): 4-parameter signature
            // boolean clipboardAccessAllowed(int op, String callingPackage, int uid, @UserIdInt int userId)
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

    private fun clipboardAccessAllowed(appOps: AppOpsManager, op: Int, pkgName: String, uid: Int): Boolean {
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
