package com.simple.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.simple.messenger.core.ChatCore;
import com.simple.messenger.core.Message;
import com.simple.messenger.core.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The list of chats: everyone, then each person. */
public class ChatsActivity extends Activity {

    static final class Row {
        String key;
        String title;
        String preview;
        long ts;
        int unread;
        boolean online;
        boolean group;
    }

    private final List<Row> rows = new ArrayList<>();
    private final Runnable refresher = this::refresh;
    private TextView subtitle;
    private TextView status;
    private RowAdapter adapter;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (MessengerApp.get().core() == null) {
            toLogin();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.SURFACE);

        LinearLayout bar = Ui.bar(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        TextView title = Ui.text(this, getString(R.string.app_name), 20, 0xFFFFFFFF, true);
        subtitle = Ui.text(this, "", 13, 0xFFD7EDE6, false);
        bar.addView(Ui.titles(this, title, subtitle));
        bar.addView(Ui.barButton(this, "Sign out", v -> confirmSignOut()));
        root.addView(bar);

        status = Ui.text(this, "", 14, 0xFFFFFFFF, false);
        status.setBackgroundColor(Ui.ERROR);
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        status.setVisibility(View.GONE);
        root.addView(status);

        ListView list = new ListView(this);
        list.setDivider(null);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            Row r = rows.get(position);
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_KEY, r.key);
            i.putExtra(ChatActivity.EXTRA_TITLE, r.title);
            startActivity(i);
        });
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MessengerApp.get().watch(refresher);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MessengerApp.get().unwatch(refresher);
    }

    private void refresh() {
        MessengerApp app = MessengerApp.get();
        ChatCore core = app.core();
        if (core == null) {
            toLogin();
            return;
        }
        subtitle.setText("Signed in as " + core.myName);
        status.setVisibility(app.connected ? View.GONE : View.VISIBLE);
        status.setText(app.problem == null ? "Connecting…" : app.problem);

        rows.clear();
        Row everyone = row(core, ChatCore.EVERYONE, "Everyone", true, false);
        int people = core.others().size() + 1;
        if (everyone.preview.isEmpty()) everyone.preview = people + " people, " + core.onlineCount() + " online";
        List<Row> chats = new ArrayList<>();
        for (User u : core.others()) chats.add(row(core, u.id, u.name, false, u.online));
        Collections.sort(chats, (a, b) -> a.ts != b.ts ? Long.compare(b.ts, a.ts) : a.title.compareToIgnoreCase(b.title));
        rows.add(everyone);
        rows.addAll(chats);
        adapter.notifyDataSetChanged();
    }

    private Row row(ChatCore core, String key, String title, boolean group, boolean online) {
        Row r = new Row();
        r.key = key;
        r.title = title;
        r.group = group;
        r.online = online;
        r.unread = core.unread(key);
        Message m = core.lastMessage(key);
        if (m != null) {
            String who = m.from.equals(core.myId) ? "You: " : (group ? m.fromName + ": " : "");
            r.preview = who + m.text.replace('\n', ' ');
            r.ts = m.ts;
        } else {
            r.preview = group ? "" : (online ? "Online now" : "Say hello");
        }
        return r;
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("You can sign back in later with the same name and password.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (d, w) -> MessengerApp.get().signOut())
                .show();
    }

    private void toLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() { return rows.size(); }

        @Override
        public Object getItem(int position) { return rows.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row r = rows.get(position);
            LinearLayout item = new LinearLayout(ChatsActivity.this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(Ui.dp(ChatsActivity.this, 16), Ui.dp(ChatsActivity.this, 10), Ui.dp(ChatsActivity.this, 16), Ui.dp(ChatsActivity.this, 10));
            item.setMinimumHeight(Ui.dp(ChatsActivity.this, 72));

            FrameLayout avatarBox = new FrameLayout(ChatsActivity.this);
            TextView avatar = Ui.avatar(ChatsActivity.this, r.title, 50, r.group);
            avatarBox.addView(avatar, new FrameLayout.LayoutParams(Ui.dp(ChatsActivity.this, 50), Ui.dp(ChatsActivity.this, 50)));
            if (r.online) {
                View dot = new View(ChatsActivity.this);
                android.graphics.drawable.GradientDrawable d = Ui.circle(Ui.ONLINE);
                d.setStroke(Ui.dp(ChatsActivity.this, 2), Ui.SURFACE);
                dot.setBackground(d);
                int s = Ui.dp(ChatsActivity.this, 14);
                avatarBox.addView(dot, new FrameLayout.LayoutParams(s, s, Gravity.END | Gravity.BOTTOM));
            }
            item.addView(avatarBox);

            LinearLayout middle = new LinearLayout(ChatsActivity.this);
            middle.setOrientation(LinearLayout.VERTICAL);
            middle.setPadding(Ui.dp(ChatsActivity.this, 14), 0, Ui.dp(ChatsActivity.this, 8), 0);
            TextView name = Ui.text(ChatsActivity.this, r.title, 16, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            TextView preview = Ui.text(ChatsActivity.this, r.preview, 14, Ui.MUTED, false);
            preview.setSingleLine(true);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            middle.addView(name);
            middle.addView(preview);
            item.addView(middle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout right = new LinearLayout(ChatsActivity.this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            TextView time = Ui.text(ChatsActivity.this, r.ts > 0 ? Ui.listTime(ChatsActivity.this, r.ts) : "", 12, r.unread > 0 ? Ui.PRIMARY : Ui.MUTED, false);
            right.addView(time);
            if (r.unread > 0) {
                TextView badge = Ui.text(ChatsActivity.this, String.valueOf(r.unread), 12, 0xFFFFFFFF, true);
                badge.setGravity(Gravity.CENTER);
                badge.setMinWidth(Ui.dp(ChatsActivity.this, 22));
                badge.setPadding(Ui.dp(ChatsActivity.this, 6), Ui.dp(ChatsActivity.this, 2), Ui.dp(ChatsActivity.this, 6), Ui.dp(ChatsActivity.this, 2));
                badge.setBackground(Ui.rounded(Ui.PRIMARY, Ui.dp(ChatsActivity.this, 11)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(ChatsActivity.this, 4);
                lp.gravity = Gravity.END;
                right.addView(badge, lp);
            }
            item.addView(right);
            return item;
        }
    }
}
