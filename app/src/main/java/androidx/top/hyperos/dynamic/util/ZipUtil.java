package androidx.top.hyperos.dynamic.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtil {
    public static void unzipAndShowDialog(Context context, Uri zipUri, String targetPath) {
        new Thread(() -> {
            File targetDirectory = new File(targetPath);
            targetDirectory.mkdirs(); // 创建目标目录

            try (ZipInputStream zipInputStream = new ZipInputStream(context.getContentResolver().openInputStream(zipUri))) {
                ZipEntry entry = zipInputStream.getNextEntry();
                byte[] buffer = new byte[8192]; // 缓冲区大小
                while (entry != null) {
                    File outFile = new File(targetDirectory, entry.getName());
                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        FileOutputStream fos = new FileOutputStream(outFile);
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        fos.close();
                    }
                    zipInputStream.closeEntry();
                    entry = zipInputStream.getNextEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("ziopzip", e.toString());
            }
        }).start();
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}