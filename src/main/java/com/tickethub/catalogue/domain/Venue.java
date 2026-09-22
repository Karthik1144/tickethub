package com.tickethub.catalogue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(length = 255)
    private String address;

    public Venue(String name, String city, String address) {
        this.name = name;
        this.city = city;
        this.address = address;
    }
}
