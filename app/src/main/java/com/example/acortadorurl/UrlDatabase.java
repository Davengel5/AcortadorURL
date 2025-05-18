package com.example.acortadorurl;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
        void onSuccess(String shortUrl, int remainingAttempts, boolean premiumStatus);
        void onError(String message);
    }

    public void shortenUrl(String originalUrl, String userEmail, UrlCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("url", originalUrl);
            json.put("user_id", userEmail);

            Log.d("URL_DEBUG", "Enviando a API: " + json.toString());

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(BASE_API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("URL_ERROR", "Error de conexión: " + e.getMessage());
                    callback.onError("Error de conexión");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseData = response.body().string();
                        Log.d("URL_DEBUG", "Respuesta del servidor: " + responseData);

                        JSONObject jsonResponse = new JSONObject(responseData);

                        if (!response.isSuccessful()) {
                            String errorMsg = jsonResponse.optString("error", "Error del servidor");
                            Log.e("URL_ERROR", "Código " + response.code() + ": " + errorMsg);
                            callback.onError(errorMsg);
                            return;
                        }

                        if (jsonResponse.has("short_url")) {
                            int attempts = jsonResponse.optInt("remaining_attempts", -1);
                            boolean premium = jsonResponse.optBoolean("is_premium", false);

                            callback.onSuccess(
                                    jsonResponse.getString("short_url"),
                                    attempts,
                                    premium
                            );
                        } else {
                            Log.e("URL_ERROR", "Respuesta incompleta: " + responseData);
                            callback.onError("Respuesta incompleta del servidor");
                        }
                    } catch (Exception e) {
                        Log.e("URL_ERROR", "Error procesando respuesta: " + e.getMessage());
                        callback.onError("Error procesando respuesta");
                    }
                }
            });
        } catch (JSONException e) {
            Log.e("URL_ERROR", "Error creando JSON: " + e.getMessage());
            callback.onError("Error creando petición");
        }
    }

    public interface OriginalUrlCallback {
        void onSuccess(String originalUrl);
        void onError(String message);
    }

    public void getOriginalUrl(String shortUrl, OriginalUrlCallback callback) {
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

    public interface UserIdCallback {
        void onSuccess(int userId);
        void onError(String message);
    }

    public void getOrCreateUserId(String email, UserIdCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_API_URL + "/get_user_id.php")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Error de conexión: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);

                        if (jsonResponse.has("id")) {
                            callback.onSuccess(jsonResponse.getInt("id"));
                        } else if (jsonResponse.has("error")) {
                            callback.onError(jsonResponse.getString("error"));
                        } else {
                            callback.onError("Formato de respuesta inesperado");
                        }
                    } catch (Exception e) {
                        callback.onError("Error procesando respuesta: " + e.getMessage());
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Error creando petición: " + e.getMessage());
        }
    }
    public interface AttemptsCallback {
        void onSuccess(int remainingAttempts, boolean premiumStatus);
        void onError(String message);
    }
    public void getRemainingAttempts(String userEmail, AttemptsCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", userEmail);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_API_URL + "/get_attempts.php")
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

                        boolean premiumStatus = jsonResponse.optBoolean("is_premium", false);
                        int attempts = premiumStatus ? Integer.MAX_VALUE : jsonResponse.optInt("attempts", 0);

                        callback.onSuccess(attempts, premiumStatus);
                    } catch (Exception e) {
                        callback.onError("Error parsing response");
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Error creating request");
        }
    }
}