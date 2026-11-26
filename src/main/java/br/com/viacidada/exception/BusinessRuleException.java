package br.com.viacidada.exception;

// Excecao lancada quando alguma regra de negocio e violada (retorna erro 409 Conflict).
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
