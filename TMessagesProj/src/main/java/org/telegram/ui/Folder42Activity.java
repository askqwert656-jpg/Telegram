package org.telegram.ui;

import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tgarchive.database.ArchivedChatEntity;
import com.example.tgarchive.database.Folder42Database;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Folder42Notifications;
import org.telegram.messenger.R;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.TLRPC;
import android.os.Bundle;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent chat list for the local Folder 42 archive. */
public class Folder42Activity extends BaseFragment {
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final ArrayList<ArchivedChatEntity> chats = new ArrayList<>();
    private ChatAdapter adapter;
    private TextView emptyView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Папка № 42");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.msg_add).setContentDescription("Добавить переписку")
                .setOnClickListener(v -> openChatPicker());
        menu.addItem(2, R.drawable.msg_notifications).setContentDescription("Уведомления Папки № 42")
                .setOnClickListener(v -> {
                    boolean enabled = !Folder42Notifications.areEnabled(context);
                    Folder42Notifications.setEnabled(context, enabled);
                    if (enabled && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                            getParentActivity() != null) {
                        ActivityCompat.requestPermissions(getParentActivity(),
                                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42042);
                    }
                    Toast.makeText(context, enabled ? "Уведомления Папки № 42 включены" : "Уведомления Папки № 42 отключены", Toast.LENGTH_SHORT).show();
                });
        if (Folder42Notifications.areEnabled(context) && Folder42Notifications.shouldRequestPermission(context) && getParentActivity() != null) {
            ActivityCompat.requestPermissions(getParentActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42042);
        }

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        adapter = new ChatAdapter();
        list.setAdapter(adapter);
        root.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("Переписки появятся здесь автоматически после редактирования или удаления сообщений.\nМожно также добавить чат вручную.");
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24));
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setVisibility(View.GONE);

        fragmentView = root;
        loadChats();
        return fragmentView;
    }

    private void openChatPicker() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, selected, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (selected == null || selected.isEmpty()) return false;
            for (MessagesStorage.TopicKey key : selected) {
                long dialogId = key.dialogId;
                if (dialogId == 0) continue;
                String chatId = String.valueOf(dialogId);
                String title = "";
                int chatType = 0;
                if (dialogId > 0) {
                    TLRPC.User user = getMessagesController().getUser(dialogId);
                    if (user != null) {
                        title = org.telegram.messenger.UserObject.getUserName(user);
                    }
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    if (chat != null) {
                        title = chat.title;
                        chatType = chat.broadcast ? 2 : 1;
                    }
                }
                if (title.isEmpty()) title = "Чат " + chatId;
                final String savedTitle = title;
                final int savedType = chatType;
                databaseExecutor.execute(() -> {
                    Folder42Database.getInstance(getParentActivity()).folder42Dao().insertOrUpdateChat(
                            new ArchivedChatEntity(chatId, savedTitle, savedType, 0, System.currentTimeMillis())
                    );
                    AndroidUtilities.runOnUIThread(this::loadChats);
                });
            }
            fragment.finishFragment();
            return true;
        });
        presentFragment(picker);
    }

    private void loadChats() {
        databaseExecutor.execute(() -> {
            List<ArchivedChatEntity> result = Folder42Database.getInstance(getParentActivity()).folder42Dao().getChats();
            AndroidUtilities.runOnUIThread(() -> {
                if (adapter == null || fragmentView == null) return;
                chats.clear();
                if (result != null) chats.addAll(result);
                adapter.notifyDataSetChanged();
                if (emptyView != null) emptyView.setVisibility(chats.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            loadChats();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        databaseExecutor.shutdown();
        adapter = null;
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatHolder> {
        @Override
        public ChatHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
            TextView title = new TextView(parent.getContext());
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(25)));
            TextView subtitle = new TextView(parent.getContext());
            subtitle.setTextSize(13);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            row.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(21)));
            View divider = new View(parent.getContext());
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            row.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(1)));
            return new ChatHolder(row, title, subtitle);
        }

        @Override
        public void onBindViewHolder(ChatHolder holder, int position) {
            ArchivedChatEntity chat = chats.get(position);
            holder.title.setText(chat.title);
            holder.subtitle.setText(chat.chatType == 2 ? "Канал" : chat.chatType == 1 ? "Группа" : "Личная переписка");
            holder.itemView.setOnClickListener(v -> presentFragment(new Folder42ChatActivity(chat.chatId, chat.title)));
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }
    }

    private static class ChatHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        ChatHolder(View itemView, TextView title, TextView subtitle) {
            super(itemView);
            this.title = title;
            this.subtitle = subtitle;
        }
    }
}
