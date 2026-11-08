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
import com.studymind.nativeapp.data.AnalysisJobs;
import com.studymind.nativeapp.data.AnalysisNotifier;
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
import com.studymind.nativeapp.ui.screens.RewardsFragment;
import com.studymind.nativeapp.ui.screens.QuizFragment;
import com.studymind.nativeapp.ui.screens.WorkspaceFragment;

import java.util.List;

/**
 * Single-activity shell mirroring the web App.tsx: Header.tsx on top, Sidebar.tsx as the navigation
 * drawer, one screen in between. Signed-out users only see the sign-in screen, like the web app.
 */
public class MainActivity extends AppCompatActivity implements AppState.Host, AppState.Listener, AuthRepository.UserListener, AnalysisJobs.Listener {

    /** Drawer groups (Stitch "Danh mục Drawer"): HỌC TẬP CÁ NHÂN, then CÔNG CỤ */
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
            {null, "CÔNG CỤ", 0},
            {Screen.FUSION, "Hợp nhất kiến thức", R.drawable.ic_compare},
            {Screen.REWARDS, "Nhiệm vụ & StudyCoin", R.drawable.ic_wallet},
            {Screen.PRO, "Tài khoản Pro", R.drawable.ic_crown},
    };

    /** Top app bar title of each screen */
    private static String titleOf(Screen s) {
        switch (s) {
            case IMPORT: return "Nhập tài liệu";
            case HISTORY: return "Lịch sử bài học";
            case WORKSPACE: return "Không gian tài liệu";
            case PREREQUISITE: return "Kiến thức tiên quyết";
            case GAPS: return "Bản đồ lỗ hổng";
            case MINDMAP: return "Sơ đồ tư duy";
            case FLASHCARD: return "Thẻ ghi nhớ";
            case QUIZ: return "Trắc nghiệm";
            case FUSION: return "Hợp nhất kiến thức";
            case PRO: return "Tài khoản Pro";
            case REWARDS: return "Nhiệm vụ & StudyCoin";
            case ADMIN: return "Quản trị";
            default: return "Tổng quan";
        }
    }

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

    /** The analysis runs either way; a refusal only means no system notification (the sound still plays) */
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {});

    private boolean started;
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
        findViewById(R.id.profile).setOnClickListener(this::showAccountMenu);
        findViewById(R.id.navHome).setOnClickListener(v -> navigate(Screen.DASHBOARD));
        findViewById(R.id.navHistory).setOnClickListener(v -> navigate(Screen.HISTORY));
        findViewById(R.id.navImport).setOnClickListener(v -> navigate(Screen.IMPORT));
        findViewById(R.id.navWorkspace).setOnClickListener(v -> navigate(Screen.WORKSPACE));
        findViewById(R.id.navMore).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
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
        boolean remember = getSharedPreferences("studymind_prefs", MODE_PRIVATE).getBoolean("remember_login", true);
        if (savedInstanceState == null && !remember && state.auth().isSignedIn()) {
            // "Ghi nhớ đăng nhập" was unticked: a fresh app launch starts signed out
            getSharedPreferences("studymind_prefs", MODE_PRIVATE).edit().putBoolean("remember_login", true).apply();
            state.logout(this::showAuth);
        } else if (state.auth().isSignedIn()) showSignedIn(true);
        else showAuth();
        handleDeepLink(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        state.addListener(this);
        state.auth().addListener(this);
        state.jobs().addListener(this);
        bindJobBanner();
    }

    @Override
    protected void onStop() {
        started = false;
        state.removeListener(this);
        state.auth().removeListener(this);
        state.jobs().removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        state.clearHost(this);
        super.onDestroy();
    }

    @Override
    public void onJobsChanged() { bindJobBanner(); }

    private void bindJobBanner() {
        View banner = findViewById(R.id.jobBanner);
        AnalysisJobs.Job job = signedInShell ? state.jobs().current() : null;
        // A running job's own screen already shows its progress
        boolean ownScreen = job != null && job.running() && current == (job.kind == AnalysisJobs.Kind.IMPORT ? Screen.IMPORT
                : job.kind == AnalysisJobs.Kind.PREREQUISITE ? Screen.PREREQUISITE : Screen.FUSION);
        if (job == null || ownScreen) {
            banner.setVisibility(View.GONE);
            return;
        }
        banner.setVisibility(View.VISIBLE);
        boolean running = job.running();
        boolean ok = job.status == AnalysisJobs.Status.DONE;
        ((TextView) findViewById(R.id.jobBannerText)).setText(running ? "Đang phân tích ngầm: " + job.title
                : ok ? "✅ Đã xong: " + job.title : "⚠️ " + job.stage);
        ((TextView) findViewById(R.id.jobBannerAction)).setText(running ? job.percent + "%" : ok ? "Mở" : job.resumable ? "Thử lại" : "Xem");
        View close = findViewById(R.id.jobBannerClose);
        close.setVisibility(running ? View.GONE : View.VISIBLE);
        close.setOnClickListener(v -> state.dismissJob(job));
        android.widget.ProgressBar bar = findViewById(R.id.jobBannerProgress);
        bar.setVisibility(running ? View.VISIBLE : View.GONE);
        bar.setProgress(job.percent);
        banner.setOnClickListener(v -> state.openJob(job));
    }

    @Override
    public Screen visibleScreen() { return started ? current : null; }

    @Override
    public void askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    /** Google sign-in comes back through com.studymind.nativeapp://auth/callback#access_token=… */
    private void handleDeepLink(Intent intent) {
        String jobKind = intent != null ? intent.getStringExtra(AnalysisNotifier.EXTRA_JOB) : null;
        if (jobKind != null) {
            intent.removeExtra(AnalysisNotifier.EXTRA_JOB);
            for (AnalysisJobs.Kind k : AnalysisJobs.Kind.values()) {
                AnalysisJobs.Job job = k.name().equals(jobKind) ? state.jobs().get(k) : null;
                if (job != null && signedInShell) state.openJob(job);
            }
            return;
        }
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "com.studymind.nativeapp".equals(data.getScheme()) && "referral".equals(data.getHost())) {
            String code = data.getQueryParameter("code");
            if (code != null && code.matches("[A-Za-z0-9]{12}")) {
                getSharedPreferences("studymind_referral", MODE_PRIVATE).edit().putString("pending", code.toUpperCase(java.util.Locale.ROOT)).apply();
                if (signedInShell) navigate(Screen.REWARDS);
                toast("Đã lưu mã mời. Hãy xác nhận trong mục Mời bạn.");
            }
            intent.setData(null);
            return;
        }
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
        findViewById(R.id.jobBanner).setVisibility(View.GONE);
        header.setVisibility(View.GONE);
        findViewById(R.id.bottomNav).setVisibility(View.GONE);
        statusBar(Ui.color(this, R.color.bg), !state.isDarkMode()); // continues the sign-in page background
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        current = null;
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new AuthFragment()).commitAllowingStateLoss();
    }

    private void showSignedIn(boolean freshStart) {
        signedInShell = true;
        header.setVisibility(View.VISIBLE);
        findViewById(R.id.bottomNav).setVisibility(View.VISIBLE);
        statusBar(Ui.color(this, R.color.bg), !state.isDarkMode()); // continues the top app bar
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        state.loadAccount();
        announceBadges(Gamification.updateActivityStreak());
        state.syncGamification("open");
        bindChrome();
        if (freshStart || current == null) navigate(Screen.DASHBOARD);
        state.resumePendingImport(); // an analysis cut off when the phone killed the app
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
        ImageView avatar = findViewById(R.id.avatar);
        boolean hasPhoto = u != null && u.avatarUrl != null && !u.avatarUrl.isEmpty();
        // A photo fills the circle; without one the white person glyph sits on the teal disc
        avatar.setImageTintList(hasPhoto ? null : android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        int ap = hasPhoto ? 0 : Ui.dp(this, 10);
        avatar.setPadding(ap, ap, ap, ap);
        ImageLoader.load(avatar, hasPhoto ? u.avatarUrl : null);
        ((TextView) findViewById(R.id.name)).setText(u != null ? u.fullName : "");
        String tier = state.tierLabel();
        ((TextView) findViewById(R.id.tier)).setText(tier.startsWith("Gói") ? tier : "Gói " + tier);

        boolean dark = state.isDarkMode();
        ((ImageView) findViewById(R.id.themeIcon)).setImageResource(dark ? R.drawable.ic_dark : R.drawable.ic_sun);
        ((ImageView) findViewById(R.id.themeIcon)).setColorFilter(Ui.color(this, R.color.text_secondary));
        ((TextView) findViewById(R.id.themeLabel)).setText(dark ? "Giao diện Tối" : "Giao diện Sáng");
        ImageView themeBtn = findViewById(R.id.themeBtn);
        themeBtn.setImageResource(dark ? R.drawable.ic_dark : R.drawable.ic_sun);
        themeBtn.setColorFilter(Ui.color(this, R.color.text_secondary));
        bindBottomNav();

        buildNav();
    }

    /** Bottom navigation: teal icon + bold label on the current destination */
    private void bindBottomNav() {
        Object[][] items = {
                {Screen.DASHBOARD, R.id.navHomeIcon, R.id.navHomeLabel},
                {Screen.HISTORY, R.id.navHistoryIcon, R.id.navHistoryLabel},
                {Screen.WORKSPACE, R.id.navWorkspaceIcon, R.id.navWorkspaceLabel},
        };
        for (Object[] it : items) {
            boolean active = it[0] == current;
            int col = Ui.color(this, active ? R.color.brand : R.color.text_muted);
            ((ImageView) findViewById((int) it[1])).setColorFilter(col);
            TextView l = findViewById((int) it[2]);
            l.setTextColor(col);
            l.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (current != null) ((TextView) findViewById(R.id.headerTitle)).setText(titleOf(current));
    }

    private TextView navSection(String label) {
        TextView t = Ui.text(this, label, 12, R.color.text_muted, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 8));
        return t;
    }

    private void buildNav() {
        navList.removeAllViews();
        boolean pro = state.isPro();
        navList.addView(navSection("HỌC TẬP CÁ NHÂN"));
        for (Object[] item : NAV) {
            Screen s = (Screen) item[0];
            if (s == null) {
                View rule = new View(this);
                rule.setBackgroundColor(Ui.color(this, R.color.border));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
                rl.setMargins(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), 0);
                navList.addView(rule, rl);
                navList.addView(navSection((String) item[1]));
                continue;
            }
            navList.addView(navItem(s, (String) item[1], (int) item[2], s == Screen.FUSION && !pro));
        }
        navList.addView(navItem(null, "Bộ đếm Pomodoro", R.drawable.ic_timer, false));
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
        boolean active = s != null && s == current;
        LinearLayout row = Ui.horizontal(this);
        row.setMinimumHeight(Ui.dp(this, 52));
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        if (active) row.setBackgroundResource(R.drawable.bg_nav_active);
        row.addView(Ui.icon(this, icon, 22, Ui.color(this, active ? R.color.brand : R.color.text_secondary)));
        TextView t = Ui.text(this, label, 15, active ? R.color.brand : R.color.text_secondary, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.dp(this, 18));
        row.addView(t, lp);
        if (proBadge) row.addView(Ui.pill(this, "PRO", R.color.amber_soft, R.color.amber_700));
        row.setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            if (s == null) PomodoroDialog.show(this); else navigate(s);
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Ui.dp(this, 2);
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
        popup.showAsDropDown(anchor, Ui.dp(this, 12), Ui.dp(this, 8));
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
        bindBottomNav();
        bindJobBanner();
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
            case REWARDS: return new RewardsFragment();
            case ADMIN: return new AdminFragment();
            default: return new DashboardFragment();
        }
    }
}
