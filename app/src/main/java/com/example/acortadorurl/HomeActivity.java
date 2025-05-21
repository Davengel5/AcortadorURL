package com.example.acortadorurl;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.app.ProgressDialog;

public class HomeActivity extends AppCompatActivity {
    private EditText etUrl;
    private Button btnShorten, btnCopy, btnOpen, btnLogout;
    private TextView tvShortUrl, tvUrlCount;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isPremium = false;
    private Button btnUpgrade, btnHistory, btnDeleteAccount;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String PREMIUM_STATUS_KEY = "isPremium";
    private static final String PREMIUM_DATE_KEY = "premiumDate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        etUrl = findViewById(R.id.etUrl);
        btnShorten = findViewById(R.id.btnShorten);
        btnCopy = findViewById(R.id.btnCopy);
        btnOpen = findViewById(R.id.btnOpen);
        btnLogout = findViewById(R.id.btnLogout);
        tvShortUrl = findViewById(R.id.tvShortUrl);
        tvUrlCount = findViewById(R.id.tvUrlCount);
        btnUpgrade = findViewById(R.id.btnUpgrade);
        btnHistory = findViewById(R.id.btnHistory);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        btnShorten.setOnClickListener(v -> shortenUrl());
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnOpen.setOnClickListener(v -> openInBrowser());
        btnLogout.setOnClickListener(v -> signOut());
        btnUpgrade.setOnClickListener(v -> showPremiumUpgrade());
        btnHistory.setOnClickListener(v -> loadUrlHistory());
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountConfirmation());

        btnCopy.setVisibility(View.GONE);
        btnOpen.setVisibility(View.GONE);
        btnUpgrade.setVisibility(View.GONE);

        checkUserStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkUserStatus();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                updateUrlCount(user.getEmail());
                Toast.makeText(this, "¡Felicidades! Ahora eres Premium", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkUserStatus() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            verifyPremiumStatus(user.getEmail());
            updateUrlCount(user.getEmail());
        }
    }

    private void verifyPremiumStatus(String email) {
        boolean cachedPremium = sharedPreferences.getBoolean(PREMIUM_STATUS_KEY, false);
        if (cachedPremium) {
            updatePremiumUI(true, Integer.MAX_VALUE);
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("email", email);

                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/get_premium_status.php")
                        .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                        .build();

                Response response = new OkHttpClient().newCall(request).execute();
                String responseData = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseData);

                boolean isPremium = jsonResponse.getBoolean("is_premium");
                String premiumDate = jsonResponse.optString("fecha_upgrade", "");

                sharedPreferences.edit()
                        .putBoolean(PREMIUM_STATUS_KEY, isPremium)
                        .putString(PREMIUM_DATE_KEY, premiumDate)
                        .apply();

                runOnUiThread(() -> {
                    updatePremiumUI(isPremium, isPremium ? Integer.MAX_VALUE : getRemainingAttempts());
                });

            } catch (Exception e) {
                Log.e("PremiumCheck", "Error", e);
                boolean fallbackPremium = sharedPreferences.getBoolean(PREMIUM_STATUS_KEY, false);
                runOnUiThread(() -> {
                    updatePremiumUI(fallbackPremium, fallbackPremium ? Integer.MAX_VALUE : getRemainingAttempts());
                });
            }
        }).start();
    }
    private int getRemainingAttempts() {
        SharedPreferences prefs = getSharedPreferences("UrlPrefs", MODE_PRIVATE);
        int attempts = prefs.getInt("remaining_attempts", -1);

        if (attempts == -1 || isPremium) {
            return isPremium ? Integer.MAX_VALUE : 5;
        }

        return attempts;
    }

    private void updateAttemptsCache(int attempts) {
        getSharedPreferences("UrlPrefs", MODE_PRIVATE)
                .edit()
                .putInt("remaining_attempts", attempts)
                .apply();
    }

    private void updateUrlCount(String userEmail) {
        UrlDatabase.getInstance(this).getRemainingAttempts(userEmail, new UrlDatabase.AttemptsCallback() {
            @Override
            public void onSuccess(int remainingAttempts, boolean premiumStatus) {
                runOnUiThread(() -> {
                    isPremium = premiumStatus;
                    String countText = isPremium ? "Intentos: ∞ (Premium)" : "Intentos: " + remainingAttempts;
                    tvUrlCount.setText(countText);

                    Log.d("PREMIUM_DEBUG", "Premium status: " + premiumStatus);
                    Log.d("PREMIUM_DEBUG", "Botón upgrade visible: " + !premiumStatus);

                    btnUpgrade.setVisibility(premiumStatus ? View.GONE : View.VISIBLE);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnUpgrade.setVisibility(View.VISIBLE);
                    Log.e("PREMIUM_ERROR", message);
                });
            }
        });
    }

    private void shortenUrl() {
        String originalUrl = etUrl.getText().toString().trim();

        if (originalUrl.isEmpty()) {
            etUrl.setError("Ingresa una URL");
            return;
        }

        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "http://" + originalUrl;
        }

        btnShorten.setEnabled(false);
        btnShorten.setText("Acortando...");

        FirebaseUser user = mAuth.getCurrentUser();
        String userEmail = (user != null && user.getEmail() != null) ? user.getEmail() : "anonimo";

        UrlDatabase.getInstance(this).shortenUrl(originalUrl, userEmail, new UrlDatabase.UrlCallback() {
            @Override
            public void onSuccess(String shortUrl, int remainingAttempts, boolean premiumStatus) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");
                    tvShortUrl.setText(shortUrl);
                    tvShortUrl.setVisibility(View.VISIBLE);
                    btnCopy.setVisibility(View.VISIBLE);
                    btnOpen.setVisibility(View.VISIBLE);

                    isPremium = premiumStatus;
                    String countText = isPremium ? "Intentos: ∞ (Premium)" : "Intentos: " + remainingAttempts;
                    tvUrlCount.setText(countText);

                    Toast.makeText(HomeActivity.this, "URL acortada creada", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");

                    if (message.contains("Límite de intentos")) {
                        tvUrlCount.setText("Límite alcanzado");
                        btnShorten.setEnabled(false);
                    }

                    Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void copyToClipboard() {
        String shortUrl = tvShortUrl.getText().toString();
        if (!shortUrl.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("URL acortada", shortUrl);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "URL copiada al portapapeles", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInBrowser() {
        String shortUrl = tvShortUrl.getText().toString();
        if (!shortUrl.isEmpty()) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(shortUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void signOut() {
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
    private void showPremiumUpgrade() {
        Intent intent = new Intent(this, PremiumActivity.class);
        startActivityForResult(intent, 1);
    }
    private void updatePremiumUI(boolean isPremium, int remainingAttempts) {
        runOnUiThread(() -> {
            btnShorten.setEnabled(true);
            String countText;
            if (isPremium) {
                countText = "★ Cuenta Premium ★";
            } else {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null && !user.isAnonymous()) {
                    countText = remainingAttempts == Integer.MAX_VALUE ?
                            "★ Cuenta Premium ★" : // Caso de seguridad
                            "Intentos: " + remainingAttempts;
                } else {
                    countText = "Inicia sesión para ver intentos";
                }
            }
            tvUrlCount.setText(countText);

            boolean shouldShowUpgradeButton = !isPremium &&
                    mAuth.getCurrentUser() != null &&
                    !mAuth.getCurrentUser().isAnonymous();

            btnUpgrade.setVisibility(shouldShowUpgradeButton ? View.VISIBLE : View.GONE);

            this.isPremium = isPremium;

            Log.d("PremiumUI", "Estado actualizado - Premium: " + isPremium +
                    ", Intentos: " + remainingAttempts +
                    ", Botón visible: " + (btnUpgrade.getVisibility() == View.VISIBLE));
        });
    }
    private void loadUrlHistory() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            runOnUiThread(() -> Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show());
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("email", user.getEmail());

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/historial.php")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseData);

                if (jsonResponse.getBoolean("success")) {
                    JSONArray urls = jsonResponse.getJSONArray("data");
                    List<Map<String, String>> urlList = new ArrayList<>();

                    for (int i = 0; i < urls.length(); i++) {
                        JSONObject urlItem = urls.getJSONObject(i);
                        Map<String, String> urlMap = new HashMap<>();
                        urlMap.put("url", urlItem.getString("url"));
                        urlMap.put("slug", urlItem.getString("slug"));
                        urlMap.put("fecha", urlItem.getString("created_at"));
                        urlList.add(urlMap);
                    }

                    runOnUiThread(() -> showHistoryDialog(urlList));
                } else {
                    String errorMsg = jsonResponse.optString("error", "Error desconocido");
                    runOnUiThread(() -> {
                        Toast.makeText(
                                HomeActivity.this,
                                "Error al cargar historial: " + errorMsg,
                                Toast.LENGTH_LONG
                        ).show();
                        Log.e("API_ERROR", errorMsg);
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            HomeActivity.this,
                            "Excepción: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                    Log.e("HISTORY_ERROR", "Error completo:", e);
                });
            }
        }).start();
    }

    private void showHistoryDialog(List<Map<String, String>> urls) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tu historial de URLs");

        View view = getLayoutInflater().inflate(R.layout.dialog_history, null);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerHistory);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        HistorialAdapter adapter = new HistorialAdapter(urls);

        adapter.setOnItemDeletedListener(new HistorialAdapter.OnItemDeletedListener() {
            @Override
            public void onItemDeleted() {
                loadUrlHistory();
            }
        });

        recyclerView.setAdapter(adapter);

        builder.setView(view);
        builder.setPositiveButton("Cerrar", null);
        builder.show();
    }
    private void showDeleteAccountConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar cuenta permanentemente")
                .setMessage("¿Estás seguro? Esta acción:\n\n• Eliminará TODAS tus URLs\n• Borrará tu cuenta irreversiblemente\n• No podrás recuperar los datos")
                .setPositiveButton("Eliminar todo", (dialog, which) -> deleteUserAccount())
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUserAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        ProgressDialog progressDialog = ProgressDialog.show(
                this,
                "Eliminando cuenta",
                "Por favor espere...",
                true,
                false
        );

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("email", user.getEmail());

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/delete_user.php")
                        .addHeader("Content-Type", "application/json")
                        .delete(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();

                Log.d("API_RESPONSE", "Respuesta: " + responseData);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    try {
                        if (responseData.trim().startsWith("<!doctype")) {
                            throw new JSONException("El servidor devolvió una página HTML");
                        }

                        JSONObject jsonResponse = new JSONObject(responseData);
                        if (response.isSuccessful() && jsonResponse.getBoolean("success")) {
                            deleteFirebaseUser(user);
                        } else {
                            String errorMsg = jsonResponse.optString("error",
                                    "Código " + response.code() + ": " + response.message());
                            showError(errorMsg);
                        }
                    } catch (JSONException e) {
                        showError("Error en el servidor. Detalles técnicos: " +
                                response.code() + " - " + responseData.substring(0, 50) + "...");
                    }
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showError("Error de conexión: " + e.getMessage());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showError("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void deleteFirebaseUser(FirebaseUser user) {
        user.delete()
                .addOnSuccessListener(aVoid -> {
                    signOut();
                    Toast.makeText(this, "Cuenta eliminada permanentemente", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showError("Error al eliminar cuenta de autenticación: " + e.getMessage());
                });
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("Aceptar", null)
                .show();
    }
}