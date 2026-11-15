package com.aurora.custombgm.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AudioSources {

    private AudioSources() {
    }

    public static boolean isSupported(Path file) {
        return isSupportedName(file.getFileName().toString());
    }

    public static boolean isSupportedName(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".wave")
                || n.endsWith(".aif") || n.endsWith(".aiff") || n.endsWith(".mp3");
    }

    public static AudioSource open(Path file, float speed) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a file: " + file);
        }
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        AudioSource source;
        if (n.endsWith(".ogg")) {
            source = new OggSource(file);
        } else if (n.endsWith(".mp3")) {
            try {
                source = new Mp3Source(file);
            } catch (NoClassDefFoundError e) {
                throw new IOException("MP3 decoder (jlayer) is missing from the mod jar", e);
            }
        } else if (n.endsWith(".wav") || n.endsWith(".wave") || n.endsWith(".aif") || n.endsWith(".aiff")) {
            source = new WavSource(file);
        } else {
            throw new IOException("Unsupported audio format: " + n);
        }
        if (Math.abs(speed - 1f) > 0.001f) {
            source = new ResampledSource(source, speed);
        }
        return source;
    }
}
