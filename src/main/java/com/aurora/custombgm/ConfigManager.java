package com.aurora.custombgm;

import com.aurora.custombgm.audio.AudioSources;
import com.aurora.custombgm.music.BgmConfig;
import com.aurora.custombgm.music.Section;
import com.aurora.custombgm.music.SectionConfig;
import com.aurora.custombgm.music.TrackConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 配置读写、曲库目录管理、整包导入导出。
 *
 * <pre>
 * config/custombgm/
 *   config.json       播放列表 + 全局调音参数
 *   music/            放音频文件的地方（ogg / wav / mp3）
 *   profiles/         导出的整包（zip，含 json + 音频）
 * </pre>
 */
public final class ConfigManager {

    private static final ConfigManager INSTANCE = new ConfigManager();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private BgmConfig config = BgmConfig.defaults();
    private Path root;

    private ConfigManager() {
    }

    public static ConfigManager get() {
        return INSTANCE;
    }

    public BgmConfig config() {
        return config;
    }

    public Path root() {
        if (root == null) {
            root = FMLPaths.CONFIGDIR.get().resolve("custombgm");
        }
        return root;
    }

    public Path musicDir() {
        return root().resolve("music");
    }

    public Path profilesDir() {
        return root().resolve("profiles");
    }

    public Path configFile() {
        return root().resolve("config.json");
    }

    public void load() {
        try {
            Files.createDirectories(musicDir());
            Files.createDirectories(profilesDir());
        } catch (IOException e) {
            CustomBgm.LOGGER.error("Cannot create config directories", e);
        }

        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            config = BgmConfig.defaults();
            config.sanitize();
            save();
            syncAutoTracks();
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            BgmConfig loaded = GSON.fromJson(json, BgmConfig.class);
            if (loaded == null) throw new IOException("empty config");
            config = loaded;
            config.sanitize();
        } catch (Exception e) {
            CustomBgm.LOGGER.error("config.json is broken, backing up and using defaults", e);
            try {
                Files.copy(file, file.resolveSibling("config.json.broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            config = BgmConfig.defaults();
            config.sanitize();
            save();
        }
        syncAutoTracks();
    }

    /**
     * 把 music 目录里尚未出现在列表中的文件，自动补进 autoSections 指定的情境。
     * 手动移除过的文件记在 autoExclude 里，不会被加回来。
     */
    public void syncAutoTracks() {
        BgmConfig cfg = config;
        if (!cfg.autoPopulate) return;
        List<String> files = listMusicFiles();
        if (files.isEmpty()) return;

        boolean changed = false;
        for (String id : new ArrayList<>(cfg.autoSections)) {
            Section s = Section.byId(id);
            if (s == null) continue;
            SectionConfig sc = cfg.section(s);
            if (!sc.enabled) continue;
            for (String f : files) {
                boolean present = false;
                for (TrackConfig t : sc.tracks) {
                    if (t.file.equalsIgnoreCase(f)) {
                        present = true;
                        break;
                    }
                }
                if (present) continue;
                boolean excluded = false;
                for (String e : cfg.autoExclude) {
                    if (e.equalsIgnoreCase(f)) {
                        excluded = true;
                        break;
                    }
                }
                if (excluded) continue;
                sc.tracks.add(new TrackConfig(f));
                changed = true;
            }
        }
        if (changed) save();
    }

    public void save() {
        try {
            Files.createDirectories(root());
            Files.writeString(configFile(), GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CustomBgm.LOGGER.error("Failed to save config.json", e);
        }
    }

    /** music 目录下所有受支持的音频文件名，已排序。 */
    public List<String> listMusicFiles() {
        List<String> out = new ArrayList<>();
        Path dir = musicDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> AudioSources.isSupportedName(p.getFileName().toString()))
                    .map(p -> p.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(out::add);
        } catch (IOException e) {
            CustomBgm.LOGGER.warn("Failed to list music directory", e);
        }
        return out;
    }

    /** 解析曲目文件路径，越界（../）返回 null。 */
    public Path resolveTrack(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        Path base = musicDir().toAbsolutePath().normalize();
        Path resolved = base.resolve(fileName).normalize();
        if (!resolved.startsWith(base)) return null;
        return resolved;
    }

    // ---------------------------------------------------------------- profiles

    public List<Path> listProfiles() {
        List<Path> out = new ArrayList<>();
        Path dir = profilesDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            CustomBgm.LOGGER.warn("Failed to list profiles", e);
        }
        return out;
    }

    /**
     * 把当前配置 + 引用到的所有音频打包成一个 zip。
     * 目标机解压导入后即可得到完全一致的效果，不需要额外调音。
     */
    public Path exportProfile(String name) throws IOException {
        String safeName = sanitizeFileName(name);
        if (safeName.isEmpty()) throw new IOException("invalid profile name");
        Files.createDirectories(profilesDir());
        Path target = profilesDir().resolve(safeName + ".zip");

        Set<String> used = new LinkedHashSet<>();
        for (Section s : Section.values()) {
            SectionConfig sc = config.sections.get(s.id);
            if (sc == null || sc.tracks == null) continue;
            for (TrackConfig t : sc.tracks) {
                if (t != null && t.file != null && !t.file.isBlank()) used.add(t.file);
            }
        }

        try (OutputStream fos = Files.newOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("profile.json"));
            zos.write(GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            for (String fileName : used) {
                Path p = resolveTrack(fileName);
                if (p == null || !Files.isRegularFile(p)) continue;
                zos.putNextEntry(new ZipEntry("music/" + fileName.replace('\\', '/')));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
        return target;
    }

    /**
     * 导入 zip 包。音频直接覆盖到 music 目录，配置整体替换。
     *
     * @return 导入的曲目数量
     */
    public int importProfile(Path zip) throws IOException {
        Path temp = Files.createTempDirectory("custombgm-import");
        int tracks = 0;
        try {
            unzip(zip, temp);
            Path json = temp.resolve("profile.json");
            if (!Files.isRegularFile(json)) {
                throw new IOException("profile.json missing in archive");
            }
            BgmConfig imported = GSON.fromJson(Files.readString(json, StandardCharsets.UTF_8), BgmConfig.class);
            if (imported == null) throw new IOException("profile.json is empty");
            imported.sanitize();

            Path musicTemp = temp.resolve("music");
            if (Files.isDirectory(musicTemp)) {
                Files.createDirectories(musicDir());
                try (Stream<Path> stream = Files.list(musicTemp)) {
                    for (Path p : (Iterable<Path>) stream::iterator) {
                        if (!Files.isRegularFile(p)) continue;
                        Files.copy(p, musicDir().resolve(p.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            config = imported;
            for (Section s : Section.values()) {
                SectionConfig sc = config.sections.get(s.id);
                if (sc != null && sc.tracks != null) tracks += sc.tracks.size();
            }
            save();
        } finally {
            deleteRecursively(temp);
        }
        return tracks;
    }

    public boolean deleteProfile(Path zip) {
        try {
            Files.deleteIfExists(zip);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void unzip(Path zip, Path target) throws IOException {
        Path base = target.toAbsolutePath().normalize();
        try (InputStream is = Files.newInputStream(zip);
             ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) {
                    zis.closeEntry();
                    continue;
                }
                Path out = base.resolve(name).normalize();
                if (!out.startsWith(base)) {
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            os.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public static String sanitizeFileName(String name) {
        if (name == null) return "";
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (s.length() > 64) s = s.substring(0, 64);
        return s;
    }
}
