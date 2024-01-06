package androidx.top.hyperos.dynamic.ext;

import androidx.top.hyperos.dynamic.BuildConfig;

public class Config {
    public static final int READ_REQUEST_CODE = 100;
    public static final int REQUEST_MANAGE_FILES_ACCESS = READ_REQUEST_CODE + 1;
    public static final int MENU_IMPORT = 200;
    public static final int MENU_HIDE_ICON = MENU_IMPORT + 1;
    public static int black = 0xff000000;
    public static int white = 0xffffffff;
    public static int versionCodeIceCreamSandwich = 34;
    public static final String qq = "com.tencent.mobileqq";
    public static final String wx = "com.tencent.mm";
    public static String AppPackage = BuildConfig.APPLICATION_ID;
    public static String SharedPreferencesPath = Tools.concat("/data/user/0/", AppPackage, "/shared_prefs/data.xml");
    public static String SystemUiPackage = "com.android.systemui";
    public static String ToastPackage = Tools.concat(SystemUiPackage, ".toast");
    public static String StrongToastPackage = Tools.concat(ToastPackage, ".MIUIStrongToast");
    public static String StrongToastModelPackage = Tools.concat(ToastPackage, ".bean.StrongToastModel");
    public static String NotificationListenerPackage = Tools.concat(SystemUiPackage, ".statusbar.notification.MiuiNotificationListener");
    public static String DaggerReferenceGlobalRootComponentPackage = Tools.concat(SystemUiPackage, ".dagger.DaggerReferenceGlobalRootComponent");
    public static String AppDir = "/sdcard/HyperIsle/";
    public static String luaDir = Tools.concat(AppDir, "project/");
    public static String[] luaLabels = new String[] {"label", "app_name", "appname", "title"};
    public static String[] luaThemes = new String[] {"theme", "style"};
}
