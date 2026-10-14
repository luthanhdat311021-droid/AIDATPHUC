package com.studymind.nativeapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.studymind.nativeapp.StudyMindApp;
import com.studymind.nativeapp.data.AppState;

import java.util.Objects;

/**
 * A scrolling screen rebuilt from {@link AppState}. Subclasses draw into {@link #render(LinearLayout)};
 * {@link #watchKey()} limits rebuilds to changes the screen actually shows, so typing or scrolling
 * is not interrupted by unrelated updates (e.g. the AI quota counter).
 */
public abstract class BaseFragment extends Fragment implements AppState.Listener {
    protected AppState state;
    protected LinearLayout root;
    protected ScrollView scroll;
    private Object lastKey = new Object();

    protected abstract void render(LinearLayout root);

    /** Value whose change triggers a rebuild; null = rebuild on every state change */
    protected Object watchKey() { return null; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        state = StudyMindApp.state();
        scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        root = Ui.vertical(requireContext());
        int p = Ui.dp(requireContext(), 16);
        root.setPadding(p, p, p, Ui.dp(requireContext(), 32));
        scroll.addView(root);
        return scroll;
    }

    @Override
    public void onStart() {
        super.onStart();
        state.addListener(this);
        lastKey = new Object();
        onStateChanged();
    }

    @Override
    public void onStop() {
        state.removeListener(this);
        super.onStop();
    }

    @Override
    public void onStateChanged() {
        if (root == null || !isAdded()) return;
        Object key = watchKey();
        if (key != null && Objects.equals(key, lastKey)) return;
        lastKey = key;
        rerender();
    }

    protected void rerender() {
        if (root == null || !isAdded()) return;
        root.removeAllViews();
        render(root);
    }

    protected void toast(String message) {
        if (getActivity() instanceof AppState.Host) ((AppState.Host) getActivity()).toast(message);
    }

    /** Shown when a lesson-specific screen is opened without a lesson */
    protected boolean requireDocument(LinearLayout root, String what) {
        if (state.activeDoc() != null) return true;
        root.addView(Ui.empty(requireContext(), "Chưa có tài liệu nào được mở",
                "Hãy nhập hoặc mở một tài liệu để " + what + ".",
                "Nhập tài liệu", v -> state.navigate(AppState.Screen.IMPORT)));
        return false;
    }
}
