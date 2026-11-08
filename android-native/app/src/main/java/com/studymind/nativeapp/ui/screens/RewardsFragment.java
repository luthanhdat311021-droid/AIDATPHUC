package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.studymind.nativeapp.BuildConfig;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.BiometricGuard;
import com.studymind.nativeapp.ui.LivenessActivity;
import com.studymind.nativeapp.ui.Ui;
import java.util.UUID;

/** Native mission, custodial token wallet, referral and private challenge experience. */
public class RewardsFragment extends BaseFragment {
    private JsonObject data, session, focus, answers = new JsonObject(), adminData;
    private String tab = "missions", error, topic;
    private boolean busy, loading, visible;
    private TextView focusLabel;
    private String owner;
    private final androidx.activity.result.ActivityResultLauncher<Intent> liveness = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), r -> rerender());

    /** Shows the human check instead of the screen until it has passed. */
    private boolean requireHuman(LinearLayout root) {
        String owner = state.auth().getUser() != null ? state.auth().getUser().id : null;
        if (LivenessActivity.isVerified(owner)) return true;
        Context c = requireContext();
        LinearLayout card = Ui.section(root, "Xác minh bạn là người thật", null, null, 0);
        card.addView(Ui.body(c, "Làm theo 2 yêu cầu trước camera trước, ví dụ chớp mắt hoặc quay đầu. Hình ảnh chỉ xử lý trên máy, không lưu và không gửi đi."));
        Ui.add(card, Ui.primary(c, "Bắt đầu quét khuôn mặt", v -> liveness.launch(LivenessActivity.intent(c, owner))), 12);
        return false;
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            load(false);
            Async.mainDelayed(this, 15000);
        }
    };
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!visible || focus == null) return;
            JsonObject currentFocus = focus;
            request("POST", "/sessions/" + str(currentFocus, "id") + "/heartbeat", new JsonObject(), r -> {
                if (focus != currentFocus || !visible) return;
                focus.addProperty("seconds", number(r, "seconds"));
                if (focusLabel != null) focusLabel.setText(focusText());
            }, false);
            Async.mainDelayed(this, 60000);
        }
    };

    @Override public void onStart() {
        super.onStart();
        visible = true;
        owner = state.auth().getUser() != null ? state.auth().getUser().id : null;
        load(true);
        Async.mainDelayed(poll, 15000);
        if (focus != null) Async.mainDelayed(heartbeat, 1000);
    }
    @Override public void onStop() {
        visible = false;
        Async.cancel(poll); Async.cancel(heartbeat);
        super.onStop();
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            owner = saved.getString("owner"); tab = saved.getString("tab", "missions");
            if (owner != null && com.studymind.nativeapp.StudyMindApp.state().auth().getUser() != null
                    && owner.equals(com.studymind.nativeapp.StudyMindApp.state().auth().getUser().id)) {
                session = parse(saved.getString("session")); focus = parse(saved.getString("focus"));
                JsonObject a = parse(saved.getString("answers")); if (a != null) answers = a;
            }
        }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("owner", owner); out.putString("tab", tab);
        if (session != null) out.putString("session", session.toString());
        if (focus != null) out.putString("focus", focus.toString());
        out.putString("answers", answers.toString());
    }
    private static JsonObject parse(String value) {
        try { return value == null ? null : JsonParser.parseString(value).getAsJsonObject(); }
        catch (RuntimeException e) { return null; }
    }
    private static String str(JsonObject o, String key) { return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : ""; }
    private static int number(JsonObject o, String key) { try { return o.get(key).getAsInt(); } catch (RuntimeException e) { return 0; } }
    /** Network label from the server; falls back to known chain IDs for an older backend. */
    private static String networkName(JsonObject data) {
        String name = str(data, "network");
        if (!name.isEmpty()) return name;
        switch (number(data, "chainId")) {
            case 80002: return "Polygon Amoy";
            case 11155111: return "Ethereum Sepolia";
            default: return "Mạng thử nghiệm cục bộ";
        }
    }
    private static boolean yes(JsonObject o, String key) { return o != null && o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean(); }
    private static JsonArray array(JsonObject o, String key) { return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray(); }
    private interface Result { void done(JsonObject result); }

    private void request(String method, String path, JsonObject body, Result done, boolean lock) {
        if (lock && busy) return;
        if (lock) { busy = true; rerender(); }
        state.rewards().request(method, path, body, new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject result) {
                if (!isAdded()) return;
                if (lock) busy = false;
                done.done(result);
                if (lock) rerender();
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                if (lock) busy = false;
                error = message;
                if (lock) { toast(message); rerender(); }
            }
        });
    }

    private void load(boolean show) {
        if (loading || !isAdded()) return;
        loading = true;
        if (show && data == null) { error = null; rerender(); }
        state.rewards().request("GET", "/me", null, new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                loading = false;
                if (!isAdded()) return;
                data = r; error = null;
                if (show || session == null) rerender();
                state.refreshBilling(null);
            }
            @Override public void onError(String m) {
                loading = false;
                if (!isAdded()) return;
                error = m;
                if (show || data == null) rerender();
            }
        });
    }
    @Override protected Object watchKey() { return "rewards"; }

    @Override protected void render(LinearLayout root) {
        root.setEnabled(true);
        Context c = requireContext();
        root.addView(Ui.title(c, "Nhiệm vụ & StudyCoin"));
        Ui.add(root, Ui.muted(c, "Học tập, nhận token và đổi thời gian sử dụng Pro."), 6);
        if (!requireHuman(root)) return;
        if (busy) Ui.add(root, Ui.muted(c, "Đang xử lý…"), 8);
        if (data == null || !yes(data, "enabled")) {
            LinearLayout box = Ui.vertical(c);
            box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            box.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
            box.setPadding(Ui.dp(c, 20), Ui.dp(c, 24), Ui.dp(c, 20), Ui.dp(c, 20));
            String msg;
            if (data == null && loading) {
                box.addView(Ui.spinner(c));
                msg = "Đang tải thông tin StudyCoin…";
            } else if (data == null) {
                box.addView(Ui.iconBox(c, R.drawable.ic_alert, R.color.rose_soft, R.color.m3_error, 52, 26, 999));
                msg = error != null && error.contains("quá lâu") || error != null && error.toLowerCase().contains("timeout")
                        ? "Máy chủ StudyCoin phản hồi quá lâu. Hãy thử lại sau ít phút."
                        : error != null ? error : "Chưa tải được thông tin StudyCoin.";
            } else {
                box.addView(Ui.iconBox(c, R.drawable.ic_wallet, R.color.m3_tertiary_fixed, R.color.m3_tertiary, 52, 26, 999));
                msg = "StudyCoin đang chờ máy chủ bật mạng token thử nghiệm.";
            }
            TextView mt = Ui.body(c, msg);
            mt.setGravity(android.view.Gravity.CENTER);
            Ui.add(box, mt, 12);
            if (!loading) Ui.add(box, Ui.primary(c, "Tải lại", v -> load(true)), 14);
            Ui.add(root, box, 20);
            return;
        }
        if (error != null) Ui.add(root, Ui.muted(c, error), 10);
        LinearLayout hero = Ui.section(root, "Ví StudyCoin", null, null, 16);
        hero.addView(Ui.text(c, yes(data,"chainAvailable") ? number(data,"balance") + " SC đã xác nhận" : "Chưa đọc được số dư blockchain", 22, R.color.brand, true));
        Ui.add(hero, Ui.muted(c, "Chờ nhận: " + number(data,"pendingMint") + " SC · Chờ đổi: " + number(data,"pendingBurn") + " SC"), 6);
        Ui.add(hero, Ui.muted(c, "Ví do StudyMind quản lý · " + networkName(data) + " · Hệ thống tài trợ phí mạng"), 6);
        Ui.add(hero, Ui.muted(c, "Token chưa hỗ trợ chuyển nhượng. Pro chỉ được cấp sau khi burn được xác nhận."), 6);
        TextView address = Ui.muted(c, str(data,"wallet")); address.setTextIsSelectable(true); Ui.add(hero,address,8);
        LinearLayout tabs = Ui.horizontal(c);
        String[][] choices = {{"missions","Nhiệm vụ"},{"wallet","Đổi Pro"},{"referral","Mời bạn"}};
        for (String[] choice : choices) Ui.addWeighted(tabs, Ui.choice(c,choice[1],tab.equals(choice[0]),v -> { session=null; tab=choice[0]; rerender(); }), 4);
        Ui.add(root,tabs,16);
        if (state.isAdmin()) Ui.add(root,Ui.outline(c,"Quản trị StudyCoin",v -> {
            tab="admin";
            request("GET","/admin",null,r -> { adminData=r; rerender(); },true);
        }),8);
        if (focus != null) drawFocus(root);
        if (session != null && "missions".equals(tab)) drawSession(root);
        else if ("wallet".equals(tab)) drawWallet(root);
        else if ("referral".equals(tab)) drawReferrals(root);
        else if ("admin".equals(tab)) drawAdmin(root);
        else drawMissions(root);
        Ui.add(root,Ui.outline(c,"Làm mới trạng thái",v -> load(true)),16);
        if (busy) disableControls(root);
    }

    private static void disableControls(android.view.View view) {
        view.setEnabled(false);
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)disableControls(group.getChildAt(i));
        }
    }

    private void drawMissions(LinearLayout root) {
        Context c=requireContext();
        for (JsonElement item : array(data,"missions")) {
            JsonObject m=item.getAsJsonObject();
            LinearLayout card=Ui.section(root,str(m,"title")+" · "+number(m,"amount")+" SC",null,null,12);
            card.addView(Ui.muted(c,str(m,"difficulty")+" · "+str(m,"description")));
            Ui.add(card,Ui.track(c,number(m,"progress")*100/Math.max(1,number(m,"goal")),R.color.brand,6),10);
            Ui.add(card,Ui.muted(c,number(m,"progress")+"/"+number(m,"goal")+" · "+status(str(m,"status"))),6);
            Ui.add(card,Ui.muted(c,"Hạn: "+Ui.dateTime(str(m,"expiresAt"))),4);
            if ("ready".equals(str(m,"status"))) {
                android.view.View claim=Ui.primary(c,"Nhận "+number(m,"amount")+" SC",v ->
                    request("POST","/missions/"+str(m,"id")+"/claim",new JsonObject(),r -> {
                        toast("Đã gửi yêu cầu nhận token. Hãy chờ blockchain xác nhận."); load(true);
                    },true));
                claim.setEnabled(!busy); Ui.add(card,claim,8);
            }
        }
        LinearLayout practice=Ui.section(root,"Học để hoàn thành thử thách",null,null,16);
        Ui.add(practice,Ui.muted(c,"Học trong bài học cũng được tính: ôn thẻ và làm quiz (≥10 câu) do AI tạo, Pomodoro 25 phút. Thẻ/câu hỏi bạn tự thêm không được tính. \"Làm chủ 3 chủ đề\" chỉ tính quiz thử thách bên dưới."),2);
        JsonArray topics=array(data,"topics");
        if (topic==null && topics.size()>0) topic=topics.get(0).getAsString();
        for (JsonElement t:topics) {
            String name=t.getAsString();
            Ui.add(practice,Ui.choice(c,name,name.equals(topic),v -> {topic=name;rerender();}),8);
        }
        Ui.add(practice,Ui.primary(c,"Ôn 10 thẻ thử thách",v -> start("review")),12);
        Ui.add(practice,Ui.outline(c,"Làm quiz nhận thưởng",v -> start("quiz")),8);
        if (focus==null) Ui.add(practice,Ui.outline(c,"Bắt đầu / tiếp tục phiên tập trung",v -> start("focus")),8);
    }

    private void start(String kind) {
        JsonObject b=new JsonObject(); b.addProperty("kind",kind); b.addProperty("topic",topic);
        request("POST","/sessions/start",b,r -> {
            if ("focus".equals(kind)) {
                focus=r; Async.cancel(heartbeat); Async.mainDelayed(heartbeat,60000);
            } else {session=r;answers=new JsonObject();}
            rerender();
        },true);
    }
    private void drawSession(LinearLayout root) {
        Context c=requireContext();
        if (yes(session,"finished")) {
            LinearLayout card=Ui.section(root,"Đã hoàn thành phiên học",null,null,16);
            JsonObject result=session.has("result")&&!session.get("result").isJsonNull()?session.getAsJsonObject("result"):new JsonObject();
            card.addView(Ui.body(c,result.has("score")?"Kết quả: "+number(result,"score")+"%":"Đã hoàn thành lượt ôn."));
            Ui.add(card,Ui.primary(c,"Xem tiến độ nhiệm vụ",v->{session=null;load(true);rerender();}),12); return;
        }
        LinearLayout card=Ui.section(root,str(session,"topic"),null,null,16);
        card.addView(Ui.muted(c,"Hết hạn: "+Ui.dateTime(str(session,"expiresAt"))));
        if ("quiz".equals(str(session,"kind"))) {
            int n=0;
            for (JsonElement item:array(session,"questions")) {
                JsonObject q=item.getAsJsonObject(); String id=str(q,"id");
                Ui.add(card,Ui.body(c,(++n)+". "+str(q,"question")),16);
                RadioGroup group=new RadioGroup(c); group.setOrientation(RadioGroup.VERTICAL);
                JsonArray options=array(q,"options");
                for (int i=0;i<options.size();i++) {
                    RadioButton rb=new RadioButton(c); rb.setId(android.view.View.generateViewId());
                    rb.setText(options.get(i).getAsString()); rb.setTextColor(Ui.color(c,R.color.text));
                    final int answer=i; group.addView(rb);
                    if (answers.has(id)&&number(answers,id)==i) group.check(rb.getId());
                    rb.setOnClickListener(v -> answers.addProperty(id,answer));
                }
                Ui.add(card,group,4);
            }
            Ui.add(card,Ui.primary(c,"Nộp thử thách",v -> {
                if (answers.size()!=array(session,"questions").size()) {toast("Hãy trả lời đủ các câu hỏi.");return;}
                JsonObject b=new JsonObject();b.add("answers",answers);
                request("POST","/sessions/"+str(session,"id")+"/submit",b,r->{
                    session.addProperty("finished",true);session.add("result",r);load(false);
                },true);
            }),12);
        } else {
            JsonObject current=session.getAsJsonObject("card");
            card.addView(Ui.heading(c,"Thẻ "+number(current,"number")+"/"+number(current,"total")));
            Ui.add(card,Ui.body(c,str(current,"front")),16);
            if (!str(current,"back").isEmpty()) {
                Ui.add(card,Ui.text(c,str(current,"back"),18,R.color.brand,true),16);
                Ui.add(card,Ui.primary(c,"Đã ôn · Thẻ tiếp theo",v -> request("POST","/sessions/"+str(session,"id")+"/review",new JsonObject(),r->{
                    request("GET","/sessions/"+str(session,"id"),null,next->{session=next;rerender();load(false);},false);
                },true)),12);
            } else Ui.add(card,Ui.primary(c,"Lật thẻ",v -> request("POST","/sessions/"+str(session,"id")+"/reveal",new JsonObject(),r->{
                current.addProperty("back",str(r,"back"));
            },true)),12);
            Ui.add(card,Ui.muted(c,"Đọc mặt trước ít nhất 3 giây và mặt sau ít nhất 2 giây."),8);
        }
        Ui.add(card,Ui.outline(c,"Về nhiệm vụ (giữ phiên trên máy chủ)",v->{session=null;rerender();}),12);
    }
    private String focusText() {int seconds=number(focus,"seconds");return "Đã xác thực "+(seconds/60)+" phút "+(seconds%60)+" giây / 25 phút";}
    private void drawFocus(LinearLayout root) {
        Context c=requireContext(); LinearLayout card=Ui.section(root,"Phiên tập trung",null,null,12);
        focusLabel=Ui.body(c,focusText());card.addView(focusLabel);
        Ui.add(card,Ui.muted(c,"Giữ màn hình StudyCoin ở phía trước và ôn thẻ/làm quiz tại đây. Rời màn hình hoặc mất mạng sẽ dừng cộng thời gian."),6);
        Ui.add(card,Ui.outline(c,"Hoàn tất phiên tập trung",v->request("POST","/sessions/"+str(focus,"id")+"/finish",new JsonObject(),r->{
            focus=null;Async.cancel(heartbeat);toast("Đã hoàn thành phiên tập trung.");load(true);
        },true)),8);
        Ui.add(card,Ui.outline(c,"Tạm dừng / Ẩn phiên tập trung",v->{
            focus=null;focusLabel=null;Async.cancel(heartbeat);rerender();
        }),4);
    }

    private void drawWallet(LinearLayout root) {
        Context c=requireContext();
        for (JsonElement item:array(data,"catalog")) {
            JsonObject reward=item.getAsJsonObject();
            LinearLayout card=Ui.section(root,str(reward,"title"),null,null,12);
            card.addView(Ui.body(c,number(reward,"amount")+" SC · Cộng dồn hạn Pro hiện có"));
            android.view.View button=Ui.primary(c,"Đổi token lấy Pro",v -> new MaterialAlertDialogBuilder(c)
                .setTitle("Xác nhận đổi "+str(reward,"title"))
                .setMessage("Tiêu "+number(reward,"amount")+" SC trên blockchain. Pro sẽ được cộng sau khi burn được xác nhận; giao dịch đã burn không thể hủy.")
                .setNegativeButton("Để sau",null).setPositiveButton("Xác nhận đổi",(dialog,which)->redeem(reward)).show());
            button.setEnabled(!busy&&yes(data,"chainAvailable")&&number(data,"available")>=number(reward,"amount")&&number(data,"pendingBurn")==0);
            Ui.add(card,button,8);
        }
        LinearLayout history=Ui.section(root,"Giao dịch token gần đây",null,null,20);
        if (array(data,"jobs").size()==0) history.addView(Ui.muted(c,"Chưa có giao dịch."));
        for (JsonElement item:array(data,"jobs")) {
            JsonObject j=item.getAsJsonObject();
            Ui.add(history,Ui.body(c,("mint".equals(str(j,"kind"))?"+":"−")+number(j,"amount")+" SC · "+str(j,"label")),14);
            Ui.add(history,Ui.muted(c,status(str(j,"status"))+" · "+Ui.dateTime(str(j,"created_at"))),4);
            if (!str(j,"error_code").isEmpty()) Ui.add(history,Ui.muted(c,"Hệ thống cần kiểm tra giao dịch: "+str(j,"error_code")),4);
            String hash=str(j,"tx_hash");
            if (!hash.isEmpty()) {
                TextView tx=Ui.muted(c,hash);tx.setTextIsSelectable(true);Ui.add(history,tx,4);
                if (!str(data,"explorer").isEmpty()) Ui.add(history,Ui.outline(c,"Xem trên blockchain",v->startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(str(data,"explorer")+"/tx/"+hash)))),4);
            }
        }
    }
    private void redeem(JsonObject reward) {
        String pref="redeem:"+owner+":"+str(reward,"id")+":"+number(reward,"version");
        android.content.SharedPreferences prefs=requireContext().getSharedPreferences("studymind_reward_requests",Context.MODE_PRIVATE);
        String key=prefs.getString(pref,null);
        if (key==null) {key=UUID.randomUUID().toString();prefs.edit().putString(pref,key).apply();}
        JsonObject b=new JsonObject();b.addProperty("key",key);b.addProperty("catalogId",str(reward,"id"));b.addProperty("version",number(reward,"version"));
        if (!BiometricGuard.available(requireActivity())) {toast("Hãy bật vân tay, khuôn mặt hoặc khóa màn hình trên máy để đổi token.");return;}
        JsonObject enroll=new JsonObject();
        try {enroll.addProperty("publicKey",BiometricGuard.publicKey(owner));}
        catch (Exception e) {toast("Không tạo được khóa bảo mật trên thiết bị này.");return;}
        request("POST","/biometric/enroll",enroll,status->{
            if (!yes(status,"active")) {toast("Thiết bị mới cần chờ 24 giờ trước khi được đổi token, để bảo vệ tài khoản.");return;}
            request("POST","/biometric/challenge",b,challenge->BiometricGuard.sign(requireActivity(),owner,str(challenge,"message"),
                "Tiêu "+number(reward,"amount")+" SC để đổi "+str(reward,"title"),new BiometricGuard.Signed(){
                    @Override public void success(String signature) {
                        if (!isAdded()) return;
                        JsonObject proof=new JsonObject();proof.addProperty("challengeId",str(challenge,"challengeId"));proof.addProperty("signature",signature);
                        b.add("biometric",proof);
                        request("POST","/redemptions",b,r->{prefs.edit().remove(pref).apply();toast("Yêu cầu đổi Pro đang chờ blockchain xác nhận.");load(true);},true);
                    }
                    @Override public void failure(String message) { if (isAdded()) toast(message); }
                }),true);
        },true);
    }

    private void drawReferrals(LinearLayout root) {
        Context c=requireContext();LinearLayout card=Ui.section(root,"Mời bạn cùng học",null,null,16);
        TextView code=Ui.text(c,str(data,"referralCode"),24,R.color.brand,true);code.setTextIsSelectable(true);card.addView(code);
        Ui.add(card,Ui.body(c,"Bạn nhận 200 SC, bạn mới nhận 30 SC sau khi học đủ điều kiện."),8);
        Ui.add(card,Ui.muted(c,"Trong 7 ngày: học ít nhất 2 ngày, ôn 20 thẻ và đạt quiz từ 80%. Sau đó chờ kiểm tra 48 giờ. Tối đa 3 lượt/tháng. Bấm chia sẻ đơn thuần không nhận token."),8);
        Ui.add(card,Ui.primary(c,"Chia sẻ mã mời",v->{
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,"Cùng học với mình trên StudyMind! Mã mời: "+str(data,"referralCode")+"\n"
                +BuildConfig.WEB_URL+"/referral.html?code="+str(data,"referralCode"));
            startActivity(Intent.createChooser(send,"Mời bạn cùng học"));
        }),12);
        if (data.has("bound")&&!data.get("bound").isJsonNull()) {
            Ui.add(card,Ui.muted(c,"Mã đã gắn · "+status(str(data.getAsJsonObject("bound"),"status"))),12);
        } else {
            TextInputLayout field=Ui.input(c,"Mã người giới thiệu (12 ký tự)",false);
            String pending=c.getSharedPreferences("studymind_referral",Context.MODE_PRIVATE).getString("pending","");
            if (field.getEditText()!=null) field.getEditText().setText(pending);
            Ui.add(card,field,16);
            Ui.add(card,Ui.outline(c,"Xác nhận mã giới thiệu",v->{
                String entered=Ui.value(field).toUpperCase(java.util.Locale.ROOT);
                new MaterialAlertDialogBuilder(c).setTitle("Gắn mã "+entered+"?")
                    .setMessage("Mỗi tài khoản chỉ gắn một người giới thiệu, trong 7 ngày đầu và trước khi bắt đầu học nhận thưởng.")
                    .setNegativeButton("Để sau",null).setPositiveButton("Xác nhận",(d,w)->{
                        JsonObject b=new JsonObject();b.addProperty("code",entered);
                        request("POST","/referrals/bind",b,r->{c.getSharedPreferences("studymind_referral",Context.MODE_PRIVATE).edit().remove("pending").apply();load(true);},true);
                    }).show();
            }),8);
        }
        LinearLayout list=Ui.section(root,"Tiến độ các lượt mời",null,null,16);
        int n=0;
        for (JsonElement item:array(data,"referrals")) {
            JsonObject r=item.getAsJsonObject();
            Ui.add(list,Ui.body(c,"Lượt "+(++n)+" · "+status(str(r,"status"))),10);
            if (!str(r,"reason").isEmpty()) Ui.add(list,Ui.muted(c,str(r,"reason")),4);
        }
        if(n==0)list.addView(Ui.muted(c,"Chưa có lượt mời nào được gắn mã."));
    }

    private void drawAdmin(LinearLayout root) {
        Context c=requireContext();
        if(adminData==null){root.addView(Ui.muted(c,"Đang tải quản trị…"));return;}
        if(adminData.has("totals")){
            JsonObject totals=adminData.getAsJsonObject("totals");
            LinearLayout stats=Ui.section(root,"Tổng quan toàn hệ thống",null,null,16);
            stats.addView(Ui.body(c,"Đã mint: "+number(totals,"minted")+" SC · Đã burn: "+number(totals,"burned")+" SC"));
            Ui.add(stats,Ui.muted(c,"Đã đổi: "+number(totals,"proDays")+" ngày Pro · Chờ: "+number(totals,"pending")+" · Lỗi: "+number(totals,"failed")),6);
        }
        LinearLayout rules=Ui.section(root,"Mức thưởng (áp dụng cho nhiệm vụ giao mới)",null,null,16);
        for(JsonElement item:array(adminData,"rules")){
            JsonObject r=item.getAsJsonObject();
            Ui.add(rules,Ui.outline(c,str(r,"title")+" · "+number(r,"amount")+" SC",v->editAmount("rule_amount",str(r,"code"),r)),8);
        }
        LinearLayout prices=Ui.section(root,"Giá đổi Pro",null,null,16);
        for(JsonElement item:array(adminData,"catalog")){
            JsonObject r=item.getAsJsonObject();
            Ui.add(prices,Ui.outline(c,str(r,"title")+" · "+number(r,"amount")+" SC",v->editAmount("catalog_amount",str(r,"id"),r)),8);
        }
        LinearLayout jobs=Ui.section(root,"Đối soát token (100 giao dịch gần nhất)",null,null,16);
        for(JsonElement item:array(adminData,"jobs")){
            JsonObject j=item.getAsJsonObject();
            Ui.add(jobs,Ui.body(c,str(j,"label")+" · "+number(j,"amount")+" SC"),12);
            Ui.add(jobs,Ui.muted(c,str(j,"id")+" · "+status(str(j,"status"))),4);
            if(!str(j,"error_code").isEmpty())Ui.add(jobs,Ui.muted(c,str(j,"error_code")),4);
            if("failed".equals(str(j,"status")))Ui.add(jobs,Ui.outline(c,"Kiểm tra và gửi lại",v->
                new MaterialAlertDialogBuilder(c).setTitle("Gửi lại giao dịch chưa thành công?")
                    .setMessage("Chỉ dùng sau khi đã khắc phục nguyên nhân trên máy chủ. Hệ thống giữ nguyên mã thao tác để ngăn xử lý trùng.")
                    .setNegativeButton("Để sau",null).setPositiveButton("Gửi lại",(d,w)->{
                        JsonObject b=new JsonObject();b.addProperty("action","retry_job");b.addProperty("id",str(j,"id"));adminAction(b);
                    }).show()),6);
        }
        LinearLayout refs=Ui.section(root,"Lượt mời cần theo dõi",null,null,16);
        for(JsonElement item:array(adminData,"referrals")){
            JsonObject r=item.getAsJsonObject();
            Ui.add(refs,Ui.muted(c,str(r,"id")+" · "+status(str(r,"status"))),10);
            if("waiting".equals(str(r,"status"))||"learning".equals(str(r,"status")))Ui.add(refs,Ui.outline(c,"Từ chối và ghi lý do",v->{
                TextInputLayout reason=Ui.input(c,"Lý do cụ thể",true);
                new MaterialAlertDialogBuilder(c).setTitle("Kiểm tra lượt giới thiệu").setView(reason)
                    .setNegativeButton("Để sau",null).setPositiveButton("Từ chối",(d,w)->{
                        JsonObject b=new JsonObject();b.addProperty("action","reject_referral");b.addProperty("id",str(r,"id"));b.addProperty("reason",Ui.value(reason));adminAction(b);
                    }).show();
            }),4);
        }
        Ui.add(root,Ui.muted(c,"Thay đổi cấu hình và xử lý giao dịch phải được ghi audit trên máy chủ."),16);
    }

    private void editAmount(String action,String id,JsonObject item){
        Context c=requireContext();TextInputLayout field=Ui.input(c,"Số SC",false);
        if(field.getEditText()!=null){field.getEditText().setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.getEditText().setText(String.valueOf(number(item,"amount")));}
        new MaterialAlertDialogBuilder(c).setTitle(str(item,"title")).setView(field)
            .setNegativeButton("Để sau",null).setPositiveButton("Lưu",(d,w)->{
                int value;try{value=Integer.parseInt(Ui.value(field));}catch(NumberFormatException e){toast("Hãy nhập số nguyên hợp lệ.");return;}
                JsonObject b=new JsonObject();b.addProperty("action",action);b.addProperty("id",id);b.addProperty("value",value);adminAction(b);
            }).show();
    }
    private void adminAction(JsonObject body){
        request("POST","/admin/actions",body,r->{
            toast("Đã lưu thay đổi và nhật ký quản trị.");
            request("GET","/admin",null,next->{adminData=next;rerender();},false);load(false);
        },true);
    }

    private static String status(String code) {
        switch(code){
            case "ready":return "Đủ điều kiện nhận";case "learning":return "Đang học";
            case "queued":return "Đã xếp hàng blockchain";case "prepared":return "Đã chuẩn bị giao dịch";
            case "submitted":return "Đang chờ xác nhận mạng";case "confirmed":return "Đã xác nhận";
            case "failed":return "Chưa thành công · Cần kiểm tra";case "waiting":return "Chờ kiểm tra 48 giờ";
            case "rewarded":return "Đã nhận thưởng";case "rejected":return "Không đủ điều kiện";
            case "expired":return "Hết hạn";case "limited":return "Đã đạt hạn mức tháng";default:return code;
        }
    }
}
