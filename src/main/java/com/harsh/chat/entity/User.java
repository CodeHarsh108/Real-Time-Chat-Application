package com.harsh.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document(value = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;

    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @Builder.Default
    private boolean enabled = true;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;


    // For email
    private boolean emailVerified;
    private String verificationToken;
    private LocalDateTime verificationTokenExpiry;

    // For password reset
    private String resetToken;
    private LocalDateTime resetTokenExpiry;


}
