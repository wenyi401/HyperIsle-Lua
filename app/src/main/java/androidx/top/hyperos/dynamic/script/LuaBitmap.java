package androidx.top.hyperos.dynamic.script;


import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class LuaBitmap {
    static WeakHashMap<String, WeakReference<Bitmap>> cache = new WeakHashMap<String, WeakReference<Bitmap>>();
    private static HashMap<String, String> sHeader;
    public static void setHeader(HashMap<String,String> header){
        sHeader=header;
    }

    public static void setUserAgent(String userAgent) {
        if (sHeader == null)
            sHeader = new HashMap<>();
        sHeader.put("User-Agent", userAgent);
    }

    public static void setReferer(String referer) {
        if (sHeader == null)
            sHeader = new HashMap<>();
        sHeader.put("Referer", referer);
    }

    public static void setCookie(String cookie) {
        if (sHeader == null)
            sHeader = new HashMap<>();
        sHeader.put("Cookie", cookie);
    }

    private static int l;
    private static long mCacheTime = 7 * 24 * 60 * 60 * 1000;

    public static void setCacheTime(long time) {
        mCacheTime = time;
    }

    public static long getCacheTime() {
        return mCacheTime;
    }

    public static Bitmap getLocalBitmap(String url) throws FileNotFoundException, IOException {
        FileInputStream fis = new FileInputStream(url);
        Bitmap bitmap = BitmapFactory.decodeStream(fis);
        fis.close();
        return bitmap;
    }

    public static Bitmap getLocalBitmap(LuaContext context, String url) {
        return decodeScale(context.getWidth(), new File(url));
    }

    public static Bitmap getHttpBitmap(String url) throws IOException {
        //Log.d(TAG, url);
        URL myFileUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) myFileUrl.openConnection();
        //conn.setConnectTimeout(0);
        conn.setDoInput(true);
        conn.connect();
        InputStream is = conn.getInputStream();
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        is.close();
        return bitmap;
    }

    public static boolean copyFile(InputStream in, OutputStream out) {
        try {
            int byteread = 0;
            byte[] buffer = new byte[1024*1024];
            while ((byteread = in.read(buffer)) != -1) {
                out.write(buffer, 0, byteread);
            }
            //in.close();
            //out.close();
        } catch (Exception e) {
            Log.i("lua", e.getMessage());
            return false;
        }
        return true;
    }
    public static Bitmap getAssetBitmap(Context context, String name) throws IOException {
        AssetManager am = context.getAssets();
        InputStream is = am.open(name);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        is.close();
        return bitmap;
    }

    public static Bitmap getBitmap(LuaContext context, String path) throws IOException {

        WeakReference<Bitmap> wRef = cache.get(path);
        if (wRef != null) {
            Bitmap bt = wRef.get();
            if (bt != null)
                return bt;
        }

        Bitmap bitmap;
        if (path.toLowerCase().startsWith("http://") || path.toLowerCase().startsWith("https://")) {
            bitmap = getHttpBitmap(path);
        } else if (path.charAt(0) != '/') {
            bitmap = BitmapFactory.decodeStream(context.findResource(path));
        } else {
            bitmap = getLocalBitmap(context, path);
        }

        cache.put(path, new WeakReference<Bitmap>(bitmap));
        return bitmap;
    }

    public static Bitmap decodeScale(int IMAGE_MAX_SIZE, File fis) {
        Bitmap b = null;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(fis.getAbsolutePath(), o);
        int scale = 1;
        if (o.outHeight > IMAGE_MAX_SIZE * 4 || o.outWidth > IMAGE_MAX_SIZE) {
            scale = (int) Math.pow(2, (int) Math.round(Math.log(IMAGE_MAX_SIZE / (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
        }
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;

        b = BitmapFactory.decodeFile(fis.getAbsolutePath(), o2);

        return b;
    }

    public static Bitmap getImageFromPath(String filePath) {

        Bitmap bitmap = null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, opts);

        //缩放图片，避免内存不足
        opts.inSampleSize = computeSampleSize(opts, -1, 250 * 250);
        opts.inJustDecodeBounds = false;

        try {
            bitmap = BitmapFactory.decodeFile(filePath, opts);
        } catch (Exception e) {
            // TODO: handle exception
        }
        return bitmap;
    }

    //缩放图片算法
    private static int computeSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels) {
        int initialSize = computeInitialSampleSize(options, minSideLength, maxNumOfPixels);
        int roundedSize;
        if (initialSize <= 8) {
            roundedSize = 1;
            while (roundedSize < initialSize) {
                roundedSize <<= 1;
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8;
        }
        return roundedSize;
    }

    private static int computeInitialSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels) {
        double w = options.outWidth;
        double h = options.outHeight;
        int lowerBound = (maxNumOfPixels == -1) ? 1 : (int) Math.ceil(Math.sqrt(w * h / maxNumOfPixels));
        int upperBound = (minSideLength == -1) ? 128 : (int) Math.min(Math.floor(w / minSideLength), Math.floor(h / minSideLength));
        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound;
        }
        if ((maxNumOfPixels == -1) && (minSideLength == -1)) {
            return 1;
        } else if (minSideLength == -1) {
            return lowerBound;
        } else {
            return upperBound;
        }
    }

    public static Bitmap getBitmapFromFile(File file, int width, int height) {

        BitmapFactory.Options opts = null;
        if (null != file && file.exists()) {

            if (width > 0 && height > 0) {
                opts = new BitmapFactory.Options();
                // 只是返回的是图片的宽和高，并不是返回一个Bitmap对象
                opts.inJustDecodeBounds = true;
                // 信息没有保存在bitmap里面，而是保存在options里面
                BitmapFactory.decodeFile(file.getPath(), opts);
                // 计算图片缩放比例
                final int minSideLength = Math.min(width, height);
                // 缩略图大小为原始图片大小的几分之一。根据业务需求来做。
                opts.inSampleSize = computeSampleSize(opts, minSideLength,
                        width * height);
                // 重新读入图片，注意此时已经把options.inJustDecodeBounds设回false
                opts.inJustDecodeBounds = false;
                // 设置是否深拷贝，与inPurgeable结合使用
                opts.inInputShareable = true;
                // 设置为True时，表示系统内存不足时可以被回 收，设置为False时，表示不能被回收。
                opts.inPurgeable = true;
            }
            try {
                return BitmapFactory.decodeFile(file.getPath(), opts);
            } catch (OutOfMemoryError e) {
                
                    e.printStackTrace();
            }
        }
        return null;
    }

    public static void removeBitmap(Bitmap obj) {
        Set<Map.Entry<String, WeakReference<Bitmap>>> sets = cache.entrySet();
        for (Map.Entry<String, WeakReference<Bitmap>> set : sets) {
            if (obj.equals(set.getValue().get())) {
                cache.remove(set.getKey());
                return;
            }
        }
    }
}
