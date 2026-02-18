package com.harsh.chat.service;

import com.harsh.chat.entity.Attachment;
import com.harsh.chat.repositories.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final AttachmentRepository attachmentRepository;
    private final Tika tika = new Tika();

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.max-size:52428800}")
    private long maxFileSize;

    @Value("${file.allowed-types:}")
    private String[] allowedTypesArray;

    @Value("${file.thumbnail-size:200}")
    private int thumbnailSize;

    private Path fileStorageLocation;
    private Set<String> allowedTypes;

    @PostConstruct
    public void init() {
        try {
            fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(fileStorageLocation);

            // Create subdirectories
            Files.createDirectories(fileStorageLocation.resolve("images"));
            Files.createDirectories(fileStorageLocation.resolve("thumbnails"));
            Files.createDirectories(fileStorageLocation.resolve("videos"));
            Files.createDirectories(fileStorageLocation.resolve("audio"));
            Files.createDirectories(fileStorageLocation.resolve("documents"));
            Files.createDirectories(fileStorageLocation.resolve("other"));

            // Initialize allowed types with hardcoded defaults if array is empty
            allowedTypes = new HashSet<>();

            // Always add default allowed types even if config is empty
            allowedTypes.addAll(Arrays.asList(
                    "image/jpeg", "image/png", "image/gif", "image/webp", "image/jpg",
                    "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo", "video/x-matroska",
                    "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm",
                    "application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain", "text/csv", "application/zip", "application/x-zip-compressed",
                    "application/x-7z-compressed", "application/x-rar-compressed"
            ));

            // Add any additional types from config
            if (allowedTypesArray != null && allowedTypesArray.length > 0) {
                allowedTypes.addAll(Arrays.asList(allowedTypesArray));
            }

            log.info("File storage initialized at: {}", fileStorageLocation);
            log.info("Allowed file types: {}", allowedTypes);

        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    /**
     * Store a file and return attachment entity
     */
    public Attachment storeFile(MultipartFile file, String uploadedBy, String roomId) {
        try {
            // Validate file
            validateFile(file);

            // Generate unique filename
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

            // Detect file type
            String mimeType = detectMimeType(file);
            log.info("Detected MIME type: {} for file: {}", mimeType, originalFileName);

            String fileCategory = getFileCategory(mimeType);

            // Determine subdirectory
            Path targetLocation = fileStorageLocation.resolve(fileCategory).resolve(uniqueFileName);

            // Copy file to target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Generate thumbnail for images
            String thumbnailPath = null;
            Integer width = null;
            Integer height = null;

            if (fileCategory.equals("images")) {
                thumbnailPath = generateThumbnail(targetLocation, uniqueFileName);
            }

            // Build file URL (for web access)
            String fileUrl = "/api/v1/attachments/view/" + fileCategory + "/" + uniqueFileName;
            String thumbnailUrl = thumbnailPath != null ?
                    "/api/v1/attachments/view/thumbnails/" + Paths.get(thumbnailPath).getFileName().toString() : null;

            // Create attachment record
            Attachment attachment = Attachment.builder()
                    .fileName(originalFileName)
                    .fileType(mimeType)
                    .fileSize(file.getSize())
                    .filePath(targetLocation.toString())
                    .thumbnailPath(thumbnailPath)
                    .fileUrl(fileUrl)
                    .thumbnailUrl(thumbnailUrl)
                    .uploadedBy(uploadedBy)
                    .roomId(roomId)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            Attachment savedAttachment = attachmentRepository.save(attachment);
            log.info("File stored successfully: {} (ID: {})", originalFileName, savedAttachment.getId());

            return savedAttachment;

        } catch (IOException e) {
            log.error("Could not store file: {}", e.getMessage());
            throw new RuntimeException("Could not store file: " + e.getMessage());
        }
    }

    /**
     * Load file as resource
     */
    public Resource loadFileAsResource(String fileName, String category) {
        try {
            Path filePath = fileStorageLocation.resolve(category).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + fileName);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + fileName, e);
        }
    }

    /**
     * Delete file
     */
    public boolean deleteFile(String attachmentId) {
        try {
            Attachment attachment = attachmentRepository.findById(attachmentId)
                    .orElseThrow(() -> new RuntimeException("Attachment not found"));

            Path filePath = Paths.get(attachment.getFilePath());
            Files.deleteIfExists(filePath);

            if (attachment.getThumbnailPath() != null) {
                Path thumbnailPath = Paths.get(attachment.getThumbnailPath());
                Files.deleteIfExists(thumbnailPath);
            }

            attachmentRepository.delete(attachment);
            log.info("File deleted: {}", attachmentId);

            return true;
        } catch (IOException e) {
            log.error("Could not delete file: {}", e.getMessage());
            return false;
        }
    }

    // ============== PRIVATE HELPER METHODS ==============

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size: " +
                    (maxFileSize / 1024 / 1024) + "MB");
        }

        try {
            String mimeType = detectMimeType(file);
            log.info("Validating file type: {}", mimeType);

            // Allow common image types even if not in allowed list
            boolean isAllowed = false;

            // Check against allowed types
            if (allowedTypes.contains(mimeType)) {
                isAllowed = true;
            }

            // Additional common type checks
            if (mimeType.startsWith("image/") ||
                    mimeType.startsWith("video/") ||
                    mimeType.startsWith("audio/") ||
                    mimeType.equals("application/pdf") ||
                    mimeType.contains("document") ||
                    mimeType.contains("spreadsheet") ||
                    mimeType.contains("presentation")) {
                isAllowed = true;
            }

            if (!isAllowed) {
                throw new IllegalArgumentException("File type not allowed: " + mimeType);
            }

        } catch (IOException e) {
            throw new RuntimeException("Could not detect file type", e);
        }
    }

    private String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return tika.detect(is);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String getFileCategory(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return "images";
        } else if (mimeType.startsWith("video/")) {
            return "videos";
        } else if (mimeType.startsWith("audio/")) {
            return "audio";
        } else if (mimeType.equals("application/pdf") ||
                mimeType.contains("document") ||
                mimeType.contains("spreadsheet") ||
                mimeType.contains("presentation") ||
                mimeType.equals("text/plain") ||
                mimeType.equals("text/csv")) {
            return "documents";
        } else {
            return "other";
        }
    }

    private String generateThumbnail(Path originalPath, String fileName) throws IOException {
        Path thumbnailDir = fileStorageLocation.resolve("thumbnails");
        Files.createDirectories(thumbnailDir);

        String thumbnailName = "thumb_" + fileName.replace(getFileExtension(fileName), ".jpg");
        Path thumbnailPath = thumbnailDir.resolve(thumbnailName);

        Thumbnails.of(originalPath.toFile())
                .size(thumbnailSize, thumbnailSize)
                .outputFormat("jpg")
                .toFile(thumbnailPath.toFile());

        return thumbnailPath.toString();
    }

    /**
     * Get file size in human readable format
     */
    public String getReadableFileSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }
}