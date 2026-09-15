package com.example.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Check;

import java.io.Serializable;


@Entity
@Table(name = "customers")
@Check(
        name = "chk_customers_cif_format",
        constraints = "cif REGEXP '^[0-9]{6,12}$'"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Customer implements Serializable {

    @Id
    @NotBlank
    @Size(max = 12)
    @Column(name = "cif", length = 12, nullable = false, updatable = false)
    private String cif;

    @NotBlank
    @Size(max = 255)
    @Column(name = "customer_name", length = 255, nullable = false)
    private String customerName;

    @Size(max = 20)
    @Column(name = "tax_code", length = 20, nullable = true)
    private String taxCode;

    @Size(max = 500)
    @Column(name = "address", length = 500, nullable = true)
    private String address;
}
