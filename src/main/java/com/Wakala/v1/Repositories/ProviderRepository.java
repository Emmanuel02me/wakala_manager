package com.Wakala.v1.Repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Wakala.v1.Entity.Provider;

public interface ProviderRepository extends JpaRepository<Provider, Long>{
    Optional<Provider> findByName(String name);
    List<Provider> findByActiveTrue();
}
