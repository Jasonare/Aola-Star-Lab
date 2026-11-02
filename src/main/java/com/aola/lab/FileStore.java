package com.aola.lab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStore {
    public record Stored(String key, String type) {
    }

    private final Path root;

    public FileStore(@Value("${app.storage}") String location) throws IOException {
        root = Paths.get(location).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Stored save(MultipartFile file, String category) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 100L * 1024 * 1024) {
            throw new IllegalArgumentException("文件为空或超过100MB");
        }
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : "";
        if (original.contains("/") || original.contains("\\") || original.length() > 200) {
            throw new IllegalArgumentException("文件名无效");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(16);
        }
        // 根据文件头校验扩展名对应的类型。
        String type = switch (ext) {
            case "png" -> starts(head, new int[]{137, 80, 78, 71, 13, 10, 26, 10}) ? "image/png" : null;
            case "jpg", "jpeg" -> starts(head, new int[]{255, 216, 255}) ? "image/jpeg" : null;
            case "webp" -> head.length >= 12 && starts(head, new int[]{82, 73, 70, 70})
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P'
                    ? "image/webp" : null;
            case "mp4" -> head.length >= 12 && head[4] == 'f' && head[5] == 't'
                    && head[6] == 'y' && head[7] == 'p' ? "video/mp4" : null;
            case "pdf" -> starts(head, new int[]{37, 80, 68, 70, 45}) ? "application/pdf" : null;
            case "zip" -> starts(head, new int[]{80, 75, 3, 4}) || starts(head, new int[]{80, 75, 5, 6})
                    ? "application/zip" : null;
            case "apk" -> starts(head, new int[]{80, 75, 3, 4}) || starts(head, new int[]{80, 75, 5, 6})
                    ? "application/vnd.android.package-archive" : null;
            case "exe" -> starts(head, new int[]{77, 90}) ? "application/octet-stream" : null;
            default -> null;
        };
        if (type == null || (category.equals("image") && !type.startsWith("image/"))
                || (category.equals("video") && !type.equals("video/mp4"))) {
            throw new IllegalArgumentException("不支持的文件类型或文件内容不匹配");
        }
        String key = UUID.randomUUID().toString();
        Path target = root.resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }
        return new Stored(key, type);
    }

    private boolean starts(byte[] data, int[] signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 255) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    public FileSystemResource load(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("文件不存在");
        }
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("文件不存在");
        }
        return new FileSystemResource(path);
    }

    public void delete(String key) throws IOException {
        if (key != null && key.matches("[0-9a-f-]{36}")) {
            Files.deleteIfExists(root.resolve(key));
        }
    }

}
