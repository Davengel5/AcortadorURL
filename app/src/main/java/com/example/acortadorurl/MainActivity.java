package com.example.acortadorurl;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 123;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private Button btnGoogleLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Error en Google Sign-In: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            checkUserExistsAndRegister(user);
                        }
                    } else {
                        Toast.makeText(this, "Error en autenticación", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkUserExistsAndRegister(FirebaseUser user) {
        new Thread(() -> {
            try {
                JSONObject checkJson = new JSONObject();
                checkJson.put("email", user.getEmail());

                Request checkRequest = new Request.Builder()
                        .url("https://apiurl.up.railway.app/check_user.php")
                        .post(RequestBody.create(checkJson.toString(), MediaType.parse("application/json")))
                        .build();

                Response checkResponse = new OkHttpClient().newCall(checkRequest).execute();
                String checkData = checkResponse.body().string();
                JSONObject checkResult = new JSONObject(checkData);

                if (!checkResult.getBoolean("exists")) {
                    JSONObject registerJson = new JSONObject();
                    registerJson.put("email", user.getEmail());
                    registerJson.put("nombre", user.getDisplayName() != null ? user.getDisplayName() : "Usuario");
                    registerJson.put("tipo", "Free");
                    registerJson.put("intentos", 5);

                    Request registerRequest = new Request.Builder()
                            .url("https://apiurl.up.railway.app/insert_user.php")
                            .post(RequestBody.create(registerJson.toString(), MediaType.parse("application/json")))
                            .build();

                    new OkHttpClient().newCall(registerRequest).execute();
                }

                checkPremiumStatus(user.getEmail());

            } catch (Exception e) {
                Log.e("Registration", "Error: ", e);
                runOnUiThread(() -> {
                    startActivity(new Intent(MainActivity.this, HomeActivity.class));
                    finish();
                });
            }
        }).start();
    }

    private void checkPremiumStatus(String email) {
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

                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                    intent.putExtra("is_premium", isPremium);
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                Log.e("PremiumCheck", "Error: ", e);
                runOnUiThread(() -> {
                    startActivity(new Intent(MainActivity.this, HomeActivity.class));
                    finish();
                });
            }
        }).start();
    }

    private void registerUserInDatabase(FirebaseUser user) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("email", user.getEmail());
                json.put("nombre", user.getDisplayName() != null ? user.getDisplayName() : "Usuario");
                json.put("tipo", "Free"); // Por defecto Free
                json.put("intentos", 5);  // Intentos iniciales

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/insert_user.php")
                        .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                        .build();

                client.newCall(request).execute();
            } catch (Exception e) {
                Log.e("REGISTER_ERROR", "Error registrando usuario", e);
            }
        }).start();
    }
    private void insertUserInApi(FirebaseUser user) {
        if (user == null || user.getEmail() == null) return;

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                JSONObject checkUser = new JSONObject();
                checkUser.put("email", user.getEmail());

                Request checkRequest = new Request.Builder()
                        .url("https://apiurl.up.railway.app/check_user.php")
                        .post(RequestBody.create(
                                checkUser.toString(),
                                MediaType.parse("application/json")
                        ))
                        .build();

                Response checkResponse = client.newCall(checkRequest).execute();
                String checkData = checkResponse.body().string();
                JSONObject checkJson = new JSONObject(checkData);

                if (checkJson.getBoolean("exists")) {
                    Log.d("API_RESPONSE", "Usuario ya registrado");
                    return;
                }

                JSONObject json = new JSONObject();
                json.put("nombre", user.getDisplayName() != null ? user.getDisplayName() : "Usuario");
                json.put("email", user.getEmail());
                json.put("tipo", "Free");
                json.put("intentos", 5);

                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/insert_user.php")
                        .post(RequestBody.create(
                                json.toString(),
                                MediaType.parse("application/json")
                        ))
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();

                runOnUiThread(() -> {
                    Log.d("API_RESPONSE", "Usuario registrado: " + responseData);
                });

            } catch (Exception e) {
                Log.e("API_ERROR", "Error insertando usuario", e);
            }
        }).start();
    }

}