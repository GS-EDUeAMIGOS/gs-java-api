package br.com.viacidada.exception;

// Excecao lancada quando uma API externa (como SPTrans ou OpenStreetMap) falha ou nao responde (retorna 502).
public class ExternalIntegrationException extends RuntimeException {
    public ExternalIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalIntegrationException(String message) {
        super(message);
    }
}
