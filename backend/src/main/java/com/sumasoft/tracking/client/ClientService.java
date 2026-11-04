package com.sumasoft.tracking.client;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The business rules for clients live here (not in the controller).
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listClients() {
        return clientRepository.findAll(Sort.by("id")).stream()
                .map(ClientResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(Long id) {
        return ClientResponse.from(findClient(id));
    }

    @Transactional
    public ClientResponse createClient(ClientRequest request) {
        String name = request.clientName().trim();
        if (clientRepository.existsByClientNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A client with this name already exists");
        }
        Client saved = clientRepository.saveAndFlush(Client.create(request));
        return ClientResponse.from(saved);
    }

    @Transactional
    public ClientResponse updateClient(Long id, ClientRequest request) {
        Client client = findClient(id);
        String name = request.clientName().trim();
        if (clientRepository.existsByClientNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another client already uses this name");
        }
        client.applyDetails(request);
        return ClientResponse.from(clientRepository.saveAndFlush(client));
    }

    @Transactional
    public ClientResponse activateClient(Long id) {
        Client client = findClient(id);
        client.activate();
        return ClientResponse.from(clientRepository.saveAndFlush(client));
    }

    @Transactional
    public ClientResponse deactivateClient(Long id) {
        Client client = findClient(id);
        client.deactivate();
        return ClientResponse.from(clientRepository.saveAndFlush(client));
    }

    private Client findClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }
}