package com.studymind.nativeapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.AuthRepository;
import com.studymind.nativeapp.data.Gamification;
import com.studymind.nativeapp.data.model.Models.AppUser;
import com.studymind.nativeapp.ui.ImageLoader;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.PomodoroDialog;
import com.studymind.nativeapp.ui.dialogs.ProfileDialog;
import com.studymind.nativeapp.ui.dialogs.StreakDialog;
import com.studymind.nativeapp.ui.dialogs.UpgradeSheet;
import com.studymind.nativeapp.ui.screens.AdminFragment;
import com.studymind.nativeapp.ui.screens.AuthFragment;
import com.studymind.nativeapp.ui.screens.DashboardFragment;
import com.studymind.nativeapp.ui.screens.FlashcardFragment;
import com.studymind.nativeapp.ui.screens.FusionFragment;
import com.studymind.nativeapp.ui.screens.GapsFragment;
import com.studymind.nativeapp.ui.screens.HistoryFragment;
import com.studymind.nativeapp.ui.screens.ImportFragment;
import com.studymind.nativeapp.ui.screens.MindmapFragment;
import com.studymind.nativeapp.ui.screens.PrerequisiteFragment;
import com.studymind.nativeapp.ui.screens.ProFragment;
import com.studymind.nativeapp.ui.screens.QuizFragment;
import com.studymind.nativeapp.ui.screens.WorkspaceFragment;

import java.util.List;

/**
 * Single-activity shell mirroring the web App.tsx: Header.tsx on top, Sidebar.tsx as the navigation
 * drawer, one screen in between. Signed-out users only see the sign-in screen, like the web app.
 */
public class MainActivity extends AppCompatActivity implements AppState.Host, AppState.Listener, AuthRepository.UserListener {

    private static final Object[][] NAV = {
            {Screen.DASHBOARD, "Tổng quan", R.drawable.ic_dashboard},
            {Screen.IMPORT, "Nhập tài liệu", R.drawable.ic_upload},
            {Screen.HISTORY, "Lịch sử bài học", R.drawable.ic_history},
            {Screen.WORKSPACE, "Không gian tài liệu", R.drawable.ic_book},
            {Screen.PREREQUISITE, "Kiến thức tiên quyết", R.drawable.ic_compass},
            {Screen.GAPS, "Bản đồ lỗ hổng", R.drawable.ic_network},
            {Screen.MINDMAP, "Sơ đồ tư duy", R.drawable.ic_mindmap},
            {Screen.FLASHCARD, "Thẻ ghi nhớ", R.drawable.ic_layers},
            {Screen.QUIZ, "Trắc nghiệm", R.drawable.ic_quiz},
            {Screen.FUSION, "Hợp nhất kiến thức", R.drawable.ic_compare},
            {Screen.PRO, "Tài khoản Pro", R.drawable.ic_crown},
    };

    public interface OnImage { void picked(Uri uri); }

    private OnImage pendingImage;
    private final androidx.activity.result.ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> { if (uri != null && pendingImage != null) pendingImage.picked(uri); pendingImage = null; });

    /** Lets dialogs (profile avatar) pick an image; launchers must be registered on the activity */
    public void pickImage(OnImage cb) {
        pendingImage = cb;
        imagePicker.launch("image/*");
    }

    private AppState state;
    private DrawerLayout drawer;
    private View header;
    private LinearLayout navList;
    private Screen current;
    private boolean signedInShell;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        state = StudyMindApp.state();
        drawer = findViewById(R.id.drawer);
        header = findViewById(R.id.header);
        navList = findViewById(R.id.navList);

        SpannableString brand = new SpannableString("StudyMind AI");
        brand.setSpan(new ForegroundColorSpan(Ui.color(this, R.color.brand)), 10, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.sideBrand)).setText(brand);

        Ui.circle(findViewById(R.id.headerAvatar));
        Ui.circle(findViewById(R.id.avatar));

        findViewById(R.id.menuBtn).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.sideClose).setOnClickListener(v -> drawer.closeDrawer(GravityCompat.START));
        findViewById(R.id.searchBox).setOnClickListener(v -> navigate(Screen.HISTORY));
        findViewById(R.id.streakPill).setOnClickListener(v -> StreakDialog.show(this));
        findViewById(R.id.timerBtn).setOnClickListener(v -> PomodoroDialog.show(this));
        findViewById(R.id.themeBtn).setOnClickListener(v -> toggleTheme());
        findViewById(R.id.bellBtn).setOnClickListener(v -> toast("🔔 Nhắc học mỗi ngày lúc 20:00 đang bật."));
        findViewById(R.id.headerAvatar).setOnClickListener(this::showAccountMenu);
        findViewById(R.id.profile).setOnClickListener(v -> { drawer.closeDrawer(GravityCompat.START); ProfileDialog.show(this); });
        findViewById(R.id.logoutBtn).setOnClickListener(v -> state.logout(this::showAuth));
        MaterialSwitch sw = findViewById(R.id.themeSwitch);
        sw.setChecked(state.isDarkMode());
        sw.setOnCheckedChangeListener((b, checked) -> { if (checked != state.isDarkMode()) toggleTheme(); });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START);
                else if (signedInShell && current != Screen.DASHBOARD) navigate(Screen.DASHBOARD);
                else finish();
            }
        });

        state.setHost(this);
        if (state.auth().isSignedIn()) showSignedIn(true);
        else showAuth();
        handleDeepLink(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        state.addListener(this);
        state.auth().addListener(this);
    }

    @Override
    protected void onStop() {
        state.removeListener(this);
        state.auth().removeListener(this);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    /** Google sign-in comes back through com.studymind.nativeapp://auth/callback#access_token=… */
    private void handleDeepLink(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null || !data.toString().startsWith(AuthRepository.NATIVE_AUTH_REDIRECT)) return;
        intent.setData(null);
        Async.run(() -> { state.auth().handleOAuthCallback(data); return true; }, new Async.Callback<Boolean>() {
            @Override public void onSuccess(Boolean r) { toast("Đăng nhập thành công!"); }
            @Override public void onError(String m) {
                toast(m != null && m.toLowerCase().contains("denied") ? "Bạn đã hủy đăng nhập. Hãy thử lại hoặc dùng email." : "Đăng nhập thất bại: " + m);
            }
        });
    }

    // ---------- shells ----------

    private void showAuth() {
        signedInShell = false;
        header.setVisibility(View.GONE);
        statusBar(0xFF64748B, false); // the dimmed cloud background of the sign-in page
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        current = null;
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new AuthFragment()).commitAllowingStateLoss();
    }

    private void showSignedIn(boolean freshStart) {
        signedInShell = true;
        header.setVisibility(View.VISIBLE);
        statusBar(Ui.color(this, R.color.surface), !state.isDarkMode()); // continues the white header
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        state.loadAccount();
        announceBadges(Gamification.updateActivityStreak());
        bindChrome();
        if (freshStart || current == null) navigate(Screen.DASHBOARD);
    }

    @Override
    public void onUserChanged(AppUser user) {
        if (user == null && signedInShell) {
            state.clearAccount();
            showAuth();
        } else if (user != null && !signedInShell) {
            showSignedIn(true);
        } else {
            bindChrome();
        }
    }

    @Override
    public void onStateChanged() {
        if (signedInShell) bindChrome();
    }

    /** Header avatar + streak, Sidebar items, theme row and user card */
    private void bindChrome() {
        AppUser u = state.auth().getUser();
        ((TextView) findViewById(R.id.streakText)).setText(String.valueOf(Gamification.load().streak));
        ImageLoader.load(findViewById(R.id.headerAvatar), u != null ? u.avatarUrl : null);
        ImageLoader.load(findViewById(R.id.avatar), u != null ? u.avatarUrl : null);
        ((TextView) findViewById(R.id.name)).setText(u != null ? u.fullName : "");
        ((TextView) findViewById(R.id.tier)).setText("✦ " + state.tierLabel());

        boolean dark = state.isDarkMode();
        ((ImageView) findViewById(R.id.themeIcon)).setImageResource(dark ? R.drawable.ic_dark : R.drawable.ic_sun);
        ((ImageView) findViewById(R.id.themeIcon)).setColorFilter(dark ? 0xFF818CF8 : 0xFFF59E0B);
        ((TextView) findViewById(R.id.themeLabel)).setText(dark ? "Giao diện Tối" : "Giao diện Sáng");
        ImageView themeBtn = findViewById(R.id.themeBtn);
        themeBtn.setImageResource(dark ? R.drawable.ic_sun : R.drawable.ic_dark);
        themeBtn.setColorFilter(dark ? 0xFFFBBF24 : Ui.color(this, R.color.text_secondary));

        buildNav();
    }

    private void buildNav() {
        navList.removeAllViews();
        boolean pro = state.isPro();
        for (Object[] item : NAV) {
            Screen s = (Screen) item[0];
            navList.addView(navItem(s, (String) item[1], (int) item[2], s == Screen.FUSION && !pro));
        }
        if (state.isAdmin()) navList.addView(navItem(Screen.ADMIN, "Quản trị", R.drawable.ic_shield, false));

        // Upgrade card under the menu (Sidebar.tsx) for signed-in free users
        if (!pro && state.billing() != null) {
            LinearLayout card = Ui.vertical(this);
            card.setBackground(Ui.gradient(this, 16, 0xFF0F766E, 0xFF134E4A));
            card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
            card.addView(Ui.iconText(this, R.drawable.ic_crown, "StudyMind Pro", 12, 0xFFFCD34D, true));
            TextView sub = Ui.text(this, "Mở khóa phân tích Chuyên sâu & Knowledge Fusion.", 11, R.color.brand_soft, false);
            sub.setTextColor(0xFFCCFBF1);
            Ui.add(card, sub, 4);
            TextView btn = Ui.text(this, "Nâng cấp ngay", 12, R.color.brand, true);
            btn.setTextColor(0xFF0F766E);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(Ui.rounded(this, android.R.color.white, 8, 0));
            btn.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            btn.setOnClickListener(v -> { drawer.closeDrawer(GravityCompat.START); openUpgrade(null); });
            Ui.add(card, btn, 12);
            Ui.add(navList, card, 16);
        }
    }

    /** Web nav item: rounded-lg; active = bg #CCFBF1 + teal semibold, else #4B5563 with slate-400 icon */
    private View navItem(Screen s, String label, int icon, boolean proBadge) {
        boolean active = s == current;
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        if (active) row.setBackground(Ui.rounded(this, R.color.brand_soft, 8, 0));
        row.addView(Ui.icon(this, icon, 16, active ? Ui.color(this, R.color.brand) : Ui.color(this, R.color.slate_400)));
        TextView t = Ui.text(this, label, 14, active ? R.color.brand : R.color.text_secondary, active);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.dp(this, 12));
        row.addView(t, lp);
        if (proBadge) row.addView(Ui.pill(this, "PRO", R.color.amber_soft, R.color.amber_700));
        row.setOnClickListener(v -> { drawer.closeDrawer(GravityCompat.START); navigate(s); });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Ui.dp(this, 4);
        row.setLayoutParams(rlp);
        return row;
    }

    /** Header avatar dropdown: name + e-mail, edit profile, Pro account, sign out */
    private void showAccountMenu(View anchor) {
        AppUser u = state.auth().getUser();
        LinearLayout box = Ui.vertical(this);
        box.setBackground(Ui.rounded(this, R.color.surface, 16, R.color.border));
        box.setElevation(Ui.dp(this, 8));
        int p = Ui.dp(this, 8);
        box.setPadding(p, p, p, p);

        LinearLayout who = Ui.vertical(this);
        who.setBackground(Ui.rounded(this, R.color.slate_50, 12, R.color.slate_100));
        who.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        who.addView(Ui.text(this, u != null ? u.fullName : "", 12, R.color.text, true));
        who.addView(Ui.text(this, u != null && u.email != null ? u.email : "", 11, R.color.text_muted, false));
        box.addView(who);

        PopupWindow popup = new PopupWindow(box, Ui.dp(this, 220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(Ui.dp(this, 8));
        box.addView(menuRow(R.drawable.ic_person, "Chỉnh sửa hồ sơ", R.color.brand, R.color.text_secondary, () -> { popup.dismiss(); ProfileDialog.show(this); }));
        box.addView(menuRow(R.drawable.ic_crown, "Tài khoản Pro", R.color.amber, R.color.text_secondary, () -> { popup.dismiss(); navigate(Screen.PRO); }));
        box.addView(menuRow(R.drawable.ic_logout, "Đăng xuất tài khoản", R.color.rose, R.color.rose, () -> { popup.dismiss(); state.logout(this::showAuth); }));
        popup.showAsDropDown(anchor, -Ui.dp(this, 186), Ui.dp(this, 8));
    }

    private View menuRow(int icon, String label, int iconColor, int textColor, Runnable onClick) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.addView(Ui.icon(this, icon, 16, Ui.color(this, iconColor)));
        TextView t = Ui.text(this, label, 12, textColor, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Ui.dp(this, 10));
        row.addView(t, lp);
        row.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 4);
        row.setLayoutParams(rlp);
        return row;
    }

    /** DrawerLayout paints the status-bar area itself (fitsSystemWindows); match it to the screen below */
    private void statusBar(int color, boolean darkIcons) {
        drawer.setStatusBarBackgroundColor(color);
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), drawer).setAppearanceLightStatusBars(darkIcons);
    }

    private void toggleTheme() {
        boolean dark = !state.isDarkMode();
        state.setDarkMode(dark);
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** "🎉 Mở khóa Huy hiệu mới: [name] icon" — same toast as the web app */
    public void announceBadges(List<Gamification.Badge> badges) {
        for (Gamification.Badge b : badges) {
            if (!b.id.equals("first_step")) toast("🎉 Mở khóa Huy hiệu mới: [" + b.name + "] " + b.icon);
        }
        TextView streak = findViewById(R.id.streakText);
        if (streak != null) streak.setText(String.valueOf(Gamification.load().streak));
    }

    // ---------- AppState.Host ----------

    @Override
    public void navigate(Screen screen) {
        if (!signedInShell) return;
        current = screen;
        buildNav();
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.content, fragmentFor(screen))
                .commitAllowingStateLoss();
    }

    @Override
    public void toast(String message) {
        View anchor = findViewById(R.id.content);
        if (anchor == null || message == null) return;
        Snackbar bar = Snackbar.make(anchor, message, 4000);
        View v = bar.getView();
        v.setBackground(Ui.rounded(this, R.color.text, 12, 0));
        TextView tv = v.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextColor(Ui.color(this, R.color.surface));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setMaxLines(4);
        }
        bar.show();
    }

    @Override
    public void openUpgrade(String reason) {
        if (signedInShell && !isFinishing()) UpgradeSheet.show(this, reason);
    }

    private static Fragment fragmentFor(Screen s) {
        switch (s) {
            case IMPORT: return new ImportFragment();
            case HISTORY: return new HistoryFragment();
            case WORKSPACE: return new WorkspaceFragment();
            case PREREQUISITE: return new PrerequisiteFragment();
            case GAPS: return new GapsFragment();
            case MINDMAP: return new MindmapFragment();
            case FLASHCARD: return new FlashcardFragment();
            case QUIZ: return new QuizFragment();
            case FUSION: return new FusionFragment();
            case PRO: return new ProFragment();
            case ADMIN: return new AdminFragment();
            default: return new DashboardFragment();
        }
    }
}
