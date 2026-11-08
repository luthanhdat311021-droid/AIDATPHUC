package com.studymind.nativeapp.data;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Focus background audio for the Pomodoro screen, synthesised on the device (no downloads):
 * "alpha" = 10 Hz binaural beat (200 Hz left / 210 Hz right), "rain" = soft filtered noise,
 * "brown" = brown noise. One sound plays at a time.
 */
public final class AmbientSound {
    private AmbientSound() {}

    private static final int RATE = 22050;
    private static volatile String playing;
    private static Thread worker;

    public static String current() { return playing; }

    /** Plays the sound, or stops it when it is already playing */
    public static synchronized void toggle(String kind) {
        if (kind.equals(playing)) { stop(); return; }
        stop();
        playing = kind;
        final String mine = kind;
        worker = new Thread(() -> loop(mine), "ambient-" + kind);
        worker.start();
    }

    public static synchronized void stop() {
        playing = null;
        if (worker != null) worker.interrupt();
        worker = null;
    }

    private static void loop(String kind) {
        int frames = RATE / 10;
        int minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(Math.max(minBuf, frames * 4))
                .build();
        short[] buf = new short[frames * 2];
        Random rnd = new Random();
        double phaseL = 0, phaseR = 0, brown = 0, lp = 0, lp2 = 0;
        try {
            track.play();
            while (kind.equals(playing) && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < frames; i++) {
                    double l, r;
                    switch (kind) {
                        case "alpha":
                            phaseL += 2 * Math.PI * 200 / RATE;
                            phaseR += 2 * Math.PI * 210 / RATE;
                            l = Math.sin(phaseL) * 0.18;
                            r = Math.sin(phaseR) * 0.18;
                            break;
                        case "rain": {
                            double white = rnd.nextGaussian() * 0.25;
                            lp += 0.35 * (white - lp);
                            lp2 += 0.02 * (lp - lp2);
                            double drops = rnd.nextDouble() < 0.0015 ? rnd.nextGaussian() * 0.4 : 0;
                            l = (lp - lp2) * 0.9 + drops;
                            r = (lp - lp2) * 0.9 + drops * 0.7;
                            break;
                        }
                        default: {
                            brown += rnd.nextGaussian() * 0.02;
                            brown *= 0.998;
                            l = r = Math.max(-1, Math.min(1, brown * 3));
                        }
                    }
                    buf[i * 2] = (short) (Math.max(-1, Math.min(1, l)) * 32767);
                    buf[i * 2 + 1] = (short) (Math.max(-1, Math.min(1, r)) * 32767);
                }
                if (phaseL > 2 * Math.PI * 1000) { phaseL %= 2 * Math.PI; phaseR %= 2 * Math.PI; }
                track.write(buf, 0, buf.length);
            }
        } catch (RuntimeException ignored) {
        } finally {
            try { track.stop(); } catch (RuntimeException ignored) {}
            track.release();
        }
    }
}
