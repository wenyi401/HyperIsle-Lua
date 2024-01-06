package androidx.top.hyperos.dynamic.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.top.hyperos.dynamic.BuildConfig;
import androidx.top.hyperos.dynamic.LuaActivity;

import de.robv.android.xposed.XSharedPreferences;

public class Tools {
    private static final String TAG = "Tools";

    public static String concat(CharSequence... charSequenceArr) {
        try {
            return TextUtils.concat(charSequenceArr).toString();
        } catch (Throwable th) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence charSequence : charSequenceArr) {
                sb.append(charSequence);
            }
            return sb.toString();
        }
    }

    public static String concat(String[] strArr, String str) {
        StringBuilder sb = new StringBuilder();
        for (String i : strArr) {
            if (sb.length() != 0) {
                sb.append(str);
            }
            sb.append(i);
        }
        return sb.toString();
    }

    public static Context removeContextWrappers(Context context) {
        while (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context;
    }

    public static Context getContext() {
        Context context = LuaActivity.context;
        if (context == null) {
            context = XposedInfo.mContext;
        }
        return context;
    }

    public static int getColor(int i) {
        return getColor(i, -1);
    }

    public static int getColor(int colorId, int colorReturn) {
        try {
            Context context = getContext();
            colorReturn = Build.VERSION.SDK_INT >= 23 ? context.getColor(colorId) : context.getResources().getColor(colorId);
        } catch (Throwable th) {
            Log.e(TAG, th.toString());
        }
        return colorReturn;
    }

    public static String getString(int i) {
        String str = null;
        try {
            Context context = getContext();
            str = Build.VERSION.SDK_INT >= 23 ? context.getString(i) : context.getResources().getString(i);
        } catch (Throwable th) {
            Log.e(TAG, th.toString());
        }
        return str;
    }

    public static Drawable getDrawable(int i) {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                return getContext().getDrawable(i);
            } catch (NoSuchMethodError e) {
                return getContext().getResources().getDrawable(i);
            }
        }
        return getContext().getResources().getDrawable(i);
    }

    public static ViewGroup.MarginLayoutParams getLayoutMargin(ViewGroup.LayoutParams params) {
        ViewGroup.MarginLayoutParams margin = new ViewGroup.MarginLayoutParams(params);
        return margin;
    }

    public static int dp(int i) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, i, getContext().getResources().getDisplayMetrics());
    }

    public static int dp(Context context, int i) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, i, context.getResources().getDisplayMetrics());
    }

    public static int px(float i) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (i * scale + 0.5f);

    }

    public static GradientDrawable getShepeBackground(int color, float radiu) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiu);
        return drawable;
    }

    public static GradientDrawable getShepeBackground(int color, float[] radiu) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadii(radiu);
        return drawable;
    }

    public static XSharedPreferences getSharedPreferences(String key) {
        XSharedPreferences sp = new XSharedPreferences(BuildConfig.APPLICATION_ID, key);
        sp.makeWorldReadable();
        return sp;
    }


    public static WindowManager.LayoutParams getWindowParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, 2017, 0x01000338, -3);
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN;
        //params.setTitle("HyperContentToastView");
        params.gravity = Gravity.CENTER | Gravity.TOP;
        return params;
    }

    public static GradientDrawable getLineBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(px(5f));
        drawable.setStroke(px(1f), color);
        return drawable;
    }

    public static int getWidth(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(WindowManager.class);
        return windowManager.getMaximumWindowMetrics().getBounds().width();
    }

    public static int getHeight(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(WindowManager.class);
        return windowManager.getMaximumWindowMetrics().getBounds().height();
    }

}
