package com.example.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "guarantee_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuaranteeFile implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "guarantee_id", length = 20, nullable = true)
    private String guaranteeId;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "file_url", length = 1000, nullable = false)
    private String fileUrl;

    @Column(name = "public_id", length = 255, nullable = false)
    private String publicId;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "format", length = 50)
    private String format;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "source_file_id")
    private Long sourceFileId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 20)
    @Builder.Default
    private com.example.ecommerce.enums.FileArtifactType artifactType = com.example.ecommerce.enums.FileArtifactType.ORIGINAL;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "prepared_by", length = 255)
    private String preparedBy;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "is_digitally_signed", nullable = false)
    @Builder.Default
    private Boolean isDigitallySigned = false;

    @Column(name = "requires_signing", nullable = false)
    @Builder.Default
    private Boolean requiresSigning = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
