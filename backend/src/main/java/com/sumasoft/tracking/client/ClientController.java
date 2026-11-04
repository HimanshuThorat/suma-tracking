package com.sumasoft.tracking.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Web addresses (API) for clients.
 *   GET /api/clients       -> all clients
 *   GET /api/clients/{id}  -> one client
 * NOTE: no login yet. Login and permissions are added in Phase 4.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<ClientResponse> listClients() {
        return clientService.listClients();
    }

    @GetMapping("/{id}")
    public ClientResponse getClient(@PathVariable Long id) {
        return clientService.getClient(id);
    }
}