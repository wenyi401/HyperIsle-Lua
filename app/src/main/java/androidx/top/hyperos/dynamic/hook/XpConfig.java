package androidx.top.hyperos.dynamic.hook;

import androidx.top.hyperos.dynamic.ext.Config;
import androidx.top.hyperos.dynamic.ext.Tools;

public class XpConfig {
    public static String luaDir = Tools.concat(Config.AppDir, "hook/");
    public static String SystemUiPackage = "com.android.systemui";
    public static String ToastPackage = Tools.concat(SystemUiPackage, ".toast");
    public static String StrongToastPackage = Tools.concat(ToastPackage, ".MIUIStrongToast");
    public static String StrongToastModelPackage = Tools.concat(ToastPackage, ".bean.StrongToastModel");
    public static String NotificationListenerPackage = Tools.concat(SystemUiPackage, ".statusbar.notification.MiuiNotificationListener");
    public static String DaggerReferenceGlobalRootComponentPackage = Tools.concat(SystemUiPackage, ".dagger.DaggerReferenceGlobalRootComponent");

}
