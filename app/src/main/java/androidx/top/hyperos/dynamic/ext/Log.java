package androidx.top.hyperos.dynamic.ext;

import android.nfc.Tag;

import androidx.top.hyperos.dynamic.LuaActivity;

import de.robv.android.xposed.XposedBridge;

/**
 * @Author 文艺 or literature and art
 * @Date 2023/11/21 11:09
 */
public class Log {
    private static String TAG = "HyperIsle";
    public static void e(String tag, String error) {
        try {
            android.util.Log.e(tag, error);
            LuaActivity.sendError(error);
            XposedBridge.log(Tools.concat(tag, error));
        } catch (Exception e) {

        }
    }

    public static void e(String error) {
        e(TAG, error);
    }
}