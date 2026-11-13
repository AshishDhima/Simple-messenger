package com.simple.messenger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.simple.messenger.core.ChatCore;
import com.simple.messenger.core.Message;
import com.simple.messenger.core.User;

import java.util.ArrayList;
import java.util.List;

/** One conversation: with everyone, or with one person. */
public class ChatActivity extends Activity {
    static final String EXTRA_KEY = "key";
    static final String EXTRA_TITLE = "title";

    /** Items are either a Message or a String day label. */
    private final List<Object> items = new ArrayList<>();
    private final Runnable refresher = this::refresh;
    private String key;
    private boolean group;
    private TextView title;
    private TextView subtitle;
    private ListView list;
    private EditText input;
    private ItemAdapter adapter;
    private int lastCount = -1;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (MessengerApp.get().core() == null) {
            finish();
            return;
        }
        key = getIntent().getStringExtra(EXTRA_KEY);
        group = ChatCore.EVERYONE.equals(key);
        String name = getIntent().getStringExtra(EXTRA_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout bar = Ui.bar(this);
        TextView back = Ui.barButton(this, "‹", v -> finish());
        back.setTextSize(30);
        back.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 10), Ui.dp(this, 4));
        back.setContentDescription("Back");
        bar.addView(back);
        bar.addView(Ui.avatar(this, name == null ? "" : name, 40, group));
        title = Ui.text(this, name, 17, 0xFFFFFFFF, true);
        title.setSingleLine(true);
        subtitle = Ui.text(this, "", 13, 0xFFD7EDE6, false);
        LinearLayout titles = Ui.titles(this, title, subtitle);
        titles.setPadding(Ui.dp(this, 12), 0, 0, 0);
        bar.addView(titles);
        root.addView(bar);

        list = new ListView(this);
        list.setDivider(null);
        list.setStackFromBottom(true);
        list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        list.setClipToPadding(false);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        list.setSelector(android.R.color.transparent);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 8));
        input = new EditText(this);
        input.setHint("Message");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(5);
        input.setTextSize(16);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 22)));
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 11), Ui.dp(this, 16), Ui.dp(this, 11));
        input.setMinHeight(Ui.dp(this, 46));
        composer.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView send = Ui.text(this, "➤", 20, 0xFFFFFFFF, false);
        send.setGravity(Gravity.CENTER);
        send.setBackground(Ui.circle(Ui.PRIMARY));
        send.setContentDescription("Send");
        send.setClickable(true);
        send.setFocusable(true);
        send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
        sp.leftMargin = Ui.dp(this, 6);
        composer.addView(send, sp);
        root.addView(composer);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MessengerApp app = MessengerApp.get();
        if (app.core() == null) {
            finish();
            return;
        }
        app.core().setOpenConversation(key);
        app.watch(refresher);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MessengerApp app = MessengerApp.get();
        app.unwatch(refresher);
        if (app.core() != null) app.core().setOpenConversation(null);
    }

    private void refresh() {
        MessengerApp app = MessengerApp.get();
        ChatCore core = app.core();
        if (core == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        core.setOpenConversation(key); // keeps unread at zero while this screen is open
        if (!app.connected) {
            subtitle.setText("Connecting…");
        } else if (group) {
            subtitle.setText((core.others().size() + 1) + " people, " + core.onlineCount() + " online");
        } else {
            User u = core.user(key);
            if (u != null) title.setText(u.name);
            subtitle.setText(u != null && u.online ? "Online" : "Offline");
        }

        List<Message> messages = core.messages(key);
        items.clear();
        String day = "";
        for (Message m : messages) {
            String d = Ui.day(m.ts);
            if (!d.equals(day)) {
                items.add(d);
                day = d;
            }
            items.add(m);
        }
        adapter.notifyDataSetChanged();
        if (messages.size() != lastCount) {
            list.setSelection(items.size() - 1);
            lastCount = messages.size();
        }
    }

    private void send() {
        ChatCore core = MessengerApp.get().core();
        final String text = input.getText().toString().trim();
        if (core == null || text.isEmpty()) return;
        input.setText("");
        core.send(key, text, (sent, error) -> runOnUiThread(() -> {
            if (error == null) return;
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            if (input.getText().length() == 0) {
                input.setText(text);
                input.setSelection(text.length());
            }
        }));
    }

    private final class ItemAdapter extends BaseAdapter {
        @Override
        public int getCount() { return items.size(); }

        @Override
        public Object getItem(int position) { return items.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public boolean isEnabled(int position) { return false; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object item = items.get(position);
            LinearLayout row = new LinearLayout(ChatActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int side = Ui.dp(ChatActivity.this, 10);

            if (item instanceof String) {
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                row.setPadding(side, Ui.dp(ChatActivity.this, 10), side, Ui.dp(ChatActivity.this, 6));
                TextView label = Ui.text(ChatActivity.this, (String) item, 12, Ui.MUTED, false);
                label.setBackground(Ui.rounded(0xFFF8FAF8, Ui.dp(ChatActivity.this, 8)));
                label.setPadding(Ui.dp(ChatActivity.this, 10), Ui.dp(ChatActivity.this, 3), Ui.dp(ChatActivity.this, 10), Ui.dp(ChatActivity.this, 3));
                row.addView(label);
                return row;
            }

            Message m = (Message) item;
            ChatCore core = MessengerApp.get().core();
            boolean mine = core != null && m.from.equals(core.myId);
            Object prev = position > 0 ? items.get(position - 1) : null;
            boolean sameSender = prev instanceof Message && ((Message) prev).from.equals(m.from);
            row.setGravity(mine ? Gravity.END : Gravity.START);
            row.setPadding(side, Ui.dp(ChatActivity.this, sameSender ? 2 : 6), side, 0);

            LinearLayout bubble = new LinearLayout(ChatActivity.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setBackground(Ui.rounded(mine ? Ui.MINE : Ui.SURFACE, Ui.dp(ChatActivity.this, 12)));
            bubble.setPadding(Ui.dp(ChatActivity.this, 11), Ui.dp(ChatActivity.this, 6), Ui.dp(ChatActivity.this, 11), Ui.dp(ChatActivity.this, 5));
            int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);

            if (group && !mine && !sameSender) {
                TextView who = Ui.text(ChatActivity.this, m.fromName, 13, Ui.PRIMARY, true);
                bubble.addView(who);
            }
            TextView body = Ui.text(ChatActivity.this, m.text, 16, Ui.TEXT, false);
            body.setMaxWidth(maxWidth);
            body.setTextIsSelectable(true);
            bubble.addView(body);
            TextView time = Ui.text(ChatActivity.this, Ui.time(ChatActivity.this, m.ts), 11, Ui.MUTED, false);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tp.gravity = Gravity.END;
            bubble.addView(time, tp);
            row.addView(bubble);
            return row;
        }
    }
}
