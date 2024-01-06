package androidx.top.hyperos.dynamic.ext;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * @Author 文艺 or literature and art
 * @Date 2023/11/22 14:15
 */
public class NotifyHelper {
    public static Context context;
    private static NotifyHelper instance;
    private static NotifyListener notifyListener;

    public static NotifyHelper getInstance() {
        if (instance == null) {
            instance = new NotifyHelper();
        }
        return instance;
    }

    public static Class<?> findNotificationListener() {
        return XposedInfo.getClass(XposedInfo.getLoad(), Config.NotificationListenerPackage);
    }

    public static Class<?> findDaggerReferenceGlobalRootComponent() {
        return XposedInfo.getClass(XposedInfo.getLoad(), Config.DaggerReferenceGlobalRootComponentPackage);
    }

    private static void initContext() {
        XposedHelpers.findAndHookMethod(
                findDaggerReferenceGlobalRootComponent(),
                "displayIdInteger",

                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        context = (Context) XposedHelpers.getObjectField(param.thisObject, "context");
                    }
                });
    }

    public static void initNotificationPosted() {
        initContext();

        XposedHelpers.findAndHookMethod(
                findNotificationListener(),
                "onNotificationPosted",
                StatusBarNotification.class,
                NotificationListenerService.RankingMap.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        StatusBarNotification sbn = (StatusBarNotification) param.args[0];
                        NotificationListenerService.RankingMap rankingMap = (NotificationListenerService.RankingMap) param.args[1];

                        switch (sbn.getPackageName()) {
                            case Config.qq:
                                Notification notification = sbn.getNotification();
                                if (notification != null) {
                                    // 获取通知的图标
                                    Icon notificationIcon = notification.getLargeIcon();
                                    //Icon notificationIcon = notification.getSmallIcon();
                                    CharSequence text = notification.tickerText;
                                    /*
                                    XposedBridge.log(text.toString());

                                    if (notificationIcon != null && text != null) {
                                        new HyperContentToast(context).showTextToast(notificationIcon, text.toString());
                                    }

                                     */
                                }
                                break;
                            default:
                                break;
                        }
                    }
                });
    }

    public static void init() {
        initNotificationPosted();
    }
}
