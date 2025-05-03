package com.example.acortadorurl;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

public class UrlDatabase {
    private static UrlDatabase instance;
    private HashMap<String, String> urlMap;          // Mapa de shortCode -> URL original
    private HashMap<String, String> urlUserMap;      // Mapa de shortCode -> UserID
    private HashMap<String, Integer> userUrlCounts;  // Conteo de URLs por usuario
    private SharedPreferences sharedPreferences;

    // Configuración
    private static final String PREFS_NAME = "UrlDatabasePrefs";
    private static final String BASE_SHORT_URL = "misapp://";  // Esquema para deep links
    private static final int SHORT_CODE_LENGTH = 6;
    private static final int MAX_FREE_URLS = 50;      // Límite para usuarios free

    private Random random;
    private Context context;

    // Constructor privado (Singleton)
    private UrlDatabase(Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.urlMap = new HashMap<>();
        this.urlUserMap = new HashMap<>();
        this.userUrlCounts = new HashMap<>();
        this.random = new Random();

        loadFromPreferences();  // Cargar datos guardados
    }

    // Singleton pattern
    public static synchronized UrlDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new UrlDatabase(context);
        }
        return instance;
    }

    /**
     * Acorta una URL y la asocia al usuario actual
     */
    public String shortenUrl(String originalUrl, String userId, boolean isPremium) {
        // Validar URL
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "http://" + originalUrl;
        }

        // Verificar límite para usuarios free
        if (!isPremium && getUserUrlCount(userId) >= MAX_FREE_URLS) {
            return "LIMIT_REACHED";
        }

        // Generar código corto único
        String shortCode;
        do {
            shortCode = generateRandomCode(SHORT_CODE_LENGTH);
        } while (urlMap.containsKey(shortCode));

        // Guardar en memoria
        urlMap.put(shortCode, originalUrl);
        urlUserMap.put(shortCode, userId);
        incrementUserUrlCount(userId);

        // Persistir datos
        saveToPreferences();

        return BASE_SHORT_URL + shortCode;
    }

    /**
     * Obtiene la URL original desde un código corto
     */
    public String getOriginalUrl(String shortCode) {
        return urlMap.get(shortCode);
    }

    /**
     * Elimina una URL acortada (solo si pertenece al usuario)
     */
    public boolean deleteUrl(String shortCode, String userId) {
        if (urlUserMap.containsKey(shortCode) && urlUserMap.get(shortCode).equals(userId)) {
            String user = urlUserMap.get(shortCode);
            urlMap.remove(shortCode);
            urlUserMap.remove(shortCode);
            decrementUserUrlCount(user);
            saveToPreferences();
            return true;
        }
        return false;
    }

    /**
     * Obtiene el conteo de URLs de un usuario
     */
    public int getUserUrlCount(String userId) {
        return userUrlCounts.getOrDefault(userId, 0);
    }

    // ==================== Métodos Privados ====================

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void incrementUserUrlCount(String userId) {
        int count = userUrlCounts.getOrDefault(userId, 0);
        userUrlCounts.put(userId, count + 1);
    }

    private void decrementUserUrlCount(String userId) {
        int count = userUrlCounts.getOrDefault(userId, 0);
        if (count > 0) {
            userUrlCounts.put(userId, count - 1);
        }
    }

    // ==================== Persistencia ====================

    private void saveToPreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        try {
            JSONObject data = new JSONObject();

            // Convertir HashMaps a JSON
            data.put("urlMap", new JSONObject(urlMap));
            data.put("urlUserMap", new JSONObject(urlUserMap));

            JSONObject countsJson = new JSONObject();
            for (String key : userUrlCounts.keySet()) {
                countsJson.put(key, userUrlCounts.get(key));
            }
            data.put("userUrlCounts", countsJson);

            editor.putString("database", data.toString());
            editor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadFromPreferences() {
        String jsonString = sharedPreferences.getString("database", null);
        if (jsonString != null) {
            try {
                JSONObject data = new JSONObject(jsonString);

                // Cargar urlMap
                JSONObject urlMapJson = data.getJSONObject("urlMap");
                Iterator<String> urlMapKeys = urlMapJson.keys();
                while (urlMapKeys.hasNext()) {
                    String key = urlMapKeys.next();
                    urlMap.put(key, urlMapJson.getString(key));
                }

                // Cargar urlUserMap
                JSONObject urlUserMapJson = data.getJSONObject("urlUserMap");
                Iterator<String> urlUserMapKeys = urlUserMapJson.keys();
                while (urlUserMapKeys.hasNext()) {
                    String key = urlUserMapKeys.next();
                    urlUserMap.put(key, urlUserMapJson.getString(key));
                }

                // Cargar userUrlCounts
                JSONObject countsJson = data.getJSONObject("userUrlCounts");
                Iterator<String> countKeys = countsJson.keys();
                while (countKeys.hasNext()) {
                    String key = countKeys.next();
                    userUrlCounts.put(key, countsJson.getInt(key));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}