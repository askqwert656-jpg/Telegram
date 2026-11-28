package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.graphics.drawable.GradientDrawable;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.FileProvider;

import com.example.tgarchive.database.ArchivedMessageEntity;
import com.example.tgarchive.database.Folder42Database;
import com.example.tgarchive.database.Folder42Dao;
import com.example.tgarchive.database.MessageEditEntity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
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
    private TextView eventsTab;
    private TextView fullCopyTab;
    private boolean showingFullCopy;

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
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        eventsTab = new TextView(context);
        eventsTab.setText("Удаления и изменения");
        eventsTab.setGravity(Gravity.CENTER);
        eventsTab.setTextSize(13);
        eventsTab.setOnClickListener(v -> {
            showingFullCopy = false;
            updateTabs();
            loadMessages();
        });
        fullCopyTab = new TextView(context);
        fullCopyTab.setText("Полная копия");
        fullCopyTab.setGravity(Gravity.CENTER);
        fullCopyTab.setTextSize(13);
        fullCopyTab.setOnClickListener(v -> {
            showingFullCopy = true;
            updateTabs();
            loadMessages();
        });
        tabs.addView(eventsTab, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(44), 1));
        tabs.addView(fullCopyTab, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(44), 1));
        root.addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP));
        updateTabs();

        messageList = new RecyclerView(context);
        messageList.setLayoutManager(new LinearLayoutManager(context));
        adapter = new MessageAdapter();
        messageList.setAdapter(adapter);
        root.addView(messageList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 44, 0, 0));

        emptyView = new TextView(context);
        emptyView.setText("Здесь пока нет сохранённых удалений или изменений.");
        emptyView.setTextSize(14);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 44, 0, 0));
        emptyView.setVisibility(View.GONE);

        fragmentView = root;
        loadMessages();
        return fragmentView;
    }

    private void updateTabs() {
        if (eventsTab == null || fullCopyTab == null) return;
        int selected = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int normal = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        eventsTab.setTextColor(showingFullCopy ? normal : selected);
        fullCopyTab.setTextColor(showingFullCopy ? selected : normal);
        eventsTab.setTypeface(null, showingFullCopy ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        fullCopyTab.setTypeface(null, showingFullCopy ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void loadMessages() {
        databaseExecutor.execute(() -> {
            Folder42Dao dao = Folder42Database.getInstance(getParentActivity()).folder42Dao();
            List<ArchivedMessageEntity> result = showingFullCopy ? dao.getFullMessages(chatId) : dao.getMessages(chatId);
            AndroidUtilities.runOnUIThread(() -> {
                if (adapter == null || fragmentView == null) return;
                messages.clear();
                if (result != null) messages.addAll(result);
                adapter.notifyDataSetChanged();
                if (emptyView != null) {
                    emptyView.setText(showingFullCopy
                            ? "Полная копия ещё не сохранена. Нажми «+» в Папке № 42."
                            : "Здесь пока нет удалённых или изменённых сообщений собеседника.");
                    emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                }
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

    private String getArchivedMediaPath(String metadata) {
        if (metadata == null) return null;
        int separator = metadata.indexOf('|');
        if (separator < 0 || separator == metadata.length() - 1) return null;
        String path = metadata.substring(separator + 1);
        File file = new File(path);
        return file.isFile() ? path : null;
    }

    private void openArchivedMedia(Context context, String path) {
        try {
            File file = new File(path);
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType == null ? "*/*" : mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(context, "Не удалось открыть вложение", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteArchivedMessage(ArchivedMessageEntity message) {
        databaseExecutor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(getParentActivity());
            db.runInTransaction(() -> {
                db.folder42Dao().removeEdits(message.recordId);
                db.folder42Dao().removeMessage(message.recordId);
            });
            AndroidUtilities.runOnUIThread(this::loadMessages);
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
                        history.append("\n\nИзменено: ").append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(edit.editedAt))).append("\nБыло: ").append(edit.previousText).append("\nСтало: ").append(edit.newText);
                    }
                }
                if (message.archiveType == 1) {
                    String author = message.senderId == UserConfig.getInstance(currentAccount).clientUserId ? "Вы" : "Собеседник";
                    String mediaPath = getArchivedMediaPath(message.mediaMetadata);
                    String details = "Отправитель: " + author + "\n\n" + (message.text == null ? "" : message.text);
                    if (mediaPath != null) {
                        details += "\n\nВложение сохранено отдельно.";
                    } else if (message.mediaMetadata != null && message.mediaMetadata.contains("|not_downloaded")) {
                        details += "\n\nВложение не удалось загрузить при создании копии.";
                    }
                    AlertDialog.Builder dialog = new AlertDialog.Builder(context)
                            .setTitle("Полная копия переписки")
                            .setMessage(details)
                            .setNegativeButton("Удалить из копии", (d, which) -> deleteArchivedMessage(message))
                            .setPositiveButton("Закрыть", null);
                    if (mediaPath != null) {
                        dialog.setNeutralButton("Открыть вложение", (d, which) -> openArchivedMedia(context, mediaPath));
                    }
                    dialog.show();
                    return;
                }
                if (message.deletedAt != null) history.append("\n\nСообщение удалено.");
                new AlertDialog.Builder(context).setTitle("Досье сообщения").setMessage(history.toString())
                        .setNeutralButton("Удалить из архива", (dialog, which) -> deleteArchivedMessage(message))
                        .setPositiveButton("Закрыть", null).show();
            });
        });
    }
    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            loadMessages();
        }
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
            bubble.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
            GradientDrawable bubbleBackground = new GradientDrawable();
            bubbleBackground.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            bubbleBackground.setCornerRadius(AndroidUtilities.dp(16));
            bubble.setBackground(bubbleBackground);
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
            holder.body.setText(message.text == null || message.text.isEmpty() ? "Сообщение без текста" : message.text);
            String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(message.date * 1000L));
            String state;
            if (message.archiveType == 1) {
                String author = message.senderId == UserConfig.getInstance(currentAccount).clientUserId ? "ВЫ" : "СОБЕСЕДНИК";
                state = "  ·  ПОЛНАЯ КОПИЯ · " + author;
            } else {
                state = message.deletedAt != null ? "  ·  УДАЛЕНО" : (message.editedAt > 0 ? "  ·  ИЗМЕНЕНО" : "");
            }
            holder.meta.setText(date + state);
            holder.itemView.setOnClickListener(v -> showMessageHistory(message));
            holder.itemView.setOnLongClickListener(v -> {
                Context context = getParentActivity();
                if (context != null) new AlertDialog.Builder(context)
                        .setTitle("Удалить запись?")
                        .setMessage("Запись и её история изменений будут удалены только из Папки № 42.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Удалить", (dialog, which) -> deleteArchivedMessage(message)).show();
                return true;
            });
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
