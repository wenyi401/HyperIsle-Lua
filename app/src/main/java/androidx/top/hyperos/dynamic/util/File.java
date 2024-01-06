package androidx.top.hyperos.dynamic.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

public class File {
    private Context mContext;
    private Uri mUri;

    public File(Context context, Uri uri) {
        this.mContext = context;
        this.mUri = uri;
    }

    public Bitmap getBitmap() throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                this.mContext.getContentResolver().openFileDescriptor(this.mUri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    public String getText() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(this.mUri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }

    public void delete() {
        try {
            DocumentsContract.deleteDocument(this.mContext.getContentResolver(), this.mUri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}
