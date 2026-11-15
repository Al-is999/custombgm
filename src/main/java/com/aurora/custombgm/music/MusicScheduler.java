package com.aurora.custombgm.music;

import com.aurora.custombgm.ConfigManager;
import com.aurora.custombgm.CustomBgm;
import com.aurora.custombgm.audio.AudioSource;
import com.aurora.custombgm.audio.AudioSources;
import com.aurora.custombgm.audio.OpenAlStream;
import com.aurora.custombgm.audio.dsp.EffectSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 播放调度器：决定什么时候播哪一首、怎么淡入淡出、音量挂到游戏设置上。
 * 全部跑在客户端主线程（客户端 tick + 渲染帧），不额外开线程碰 OpenAL。
 */
public final class MusicScheduler {

    private static final MusicScheduler INSTANCE = new MusicScheduler();

    public static MusicScheduler get() {
        return INSTANCE;
    }

    private final List<Voice> voices = new ArrayList<>();
    private final Random random = new Random();

    private long lastTickMs;
    private Section activeSection;
    private boolean waiting;
    private float gapTimer;
    private int sequentialIndex;
    private final List<Integer> shuffleOrder = new ArrayList<>();
    private int shuffleCursor;
    private int onceCursor;
    private String lastPlayedFile;

    private MusicScheduler() {
    }

    public Section activeSection() {
        return activeSection;
    }

    public String currentTrackName() {
        for (Voice v : voices) {
            if (v.main) return v.file;
        }
        return null;
    }

    public int voiceCount() {
        return voices.size();
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        long now = System.currentTimeMillis();
        float dt = lastTickMs == 0L ? 0.05f : Math.min(0.5f, (now - lastTickMs) / 1000f);
        lastTickMs = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        BgmConfig cfg = ConfigManager.get().config();
        updateVoices(dt, mc, cfg);

        if (!cfg.enabled) {
            fadeAll(0.5f);
            return;
        }

        Section target = resolveSection(mc);
        if (target != activeSection) {
            activeSection = target;
            resetSequence();
            SectionConfig sc = cfg.section(target);
            if (sc.enabled && sc.hasTracks()) {
                startNext(sc, cfg);
            } else {
                fadeAll(cfg.fadeOutSeconds);
            }
            return;
        }

        SectionConfig sc = cfg.section(activeSection);
        if (!sc.enabled || !sc.hasTracks()) {
            fadeAll(cfg.fadeOutSeconds);
            return;
        }

        if (!voices.isEmpty()) {
            return;
        }

        if (waiting) {
            if (gapTimer < 0f) gapTimer = Math.max(0f, sc.gapSeconds);
            gapTimer -= dt;
            if (gapTimer > 0f) return;
            waiting = false;
        }

        startNext(sc, cfg);
    }

    /** 原版音乐是否该被压掉。只在当前情境确实有自定义曲目时才压。 */
    public boolean shouldSuppressVanilla() {
        BgmConfig cfg = ConfigManager.get().config();
        if (!cfg.enabled || !cfg.replaceVanillaMusic) return false;
        Section sec = activeSection;
        if (sec == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;
            sec = resolveSection(mc);
        }
        SectionConfig sc = cfg.section(sec);
        return sc.enabled && sc.hasTracks();
    }

    public Section resolveSection(Minecraft mc) {
        if (mc.level == null) return Section.MENU;
        LocalPlayer player = mc.player;
        if (player == null) return Section.MENU;
        if (player.level().dimension() == Level.NETHER) return Section.NETHER;
        if (player.level().dimension() == Level.END) return Section.END;
        if (player.isUnderWater()) return Section.UNDERWATER;
        if (player.isCreative() || player.isSpectator()) return Section.CREATIVE;
        return Section.GAME;
    }

    // ------------------------------------------------------------ voice 管理

    private void updateVoices(float dt, Minecraft mc, BgmConfig cfg) {
        float musicVolume = 1f;
        try {
            musicVolume = mc.options.getSoundSourceVolume(SoundSource.MUSIC);
        } catch (Throwable ignored) {
        }
        float base = Math.max(0f, musicVolume) * Math.max(0f, cfg.masterVolume);

        Iterator<Voice> it = voices.iterator();
        while (it.hasNext()) {
            Voice v = it.next();
            if (v.stream.isFinished()) {
                boolean wasMain = v.main;
                v.stream.close();
                it.remove();
                if (wasMain) {
                    lastPlayedFile = v.file;
                    waiting = true;
                    gapTimer = -1f;
                }
                continue;
            }

            if (v.fade < v.targetFade) {
                v.fade = Math.min(v.targetFade, v.fade + v.speed * dt);
            } else if (v.fade > v.targetFade) {
                v.fade = Math.max(v.targetFade, v.fade - v.speed * dt);
            }

            if (!v.main && v.targetFade <= 0f && v.fade <= 0.01f) {
                v.stream.close();
                it.remove();
                continue;
            }

            v.stream.tick();
            v.stream.setGain(base * v.volume * v.fade);
        }
    }

    private void fadeAll(float seconds) {
        float speed = seconds <= 0.02f ? 100f : 1f / seconds;
        for (Voice v : voices) {
            if (v.targetFade > 0f) {
                v.targetFade = 0f;
                v.speed = speed;
            }
            v.main = false;
        }
        waiting = false;
    }

    public void stopAll() {
        fadeAll(0.4f);
    }

    private void startNext(SectionConfig sc, BgmConfig cfg) {
        int attempts = Math.max(1, sc.tracks.size());
        for (int i = 0; i < attempts; i++) {
            TrackConfig track = pickNext(sc);
            if (track == null) {
                waiting = true;
                gapTimer = 30f;
                return;
            }
            if (startTrack(track, cfg)) return;
        }
        CustomBgm.LOGGER.warn("[CustomBGM] no playable track in section {}", activeSection);
        waiting = true;
        gapTimer = 15f;
    }

    private boolean startTrack(TrackConfig track, BgmConfig cfg) {
        Path path = ConfigManager.get().resolveTrack(track.file);
        if (path == null || !Files.isRegularFile(path)) {
            CustomBgm.LOGGER.warn("[CustomBGM] missing track file: {}", track.file);
            return false;
        }

        EffectSettings effects = track.effectiveEffects(cfg.effects);
        AudioSource source;
        try {
            source = AudioSources.open(path, effects.speed);
        } catch (Exception e) {
            CustomBgm.LOGGER.warn("[CustomBGM] cannot decode {}: {}", track.file, e.toString());
            return false;
        }

        OpenAlStream stream = new OpenAlStream(source, effects);
        if (!stream.isUsable() || !stream.start()) {
            stream.close();
            return false;
        }

        float crossfade = cfg.crossfadeSeconds <= 0.02f ? 100f : 1f / cfg.crossfadeSeconds;
        for (Voice v : voices) {
            if (v.main) {
                v.main = false;
                v.targetFade = 0f;
                v.speed = crossfade;
            }
        }

        Voice voice = new Voice(stream, track.file, Math.max(0f, track.volume));
        voice.main = true;
        voice.fade = 0f;
        voice.targetFade = 1f;
        voice.speed = cfg.fadeInSeconds <= 0.02f ? 100f : 1f / cfg.fadeInSeconds;
        voices.add(voice);
        waiting = false;
        return true;
    }

    /** GUI 试听单曲，不影响当前情境的播放进度。 */
    public void previewTrack(String fileName) {
        BgmConfig cfg = ConfigManager.get().config();
        Path path = ConfigManager.get().resolveTrack(fileName);
        if (path == null || !Files.isRegularFile(path)) return;

        fadeAll(0.25f);
        try {
            AudioSource source = AudioSources.open(path, cfg.effects.speed);
            OpenAlStream stream = new OpenAlStream(source, cfg.effects);
            if (!stream.isUsable() || !stream.start()) {
                stream.close();
                return;
            }
            Voice voice = new Voice(stream, fileName, 1f);
            voice.main = false;
            voice.fade = 0f;
            voice.targetFade = 1f;
            voice.speed = cfg.fadeInSeconds <= 0.02f ? 100f : 1f / cfg.fadeInSeconds;
            voices.add(voice);
        } catch (Exception e) {
            CustomBgm.LOGGER.warn("[CustomBGM] preview failed: {}", e.toString());
        }
    }

    // ------------------------------------------------------------- 选曲策略

    private void resetSequence() {
        sequentialIndex = 0;
        shuffleOrder.clear();
        shuffleCursor = 0;
        onceCursor = 0;
        waiting = false;
        gapTimer = 0f;
    }

    private TrackConfig pickNext(SectionConfig sc) {
        List<TrackConfig> list = sc.tracks;
        int size = list.size();
        if (size == 0) return null;

        switch (sc.mode) {
            case SEQUENTIAL: {
                int idx = sequentialIndex % size;
                sequentialIndex++;
                return list.get(idx);
            }
            case SHUFFLE: {
                if (shuffleOrder.size() != size || shuffleCursor >= shuffleOrder.size()) {
                    rebuildShuffle(list);
                }
                if (shuffleCursor >= shuffleOrder.size()) return null;
                int idx = shuffleOrder.get(shuffleCursor++);
                return list.get(Math.min(idx, size - 1));
            }
            case WEIGHTED_RANDOM: {
                int total = 0;
                for (TrackConfig t : list) total += Math.max(1, t.weight);
                int roll = random.nextInt(Math.max(1, total));
                for (TrackConfig t : list) {
                    roll -= Math.max(1, t.weight);
                    if (roll < 0) return t;
                }
                return list.get(size - 1);
            }
            case ONCE: {
                if (onceCursor >= size) return null;
                return list.get(onceCursor++);
            }
            default:
                return list.get(0);
        }
    }

    private void rebuildShuffle(List<TrackConfig> list) {
        shuffleOrder.clear();
        for (int i = 0; i < list.size(); i++) shuffleOrder.add(i);
        Collections.shuffle(shuffleOrder, random);
        if (shuffleOrder.size() > 1 && lastPlayedFile != null) {
            int first = shuffleOrder.get(0);
            if (first < list.size() && lastPlayedFile.equals(list.get(first).file)) {
                Collections.swap(shuffleOrder, 0, 1);
            }
        }
        shuffleCursor = 0;
    }

    private static final class Voice {
        final OpenAlStream stream;
        final String file;
        final float volume;
        float fade;
        float targetFade = 1f;
        float speed = 1f;
        boolean main;

        Voice(OpenAlStream stream, String file, float volume) {
            this.stream = stream;
            this.file = file;
            this.volume = volume;
        }
    }
}
