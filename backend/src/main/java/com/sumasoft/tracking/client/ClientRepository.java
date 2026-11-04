package com.sumasoft.tracking.client;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and saves clients in the database.
 * Spring writes the actual database code for us.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Is this name already used? ("Hero" and "hero" count as the same.) */
    boolean existsByClientNameIgnoreCase(String clientName);

    /** Is this name used by ANOTHER client (not the one being edited)? */
    boolean existsByClientNameIgnoreCaseAndIdNot(String clientName, Long id);
}