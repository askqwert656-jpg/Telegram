package org.telegram.ui;

import android.content.Context;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tgarchive.database.ArchivedMessageEntity;
import com.example.tgarchive.database.Folder42Database;
import com.example.tgarchive.database.MessageEditEntity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays only messages belonging to the selected Folder 42 chat. */
public class Folder42ChatActivity extends BaseFragment {
    private final String chatId;
    private final String chatTitle;
    private final long targetMessageId;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final ArrayList<ArchivedMessageEntity> messages = new ArrayList<>();
    private MessageAdapter adapter;
    private RecyclerView messageList;
    private TextView emptyView;

    public Folder42ChatActivity(String chatId, String chatTitle) {
        this(chatId, chatTitle, 0);
    }

    public Folder42ChatActivity(String chatId, String chatTitle, long targetMessageId) {
        this.chatId = chatId;
        this.chatTitle = chatTitle;
        this.targetMessageId = targetMessageId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(chatTitle);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        messageList = new RecyclerView(context);
        messageList.setLayoutManager(new LinearLayoutManager(context));
        adapter = new MessageAdapter();
        messageList.setAdapter(adapter);
        root.addView(messageList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("В этой переписке пока нет сохранённых сообщений.");
        emptyView.setTextSize(14);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setVisibility(View.GONE);

        fragmentView = root;
        loadMessages();
        return fragmentView;
    }

    private void loadMessages() {
        databaseExecutor.execute(() -> {
            List<ArchivedMessageEntity> result = Folder42Database.getInstance(getParentActivity()).folder42Dao().getMessages(chatId);
            AndroidUtilities.runOnUIThread(() -> {
                if (adapter == null || fragmentView == null) return;
                messages.clear();
                if (result != null) messages.addAll(result);
                adapter.notifyDataSetChanged();
                if (emptyView != null) emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                if (targetMessageId != 0 && messageList != null) {
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).messageId == targetMessageId) {
                            messageList.scrollToPosition(i);
                            break;
                        }
                    }
                }
            });
        });
    }

    private void showMessageHistory(ArchivedMessageEntity message) {
        databaseExecutor.execute(() -> {
            List<MessageEditEntity> edits = Folder42Database.getInstance(getParentActivity()).folder42Dao().getEdits(message.recordId);
            AndroidUtilities.runOnUIThread(() -> {
                Context context = getParentActivity();
                if (context == null) return;
                StringBuilder history = new StringBuilder("Сохранённый текст: ").append(message.text);
                if (edits != null) {
                    for (MessageEditEntity edit : edits) {
                        history.append("\n\nБыло: ").append(edit.previousText).append("\nСтало: ").append(edit.newText);
                    }
                }
                if (message.deletedAt != null) history.append("\n\nСообщение удалено.");
                new AlertDialog.Builder(context).setTitle("История сообщения").setMessage(history.toString()).setPositiveButton("Закрыть", null).show();
            });
        });
    }
    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        databaseExecutor.shutdown();
        adapter = null;
    }

    private class MessageAdapter extends RecyclerView.Adapter<MessageHolder> {
        @Override
        public MessageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout bubble = new LinearLayout(parent.getContext());
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(9), AndroidUtilities.dp(14), AndroidUtilities.dp(9));
            TextView body = new TextView(parent.getContext());
            body.setTextSize(15);
            body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            bubble.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView meta = new TextView(parent.getContext());
            meta.setTextSize(11);
            meta.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            bubble.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(20)));
            return new MessageHolder(bubble, body, meta);
        }

        @Override
        public void onBindViewHolder(MessageHolder holder, int position) {
            ArchivedMessageEntity message = messages.get(position);
            holder.body.setText(message.text);
            String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(message.date * 1000L));
            holder.meta.setText(date + (message.deletedAt != null ? " · удалено" : (message.editedAt > 0 ? " · изменено" : "")));
            holder.itemView.setOnClickListener(v -> showMessageHistory(message));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    private static class MessageHolder extends RecyclerView.ViewHolder {
        final TextView body;
        final TextView meta;
        MessageHolder(View itemView, TextView body, TextView meta) {
            super(itemView);
            this.body = body;
            this.meta = meta;
        }
    }
}
