package com.sumasoft.tracking.client;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and saves clients in the database.
 * Spring writes the actual database code for us.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {
}