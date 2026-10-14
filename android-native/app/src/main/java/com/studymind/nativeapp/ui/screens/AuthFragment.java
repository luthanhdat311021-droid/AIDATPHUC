package com.studymind.nativeapp.ui.screens;

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;

import com.studymind.nativeapp.BuildConfig;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.StudyMindApp;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.AuthRepository;
import com.studymind.nativeapp.ui.Ui;

import java.util.Set;

/**
 * Sign in / sign up — same look and copy as the web AuthView on a phone, same Supabase accounts.
 * Google opens in a Custom Tab (Google refuses OAuth in embedded views) and comes back through
 * com.studymind.nativeapp://auth/callback, handled by MainActivity.
 */
public class AuthFragment extends Fragment {
    private static final int TEAL = 0xFF0F766E, SLATE_500 = 0xFF64748B;

    private AuthRepository auth;
    private boolean signup;
    private boolean loading;
    private boolean passwordVisible;

    private TextView formTitle, formSubtitle, tabLogin, tabSignup, errorText, submitText;
    private View errorBox, socialBox, nameGroup, submit, submitContent, submitProgress, forgot;
    private EditText fullName, email, password;
    private ImageView togglePassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        View v = inflater.inflate(R.layout.fragment_auth, container, false);
        auth = StudyMindApp.auth();

        TextView brand = v.findViewById(R.id.brandName);
        SpannableString name = new SpannableString("StudyMind AI");
        name.setSpan(new ForegroundColorSpan(0xFF99F6E4), 10, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(name);

        LinearLayout features = v.findViewById(R.id.features);
        addFeature(features, R.drawable.ic_zap, "Xử lý siêu tốc với Groq LPU & Gemini 3.6", "Phân tích chuyên sâu văn bản & đa phương tiện chỉ trong vài giây.");
        addFeature(features, R.drawable.ic_fork, "Tự động dựng Sơ đồ tư duy & Flashcards", "Dễ dàng ôn tập theo phương pháp Spaced Repetition khoa học.");
        addFeature(features, R.drawable.ic_shield_check, "Đồng bộ đám mây với Supabase", "Lưu trữ dữ liệu học tập an toàn, truy cập mọi lúc mọi nơi.");

        formTitle = v.findViewById(R.id.formTitle);
        formSubtitle = v.findViewById(R.id.formSubtitle);
        tabLogin = v.findViewById(R.id.tabLogin);
        tabSignup = v.findViewById(R.id.tabSignup);
        errorBox = v.findViewById(R.id.errorBox);
        errorText = v.findViewById(R.id.errorText);
        socialBox = v.findViewById(R.id.socialBox);
        nameGroup = v.findViewById(R.id.nameGroup);
        fullName = v.findViewById(R.id.fullName);
        email = v.findViewById(R.id.email);
        password = v.findViewById(R.id.password);
        togglePassword = v.findViewById(R.id.togglePassword);
        forgot = v.findViewById(R.id.forgot);
        submit = v.findViewById(R.id.submit);
        submitContent = v.findViewById(R.id.submitContent);
        submitProgress = v.findViewById(R.id.submitProgress);
        submitText = v.findViewById(R.id.submitText);

        tabLogin.setOnClickListener(x -> setMode(false));
        tabSignup.setOnClickListener(x -> setMode(true));
        togglePassword.setOnClickListener(x -> {
            passwordVisible = !passwordVisible;
            password.setInputType(InputType.TYPE_CLASS_TEXT | (passwordVisible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            password.setSelection(password.getText().length());
            togglePassword.setImageResource(passwordVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        });
        forgot.setOnClickListener(x -> forgotPassword());
        submit.setOnClickListener(x -> submit());
        v.findViewById(R.id.googleBtn).setOnClickListener(x -> openGoogle());
        password.setOnEditorActionListener((t, action, e) -> { submit(); return true; });

        bindPrivacy(v.findViewById(R.id.privacy));
        setMode(false);

        // Only providers switched on in Supabase get a button, so none is ever a dead end
        Async.run(auth::enabledProviders, new Async.Callback<Set<String>>() {
            @Override public void onSuccess(Set<String> p) { if (isAdded()) socialBox.setVisibility(p.contains("google") ? View.VISIBLE : View.GONE); }
            @Override public void onError(String m) {}
        });
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the Google Custom Tab without finishing (closed it): let the user try again
        if (loading && !auth.isSignedIn()) setLoading(false);
    }

    private void addFeature(LinearLayout parent, int icon, String title, String sub) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        ImageView iv = new ImageView(requireContext());
        iv.setImageResource(icon);
        iv.setBackgroundResource(R.drawable.bg_feature_icon);
        int p = Ui.dp(requireContext(), 6);
        iv.setPadding(p, p, p, p);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(requireContext(), 28), Ui.dp(requireContext(), 28));
        ilp.topMargin = Ui.dp(requireContext(), 2);
        row.addView(iv, ilp);

        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(requireContext());
        s.setText(sub);
        s.setTextColor(0xCCCCFBF1);
        s.setTextSize(11);
        texts.addView(t);
        texts.addView(s);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Ui.dp(requireContext(), 12));
        row.addView(texts, tlp);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) rlp.topMargin = Ui.dp(requireContext(), 14);
        parent.addView(row, rlp);
    }

    private void setMode(boolean toSignup) {
        signup = toSignup;
        hideError();
        formTitle.setText(signup ? "Tạo tài khoản mới" : "Chào mừng bạn trở lại");
        formSubtitle.setText(signup
                ? "Đăng ký ngay để trải nghiệm đầy đủ các tính năng AI hỗ trợ học tập."
                : "Vui lòng nhập thông tin tài khoản để tiếp tục tiến trình học tập.");
        styleTab(tabLogin, !signup);
        styleTab(tabSignup, signup);
        nameGroup.setVisibility(signup ? View.VISIBLE : View.GONE);
        forgot.setVisibility(signup ? View.GONE : View.VISIBLE);
        submitText.setText(signup ? "Đăng ký tài khoản ngay" : "Đăng nhập vào tài khoản");
    }

    /** Active segment: white pill, teal heavy text; inactive: slate text on the grey track */
    private void styleTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.bg_segment_active : 0);
        tab.setElevation(active ? Ui.dp(requireContext(), 2) : 0);
        tab.setTextColor(active ? TEAL : SLATE_500);
        tab.setTypeface(active ? Typeface.create("sans-serif-black", Typeface.NORMAL) : Typeface.DEFAULT_BOLD);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorBox.setVisibility(View.VISIBLE);
    }

    private void hideError() { errorBox.setVisibility(View.GONE); }

    private void setLoading(boolean on) {
        loading = on;
        submit.setEnabled(!on);
        submitContent.setVisibility(on ? View.INVISIBLE : View.VISIBLE);
        submitProgress.setVisibility(on ? View.VISIBLE : View.GONE);
        socialBox.setAlpha(on ? 0.6f : 1f);
    }

    private void submit() {
        if (loading) return;
        hideError();
        String name = fullName.getText().toString().trim();
        String mail = email.getText().toString().trim();
        String pass = password.getText().toString();
        if (signup && name.isEmpty()) { showError("Vui lòng nhập họ và tên."); return; }
        if (mail.isEmpty() || pass.isEmpty()) { showError("Vui lòng nhập email và mật khẩu."); return; }

        setLoading(true);
        boolean isSignup = signup;
        Async.run(() -> {
            if (isSignup) return auth.signUp(name, mail, pass);
            auth.signIn(mail, pass);
            return true;
        }, new Async.Callback<Boolean>() {
            @Override public void onSuccess(Boolean hasSession) {
                if (!isAdded()) return; // signed in: MainActivity already swapped to the app
                setLoading(false);
                if (!hasSession) {
                    // "Confirm email" is on in Supabase: no session until the link is clicked
                    setMode(false);
                    showError("Đã gửi email xác nhận tới " + mail.toLowerCase() + ". Hãy bấm vào liên kết trong email rồi quay lại đăng nhập.");
                }
            }
            @Override public void onError(String m) {
                if (!isAdded()) return;
                setLoading(false);
                showError(m != null ? m : isSignup ? "Đăng ký tài khoản thất bại." : "Email hoặc mật khẩu không chính xác.");
            }
        });
    }

    private void forgotPassword() {
        String mail = email.getText().toString().trim();
        if (mail.isEmpty()) {
            showError("Nhập địa chỉ email ở trên rồi bấm \"Quên mật khẩu?\" để nhận liên kết đặt lại mật khẩu.");
            email.requestFocus();
            return;
        }
        hideError();
        Async.run(() -> { auth.recoverPassword(mail); return true; }, new Async.Callback<Boolean>() {
            @Override public void onSuccess(Boolean r) { if (isAdded()) host().toast("Đã gửi liên kết đặt lại mật khẩu tới " + mail.toLowerCase()); }
            @Override public void onError(String m) { if (isAdded()) showError(m); }
        });
    }

    private void openGoogle() {
        if (loading) return;
        hideError();
        setLoading(true);
        new CustomTabsIntent.Builder().setShowTitle(true).build()
                .launchUrl(requireContext(), Uri.parse(auth.oauthUrl("google")));
    }

    /** "Bằng cách đăng nhập, bạn đồng ý với Chính sách quyền riêng tư của StudyMind AI." */
    private void bindPrivacy(TextView tv) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Bằng cách đăng nhập, bạn đồng ý với ");
        int start = sb.length();
        sb.append("Chính sách quyền riêng tư");
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View w) {
                new CustomTabsIntent.Builder().build().launchUrl(requireContext(), Uri.parse(BuildConfig.WEB_URL + "/privacy.html"));
            }
            @Override public void updateDrawState(@NonNull android.text.TextPaint ds) {
                ds.setColor(TEAL);
                ds.setUnderlineText(false);
            }
        }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" của StudyMind AI.");
        tv.setText(sb);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private AppState.Host host() { return (AppState.Host) requireActivity(); }
}
