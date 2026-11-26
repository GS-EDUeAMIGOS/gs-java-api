package br.com.viacidada.exception;

// Excecao lancada quando o registro buscado no banco por ID nao existe (retorna erro 404 Not Found).
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
