package com.example.newapp;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;

/** Prefer an installed Thai voice identified as female; fall back to the device's Thai voice. */
final class POSVoice {
    private POSVoice() {}
    static void configure(TextToSpeech tts) {
        if (tts == null) return;
        Locale thai = new Locale("th", "TH");
        tts.setLanguage(thai);
        try {
            if (tts.getVoices() == null) return;
            for (Voice voice : tts.getVoices()) {
                if (voice == null || voice.getLocale() == null
                        || !"th".equalsIgnoreCase(voice.getLocale().getLanguage())) continue;
                String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.ROOT);
                if (name.contains("female") || name.contains("woman") || name.contains("fem")) {
                    tts.setVoice(voice);
                    return;
                }
            }
        } catch (Exception ignored) {
            // Device-provided Thai default voice remains active.
        }
    }
}
