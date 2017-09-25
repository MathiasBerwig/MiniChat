package io.github.mathiasberwig.minichat.presentation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import io.github.mathiasberwig.minichat.R;
import io.github.mathiasberwig.minichat.model.Message;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "MainActivity";

    // Request Codes
    private static final int RC_SIGN_IN = 9001;

    // Firebase
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;

    // Google
    private GoogleApiClient googleApiClient;

    // UI Components
    private ImageButton btnSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa a instância do Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

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

        if (firebaseUser == null) {
            // Usuário não fez login, então inicia o SDK do Google
            signIn();
        } else {
            // Configura a lista caso o usuário já esteja logado. Do contrário, a lista é configurada
            // após o login
            setupList();
        }

        setupInputAndSend();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Infla o menu principal na Toolbar da Activity
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
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

    /**
     * Configura a lista de mensagens.
     */
    private void setupList() {
        final RecyclerView list = findViewById(R.id.list_messages);

        final DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
        final FirebaseRecyclerAdapter adapter = new FirebaseRecyclerAdapter<Message, MessageViewHolder>(
                Message.class,
                R.layout.item_message,
                MessageViewHolder.class,
                databaseReference) {

            @Override
            protected void populateViewHolder(final MessageViewHolder viewHolder,
                                              Message message, int position) {

                // Atualiza os dados da mensagem
                viewHolder.message.setText(message.getText());
                viewHolder.username.setText(message.getName());

                if (message.getPhotoUrl() == null) {
                    // Imagem padrão
                    viewHolder.userPhoto.setImageDrawable(
                            ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_account_circle_black_36dp)
                    );
                } else {
                    // Carrega a foto do usuário
                    Glide.with(MainActivity.this)
                            .load(message.getPhotoUrl())
                            .into(viewHolder.userPhoto);
                }
            }
        };

        // Prepara o gerenciador de layout
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true); // preenche a lista partindo de baixo

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int messageCount = adapter.getItemCount();
                int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                // Se o RecyclerView está sendo carregado pela primeira vez ou o usuário está no
                // final da lista, faz  scroll até o último item para mostrar a nova mensagem
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (messageCount - 1) && lastVisiblePosition == (positionStart - 1))) {
                    list.scrollToPosition(positionStart);
                }
            }
        });

        list.setLayoutManager(linearLayoutManager);
        list.setAdapter(adapter);
    }

    /**
     * Configura o botão enviar e o edit text da mensagem.
     */
    private void setupInputAndSend() {
        btnSend = findViewById(R.id.btn_send);
        final EditText edtMessage = findViewById(R.id.input_message);

        // Desabilita o botão enviar caso o usuário não tenha sido autenticado
        btnSend.setEnabled(firebaseUser != null);

        // Define a ação do botão Enviar
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Obtém as informações da mensagem e do usuário
                final String text = edtMessage.getText().toString();
                final String username = firebaseUser.getDisplayName();
                final Uri photoUri = firebaseUser.getPhotoUrl();
                final String userPhoto = photoUri == null ? null : photoUri.toString();

                // Cria uma nova mensagem para enviar ao Firebase
                FirebaseDatabase.getInstance()
                        .getReference()
                        .push()
                        .setValue(new Message(text, username, userPhoto));

                // Remove a mensagem enviada
                edtMessage.setText("");
            }
        });
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
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
            firebaseAuth.signOut();
            Auth.GoogleSignInApi.signOut(googleApiClient);
            firebaseUser = null;
            finish();
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

                                // Configura a lista de mensagens
                                setupList();

                                // Habilita o botão enviar mensagem
                                btnSend.setEnabled(true);

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
