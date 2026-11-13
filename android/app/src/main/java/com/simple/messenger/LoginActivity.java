package com.simple.messenger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.simple.messenger.core.Api;
import com.simple.messenger.core.Json;

import java.util.Map;

public class LoginActivity extends Activity {
    private EditText server;
    private EditText name;
    private EditText password;
    private TextView error;
    private Button go;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        MessengerApp app = MessengerApp.get();
        if (app.core() != null) {
            openChats();
            return;
        }
        getWindow().setStatusBarColor(Ui.PRIMARY_DARK);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        form.setPadding(pad, Ui.dp(this, 48), pad, pad);

        TextView logo = Ui.avatar(this, "", 64, true);
        logo.setText("✉");
        form.addView(logo);
        TextView title = Ui.text(this, getString(R.string.app_name), 26, Ui.TEXT, true);
        title.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        form.addView(title);
        form.addView(Ui.text(this, "Sign in with any name and password. A new name creates an account.", 15, Ui.MUTED, false));

        server = field(form, "Server address", "192.168.1.10:3000", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        name = field(form, "Your name", "e.g. Priya", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        password = field(form, "Password", "At least 4 characters", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImeOptions(EditorInfo.IME_ACTION_GO);
        password.setOnEditorActionListener((v, actionId, event) -> {
            submit();
            return true;
        });
        server.setText(app.prefs().getString("server", ""));
        name.setText(app.prefs().getString("name", ""));

        error = Ui.text(this, "", 14, Ui.ERROR, false);
        error.setPadding(0, Ui.dp(this, 12), 0, 0);
        error.setVisibility(View.GONE);
        form.addView(error);

        go = new Button(this);
        go.setText("Sign in");
        go.setAllCaps(false);
        go.setTextColor(0xFFFFFFFF);
        go.setTextSize(16);
        go.setBackground(Ui.rounded(Ui.PRIMARY, Ui.dp(this, 24)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 50));
        lp.topMargin = Ui.dp(this, 24);
        go.setLayoutParams(lp);
        go.setOnClickListener(v -> submit());
        form.addView(go);

        TextView help = Ui.text(this, "The server address is shown when the server starts. Your phone must be on the same network.", 13, Ui.MUTED, false);
        help.setPadding(0, Ui.dp(this, 16), 0, 0);
        form.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        scroll.addView(form);
        setContentView(scroll);
    }

    private EditText field(LinearLayout form, String label, String hint, int type) {
        TextView l = Ui.text(this, label, 14, Ui.TEXT, true);
        l.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 6));
        form.addView(l);
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        e.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 10)));
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        e.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(e);
        return e;
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
        go.setEnabled(true);
        go.setText("Sign in");
    }

    private void submit() {
        final String address = server.getText().toString().trim();
        final String who = name.getText().toString().trim();
        final String pass = password.getText().toString();
        if (address.isEmpty()) { showError("Enter the server address."); return; }
        if (who.length() < 2) { showError("Enter a name of at least 2 characters."); return; }
        if (pass.length() < 4) { showError("Enter a password of at least 4 characters."); return; }
        error.setVisibility(View.GONE);
        go.setEnabled(false);
        go.setText("Signing in…");
        new Thread(() -> {
            try {
                final String base = Api.normalize(address);
                final Map<String, Object> r = new Api(base, null).join(who, pass);
                runOnUiThread(() -> {
                    MessengerApp.get().signIn(base, Json.str(r, "id"), Json.str(r, "token"), Json.str(r, "name"));
                    openChats();
                });
            } catch (Api.ApiException e) {
                final String m = e.getMessage();
                runOnUiThread(() -> showError(m));
            } catch (Exception e) {
                runOnUiThread(() -> showError("Can't reach the server. Check the address, and that the server is running and your phone is on the same network."));
            }
        }).start();
    }

    private void openChats() {
        startActivity(new Intent(this, ChatsActivity.class));
        finish();
    }
}
