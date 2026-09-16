package com.example.ecommerce.entity;

import com.example.ecommerce.enums.Action;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.validation.ValidRejectComment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Check;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "processing_histories")
@Check(
        name = "chk_ph_reject_comment",
        constraints = "action <> 'REJECT' "
                + "OR (comment IS NOT NULL AND CHAR_LENGTH(TRIM(comment)) BETWEEN 10 AND 500)"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"guaranteeRequest", "performedBy"})
public class ProcessingHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guarantee_id", referencedColumnName = "id", nullable = false, updatable = false)
    private GuaranteeRequest guaranteeRequest;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false, updatable = false)
    private Action action;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", referencedColumnName = "username", nullable = false, updatable = false)
    private User performedBy;
//    @Size(max = 50)
//    @Column(name = "performed_by", length = 50, nullable = true, updatable = false)
//    private String performedBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false, updatable = false)
    private Role role;

    @NotNull
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Size(max = 500)
    @Column(name = "comment", length = 500, nullable = true)
    private String comment;

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
