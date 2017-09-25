package io.github.mathiasberwig.minichat.presentation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import io.github.mathiasberwig.minichat.R;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener,
        FirebaseAuth.AuthStateListener {

    private static final String TAG = "MainActivity";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 140;

    // Request Codes
    private static final int RC_SIGN_IN = 9001;

    // Firebase
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;

    // Google
    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa a instância do Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

        if (firebaseUser == null) {
            // Usuário não fez login, então inicia o SDK do Google
            signIn();
        }

        // Configura o módulo de login do Google
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        // Inicializa o cliente da API de login do Google com as configurações definidas anteriormente
        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        configureUiComponents();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Infla o menu principal na Toolbar da Activity
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onStart() {
        super.onStart();
        firebaseAuth.addAuthStateListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_in_menu: { // Entrar
                signIn();
                return true;
            }
            case R.id.sign_out_menu: { // Sair
                signOut();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                Toast.makeText(this, R.string.error_authentication_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        firebaseAuth.removeAuthStateListener(this);
    }

    /**
     * Configura os componentes da activity:
     * - EditText da mensagem
     * - Button enviar mensagem
     */
    private void configureUiComponents() {
        final ImageButton btnSend = (ImageButton) findViewById(R.id.btn_send);
        final EditText messageEditText = (EditText) findViewById(R.id.input_message);

        // Define o tamanho máximo do campo de mensagem
        messageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)
        });
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                final boolean enabled = charSequence.toString().trim().length() > 0;
                btnSend.setEnabled(enabled);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
    }

    @Override
    public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

    }

    /**
     * Inicia o Sign-in do Google.
     */
    private void signIn() {
        if (firebaseUser == null) {
            Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
            startActivityForResult(signInIntent, RC_SIGN_IN);
        } else {
            Toast.makeText(this, R.string.error_signed_in, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Remove o usuário atual da aplicação.
     */
    private void signOut() {
        if (firebaseUser == null) {
            Toast.makeText(this, R.string.error_not_signed_in, Toast.LENGTH_SHORT).show();
        } else {
            FirebaseAuth.getInstance().signOut();
            firebaseUser = null;
        }
    }

    /**
     * Autentica as credenciais do Google contra o Firebase.
     *
     * @param acct credenciais do Google.
     */
    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Login realizado com sucesso
                            Log.d(TAG, "signInWithCredential:success");
                            firebaseUser = firebaseAuth.getCurrentUser();

                            // Mostra a mensagem de boas vindas
                            if (firebaseUser != null) {
                                final String welcomeMessage = getString(
                                        R.string.welcome_message,
                                        firebaseUser.getDisplayName()
                                );

                                Toast.makeText(MainActivity.this, welcomeMessage, Toast.LENGTH_SHORT)
                                        .show();
                            }
                        } else {
                            // Erro ao fazer login, mostra uma mensagem para o usuário
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(MainActivity.this, R.string.error_authentication_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
