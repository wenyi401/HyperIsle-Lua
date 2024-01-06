package androidx.top.hyperos.dynamic.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Http {

    public static HashMap makeRequest(String url) {
        return  request(url ,null, null);
    }

    public static HashMap makeRequest(String url, HashMap headers, String data) {
        return request(url ,headers, data);
    }

    private static void addHeaders(HashMap<String, String> headers, HttpURLConnection httpURLConnection) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            httpURLConnection.addRequestProperty(key, value);
        }
    }

    private static HashMap<String, HashMap<Integer, String>> getHeaders(HttpURLConnection conn) {
        HashMap<String, HashMap<Integer, String>> headers = new HashMap<>();
        Map<String, List<String>> map = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                key = "null";
            }

            List<String> headerValues = entry.getValue();
            HashMap<Integer, String> values = new HashMap<>();
            int i = 1;
            for (String value : headerValues) {
                values.put(i, value);
                i++;
            }
            headers.put(key, values);
        }
        return headers;
    }

    private static HashMap<String, Object> request(String url, HashMap<String, String> headers, String data) {
        InputStream inputStream;
        try {
            url = URLEncoder.encode(url, "UTF-8");
            HashMap<String, Object> table = new HashMap<>();
            HttpURLConnection content = (HttpURLConnection) new URL(url).openConnection();
            if (headers != null) {
                addHeaders(headers, content);
            } else {
                content.addRequestProperty(url, data);
            }
            if (data != null) {
                content.setDoOutput(true);
                content.setRequestMethod("POST");
                int length = data.length();
                if (content.getRequestProperty("Content-Length") == null) {
                    content.addRequestProperty("Content-Length", Integer.toString(length));
                }

                if (content.getRequestProperty("Content-Type") == null) {
                    content.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                }

                content.connect();
                OutputStream output = content.getOutputStream();
                output.write(data.getBytes(), 0, length);
                output.flush();
            } else {
                content.connect();
            }
            table.put("url", url);
            table.put("requestMethod", content.getRequestMethod());
            table.put("code", content.getResponseCode());
            table.put("message", content.getResponseMessage());
            table.put("headers", getHeaders(content));
            table.put("contentEncoding", content.getContentEncoding());
            table.put("contentLength", content.getContentLength());
            table.put("contentType", content.getContentType());
            table.put("date", content.getDate());
            table.put("expiration", content.getExpiration());
            table.put("lastModified", content.getLastModified());
            table.put("usingProxy", content.usingProxy() ? true : false);
            try {
                inputStream = content.getInputStream();
            } catch (IOException e) {
                inputStream = content.getErrorStream();
                table.put("error", true);
            }

            BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            byte[] arr = new byte[8192];
            while (true) {
                int read = bufferedInput.read(arr);
                if (read == -1) {
                    table.put("content", byteArray.toString());
                    content.disconnect();
                    return table;
                }
                byteArray.write(arr, 0, read);
            }
        } catch (Exception e) {
            HashMap<String, Object> table = new HashMap<>();
            table.put("error", e.toString());
            return table;
        }
    }

}
