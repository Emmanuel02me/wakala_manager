package com.Wakala.v1.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "providers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name; // M-Pesa, Airtel Money, Mixx by Yas, HaloPesa, CRDB, NMB

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderType type;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    public enum ProviderType {
        MOBILE_MONEY, BANK
    }
}