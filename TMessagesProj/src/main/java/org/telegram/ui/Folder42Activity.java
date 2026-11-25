package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Initial screen for the independent local Folder 42 archive.
 * Telegram's built-in archive remains a separate feature.
 */
public class Folder42Activity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Папка № 42");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout layout = new FrameLayout(context);
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        TextView title = new TextView(context);
        title.setText("Папка № 42");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView description = new TextView(context);
        description.setText("Отдельный архив PotuzhnoGram. Здесь будут отображаться выбранные переписки и сохранённая история изменений.");
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        description.setTextSize(14);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 0, 0, 0);

        FrameLayout content = new FrameLayout(context);
        content.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        content.addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 48, 20, 0));
        layout.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 24, 24, 24));

        fragmentView = layout;
        return fragmentView;
    }
}
