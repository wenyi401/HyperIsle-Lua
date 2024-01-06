package androidx.top.hyperos.dynamic.util;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class StatusBarUtil {
    private Activity mActivity;
    private Window mWindow;
    public StatusBarUtil(Activity activity) {
        this.mActivity = activity;
        this.mWindow = activity.getWindow();
    }

    public static StatusBarUtil with(Activity activity) {
        return new StatusBarUtil(activity);
    }

    public StatusBarUtil setTransparentStatusBar() {
        this.mWindow.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        this.mWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        View decorView = this.mWindow.getDecorView();
        int systemUiVisibility = decorView.getWindowSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(systemUiVisibility);
        return this;
    }

    public StatusBarUtil setStatusBarTextColor(Boolean isLight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = this.mWindow.getDecorView();
            int systemUiVisibility = decorView.getWindowSystemUiVisibility();
            if (isLight) {
                systemUiVisibility = systemUiVisibility & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                systemUiVisibility = systemUiVisibility | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decorView.setSystemUiVisibility(systemUiVisibility);
        }
        setStatusBarColor(Color.TRANSPARENT);
        return this;
    }

    public StatusBarUtil setStatusBarColor(int color) {
        this.mWindow.setStatusBarColor(color);
        return this;
    }

    public StatusBarUtil setNavigationBarColor(int color) {
        this.mWindow.setStatusBarColor(color);
        return this;
    }

    public StatusBarUtil setNavigationBarBtnColor(Boolean isLight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decorView = this.mWindow.getDecorView();
            int systemUiVisibility = decorView.getWindowSystemUiVisibility();
            if (isLight) {
                systemUiVisibility = systemUiVisibility & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                systemUiVisibility = systemUiVisibility | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decorView.setSystemUiVisibility(systemUiVisibility);
        }
        return this;
    }


    public StatusBarUtil setTransparentNavigationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.mWindow.setNavigationBarContrastEnforced(false);
        }
        this.mWindow.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        this.mWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        View decorView = this.mWindow.getDecorView();
        int systemUiVisibility = decorView.getWindowSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(systemUiVisibility);
        setNavigationBarColor(Color.TRANSPARENT);
        return this;
    }


}
