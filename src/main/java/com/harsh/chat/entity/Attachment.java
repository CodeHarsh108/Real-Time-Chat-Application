package com.harsh.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    private String id;

    @Indexed
    private String messageId;

    @Indexed
    private String roomId;

    private String fileName;
    private String fileType;
    private long fileSize;
    private String filePath;
    private String thumbnailPath;
    private String fileUrl;
    private String thumbnailUrl;

    private Integer width;        // For images/videos
    private Integer height;       // For images/videos
    private Integer duration;      // For audio/video (seconds)

    private String uploadedBy;
    private LocalDateTime uploadedAt;

    @Builder.Default
    private boolean isDeleted = false;
}
