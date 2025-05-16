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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeActivity extends AppCompatActivity {
    private EditText etUrl;
    private Button btnShorten, btnCopy, btnOpen, btnLogout;
    private TextView tvShortUrl, tvUrlCount;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isPremium = false;
    private Button btnUpgrade, btnHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();

        // Configurar Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Vincular vistas
        etUrl = findViewById(R.id.etUrl);
        btnShorten = findViewById(R.id.btnShorten);
        btnCopy = findViewById(R.id.btnCopy);
        btnOpen = findViewById(R.id.btnOpen);
        btnLogout = findViewById(R.id.btnLogout);
        tvShortUrl = findViewById(R.id.tvShortUrl);
        tvUrlCount = findViewById(R.id.tvUrlCount);
        btnUpgrade = findViewById(R.id.btnUpgrade);
        btnHistory = findViewById(R.id.btnHistory);

        // Configurar listeners
        btnShorten.setOnClickListener(v -> shortenUrl());
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnOpen.setOnClickListener(v -> openInBrowser());
        btnLogout.setOnClickListener(v -> signOut());
        btnUpgrade.setOnClickListener(v -> showPremiumUpgrade());
        //btnHistory.setOnClickListener(v -> loadUrlHistory());
        btnHistory.setOnClickListener(v -> {
            Log.d("HISTORY_DEBUG", "Botón presionado"); // Verifica si llega aquí
            try {
                loadUrlHistory();
            } catch (Exception e) {
                Log.e("HISTORY_ERROR", "Error al cargar historial", e);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });


        // Ocultar botones inicialmente
        btnCopy.setVisibility(View.GONE);
        btnOpen.setVisibility(View.GONE);
        btnUpgrade.setVisibility(View.GONE);

        checkPremiumStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkUserStatus();
        checkPremiumStatus(); // Verificar estado premium al iniciar
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Actualizar estado premium
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
            // Usuario autenticado - actualizar intentos
            updateUrlCount(user.getEmail());
        } else {
            // Usuario no autenticado
            tvUrlCount.setText("Inicia sesión para ver intentos");
            btnShorten.setEnabled(true); // Permitir acortar como anónimo
        }
    }

    private void updateUrlCount(String userEmail) {
        UrlDatabase.getInstance(this).getRemainingAttempts(userEmail, new UrlDatabase.AttemptsCallback() {
            @Override
            public void onSuccess(int remainingAttempts, boolean premiumStatus) {
                runOnUiThread(() -> {
                    isPremium = premiumStatus;
                    String countText = isPremium ? "Intentos: ∞ (Premium)" : "Intentos: " + remainingAttempts;
                    tvUrlCount.setText(countText);

                    // Debug: Verificar valores
                    Log.d("PREMIUM_DEBUG", "Premium status: " + premiumStatus);
                    Log.d("PREMIUM_DEBUG", "Botón upgrade visible: " + !premiumStatus);

                    btnUpgrade.setVisibility(premiumStatus ? View.GONE : View.VISIBLE);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    // Mostrar botón por si acaso (podría ser error temporal)
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

        // Asegurar protocolo HTTP
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
        startActivityForResult(intent, 1); // Usamos código 1 para identificar esta actividad
    }
    private void updatePremiumUI(boolean isPremium, int remainingAttempts) {
        runOnUiThread(() -> {
            // 1. Actualizar texto del contador
            String countText;
            if (isPremium) {
                countText = "★ Cuenta Premium ★";
            } else {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null && !user.isAnonymous()) {
                    // Mostrar intentos para usuarios registrados no premium
                    countText = remainingAttempts == Integer.MAX_VALUE ?
                            "★ Cuenta Premium ★" : // Caso de seguridad
                            "Intentos: " + remainingAttempts;
                } else {
                    // Usuarios anónimos o no logueados
                    countText = "Inicia sesión para ver intentos";
                }
            }
            tvUrlCount.setText(countText);

            // 2. Manejar visibilidad del botón de actualización
            if (btnUpgrade != null) {
                boolean shouldShowUpgradeButton = !isPremium &&
                        mAuth.getCurrentUser() != null &&
                        !mAuth.getCurrentUser().isAnonymous();

                btnUpgrade.setVisibility(shouldShowUpgradeButton ? View.VISIBLE : View.GONE);
            }

            // 3. Actualizar estado interno
            this.isPremium = isPremium;

            // 4. Debug en logs
            Log.d("PremiumUI", "Estado actualizado - Premium: " + isPremium +
                    ", Intentos: " + remainingAttempts +
                    ", Botón visible: " + (btnUpgrade != null && btnUpgrade.getVisibility() == View.VISIBLE));
        });
    }

    // Modifica checkPremiumStatus para pasar los intentos
    private void checkPremiumStatus() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updatePremiumUI(false, 0);
            return;
        }

        // Primero verificar caché local
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        boolean isPremium = prefs.getBoolean("is_premium", false);

        if (isPremium) {
            updatePremiumUI(true, Integer.MAX_VALUE);
        }

        // Luego verificar con el servidor
        UrlDatabase.getInstance(this).getRemainingAttempts(user.getEmail(), new UrlDatabase.AttemptsCallback() {
            @Override
            public void onSuccess(int remainingAttempts, boolean premiumStatus) {
                runOnUiThread(() -> {
                    if (premiumStatus) {
                        // Guardar estado premium localmente
                        getSharedPreferences("user_prefs", MODE_PRIVATE)
                                .edit()
                                .putBoolean("is_premium", true)
                                .apply();
                    }
                    updatePremiumUI(premiumStatus, premiumStatus ? Integer.MAX_VALUE : remainingAttempts);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> updatePremiumUI(false, 5)); // Valor por defecto
            }
        });
    }
    // Añade este método a tu HomeActivity
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

                // --- PUNTO 4: Manejo de errores --- //
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
                    // Mostrar error si la API falla
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

        // Inflar custom layout
        View view = getLayoutInflater().inflate(R.layout.dialog_history, null);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerHistory);

        // Configurar RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        HistorialAdapter adapter = new HistorialAdapter(urls);
        recyclerView.setAdapter(adapter);

        builder.setView(view);
        builder.setPositiveButton("Cerrar", null);
        builder.show();
    }
}