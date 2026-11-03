package com.aola.lab;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;

@Service
public class FileStore {
    public record Stored(String key, String type) {
    }

    private final Path root;
    private final Path videoRoot;
    private final COSClient cosClient;
    private final String bucket;

    public FileStore(@Value("${app.storage}") String location,
            @Value("${app.video-storage:./data/vedios}") String videoLocation,
            @Value("${app.cos.enabled:false}") boolean cosEnabled,
            @Value("${app.cos.bucket:aola-star-hub-1350639287}") String bucket,
            @Value("${app.cos.region:ap-shanghai}") String region,
            @Value("${app.cos.secret-id:}") String secretId,
            @Value("${app.cos.secret-key:}") String secretKey) throws IOException {
        root = Paths.get(location).toAbsolutePath().normalize();
        Files.createDirectories(root);
        videoRoot = Paths.get(videoLocation).toAbsolutePath().normalize();
        Files.createDirectories(videoRoot);
        this.bucket = bucket;
        if (cosEnabled) {
            if (secretId.isBlank() || secretKey.isBlank()) {
                throw new IllegalStateException("COS 已启用，但缺少 SecretId 或 SecretKey 配置");
            }
            COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
            ClientConfig config = new ClientConfig(new Region(region));
            cosClient = new COSClient(credentials, config);
        } else {
            cosClient = null;
        }
    }

    @PreDestroy
    public void close() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    private boolean isCosKey(String key) {
        return key != null && key.startsWith("cos/") && key.length() > 4;
    }

    static String objectKey(String id, String extension) {
        return id + (extension.isEmpty() ? "" : "." + extension);
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
        boolean validMp4 = ext.equals("mp4") && hasValidMp4Structure(file);
        // 根据文件头校验扩展名对应的类型。
        String type = switch (ext) {
            case "png" -> starts(head, new int[]{137, 80, 78, 71, 13, 10, 26, 10}) ? "image/png" : null;
            case "jpg", "jpeg" -> starts(head, new int[]{255, 216, 255}) ? "image/jpeg" : null;
            case "webp" -> head.length >= 12 && starts(head, new int[]{82, 73, 70, 70})
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P'
                    ? "image/webp" : null;
            case "mp4" -> validMp4 && head.length >= 12 && head[4] == 'f' && head[5] == 't'
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
        String id = UUID.randomUUID().toString();
        boolean localVideo = category.equals("video");
        boolean useCos = !localVideo && cosClient != null
                && (category.equals("image") || category.equals("resource"));
        String objectKey = objectKey(id, ext);
        String key = useCos ? "cos/" + objectKey : localVideo ? "video/" + objectKey : id;
        if (!useCos) {
            Path target = localVideo ? videoRoot.resolve(objectKey) : root.resolve(key);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } else {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(type);
            try (InputStream in = file.getInputStream()) {
                cosClient.putObject(new PutObjectRequest(bucket, objectKey, in, metadata));
            }
        }
        return new Stored(key, type);
    }

    private boolean hasValidMp4Structure(MultipartFile file) throws IOException {
        boolean hasMovie = false;
        boolean hasMedia = false;
        boolean firstBox = true;
        long remaining = file.getSize();
        try (DataInputStream input = new DataInputStream(file.getInputStream())) {
            while (remaining >= 8) {
                long size = Integer.toUnsignedLong(input.readInt());
                byte[] typeBytes = input.readNBytes(4);
                if (typeBytes.length != 4) {
                    return false;
                }
                remaining -= 8;
                String boxType = new String(typeBytes, java.nio.charset.StandardCharsets.US_ASCII);
                long headerSize = 8;
                if (size == 1) {
                    if (remaining < 8) {
                        return false;
                    }
                    size = input.readLong();
                    remaining -= 8;
                    headerSize = 16;
                } else if (size == 0) {
                    size = remaining + headerSize;
                }
                if (size < headerSize || size - headerSize > remaining) {
                    return false;
                }
                if ((firstBox && !"ftyp".equals(boxType)) || ("ftyp".equals(boxType) && headerSize != 8)) {
                    return false;
                }
                firstBox = false;
                hasMovie |= "moov".equals(boxType);
                hasMedia |= "mdat".equals(boxType);
                long payload = size - headerSize;
                input.skipNBytes(payload);
                remaining -= payload;
            }
            return remaining == 0 && hasMovie && hasMedia;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
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

    public Resource load(String key) {
        if (isCosKey(key) && cosClient != null) {
            String objectKey = key.substring(4);
            try (InputStream in = cosClient.getObject(bucket, objectKey).getObjectContent();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return new ByteArrayResource(out.toByteArray());
            } catch (IOException e) {
                throw new IllegalArgumentException("文件不存在", e);
            }
        }
        if (key != null && key.matches("video/[0-9a-f-]{36}\\.mp4")) {
            Path videoPath = videoRoot.resolve(key.substring(6)).normalize();
            if (!videoPath.getParent().equals(videoRoot)
                    || !Files.isRegularFile(videoPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("文件不存在");
            }
            return new FileSystemResource(videoPath);
        }
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
        if (isCosKey(key) && cosClient != null) {
            cosClient.deleteObject(bucket, key.substring(4));
        } else if (key != null && key.matches("video/[0-9a-f-]{36}\\.mp4")) {
            Path videoPath = videoRoot.resolve(key.substring(6)).normalize();
            if (videoPath.getParent().equals(videoRoot)) {
                Files.deleteIfExists(videoPath);
            }
        } else if (key != null && key.matches("[0-9a-f-]{36}")) {
            Files.deleteIfExists(root.resolve(key));
        }
    }

}
