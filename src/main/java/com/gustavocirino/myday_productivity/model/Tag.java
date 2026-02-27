package com.gustavocirino.myday_productivity.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Entidade Tag para categorização de tarefas.
 * Permite organizar tarefas por contextos (trabalho, pessoal, estudos, etc).
 */
@Entity
@Table(name = "tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 7)
    private String color; // Hex color: #FF5733

    @ManyToMany(mappedBy = "tags")
    private Set<Task> tasks = new HashSet<>();
}
