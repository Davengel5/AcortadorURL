package com.example.acortadorurl;

import android.content.Context;
import android.content.SharedPreferences;
import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;

public class UrlDatabase {
    private static final String BASE_API_URL = "https://apiurl.up.railway.app/api.php";
    private static UrlDatabase instance;
    private OkHttpClient client;
    private Context context;
    private SharedPreferences prefs;

    private UrlDatabase(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient();
        this.prefs = context.getSharedPreferences("UrlPrefs", Context.MODE_PRIVATE);
    }

    public static synchronized UrlDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new UrlDatabase(context);
        }
        return instance;
    }

    public interface UrlCallback {
        void onSuccess(String result);
        void onError(String message);
    }

    public void shortenUrl(String originalUrl, String userId, UrlCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("url", originalUrl);
            json.put("user_id", userId);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_API_URL)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);

                        if (jsonResponse.has("short_url")) {
                            callback.onSuccess(jsonResponse.getString("short_url"));
                        } else {
                            callback.onError(jsonResponse.optString("error", "Error desconocido"));
                        }
                    } catch (Exception e) {
                        callback.onError("Error procesando respuesta");
                    }
                }
            });

        } catch (JSONException e) {
            callback.onError("Error creando petición");
        }
    }

    public void getOriginalUrl(String shortUrl, UrlCallback callback) {
        String shortCode = extractShortCode(shortUrl);

        HttpUrl url = HttpUrl.parse(BASE_API_URL).newBuilder()
                .addQueryParameter("slug", shortCode)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);

                    if (jsonResponse.has("url")) {
                        callback.onSuccess(jsonResponse.getString("url"));
                    } else {
                        callback.onError("URL no encontrada");
                    }
                } catch (Exception e) {
                    callback.onError("Error en la respuesta");
                }
            }
        });
    }

    private String extractShortCode(String shortUrl) {
        String[] parts = shortUrl.split("/");
        return parts[parts.length - 1];
    }

    public void getUserUrls(String userId, UrlListCallback callback) {
        HttpUrl url = HttpUrl.parse(BASE_API_URL).newBuilder()
                .addQueryParameter("user_id", userId)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseData = response.body().string();
                    JSONArray jsonArray = new JSONArray(responseData);
                    callback.onSuccess(jsonArray);
                } catch (Exception e) {
                    callback.onError("Error procesando respuesta");
                }
            }
        });
    }

    public interface UrlListCallback {
        void onSuccess(JSONArray urls);
        void onError(String message);
    }
}