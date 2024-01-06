package androidx.top.hyperos.dynamic.ext;

import android.app.NotificationChannel;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * @Author 文艺 or literature and art @Date 2023/11/22 14:51
 */
public interface NotifyListener {
    void onListenerConnected();

    void onNotificationChannelModified(String str, UserHandle userHandle, NotificationChannel notificationChannel, int i);

    void onNotificationPosted(StatusBarNotification statusBarNotification, NotificationListenerService.RankingMap rankingMap);

    void onNotificationRankingUpdate(NotificationListenerService.RankingMap rankingMap);

    void onNotificationRemoved(StatusBarNotification statusBarNotification, NotificationListenerService.RankingMap rankingMap, int i);
}
