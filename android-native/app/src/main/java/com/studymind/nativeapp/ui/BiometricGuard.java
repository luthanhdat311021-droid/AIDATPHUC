package com.studymind.nativeapp.ui;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/**
 * Signs server challenges with a Keystore key that only works after the system biometric prompt.
 * Android decides which sensors count (fingerprint, or face on phones with secure face unlock);
 * on Android 11+ the screen lock is the fallback.
 */
public final class BiometricGuard {
    private BiometricGuard() {}

    private static final String KEYSTORE = "AndroidKeyStore";

    public interface Signed {
        void success(String signatureBase64);
        void failure(String message);
    }

    private static String alias(String userId) { return "studymind_redeem_" + userId; }

    private static int authenticators() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL
                : BiometricManager.Authenticators.BIOMETRIC_STRONG;
    }

    public static boolean available(FragmentActivity activity) {
        return BiometricManager.from(activity).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        return ks;
    }

    /** Base64 SPKI public key for this account, creating the device key on first use */
    public static String publicKey(String userId) throws Exception {
        KeyStore ks = keyStore();
        String alias = alias(userId);
        if (!ks.containsAlias(alias)) {
            KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);
            generator.initialize(spec.build());
            generator.generateKeyPair();
        }
        return Base64.encodeToString(ks.getCertificate(alias).getPublicKey().getEncoded(), Base64.NO_WRAP);
    }

    public static void sign(FragmentActivity activity, String userId, String message, String subtitle, Signed result) {
        Signature signature;
        try {
            KeyStore ks = keyStore();
            signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign((PrivateKey) ks.getKey(alias(userId), null));
        } catch (KeyPermanentlyInvalidatedException e) {
            // New fingerprint or face added: this key must not be trusted any more
            try { keyStore().deleteEntry(alias(userId)); } catch (Exception ignored) {}
            result.failure("Sinh trắc học trên máy vừa thay đổi. Hãy thử lại; thiết bị cần 24 giờ để được kích hoạt lại.");
            return;
        } catch (Exception e) {
            result.failure("Không dùng được khóa bảo mật của thiết bị.");
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult auth) {
                try {
                    Signature unlocked = auth.getCryptoObject().getSignature();
                    unlocked.update(message.getBytes(StandardCharsets.UTF_8));
                    result.success(Base64.encodeToString(unlocked.sign(), Base64.NO_WRAP));
                } catch (Exception e) {
                    result.failure("Không ký được yêu cầu xác thực.");
                }
            }
            @Override public void onAuthenticationError(int code, @NonNull CharSequence text) {
                result.failure("Đã hủy đổi token: " + text);
            }
        });
        BiometricPrompt.PromptInfo.Builder info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Xác nhận đổi StudyCoin")
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) info.setNegativeButtonText("Hủy");
        prompt.authenticate(info.build(), new BiometricPrompt.CryptoObject(signature));
    }
}
