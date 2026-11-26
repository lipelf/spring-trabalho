package com.soundhub.common.exception;

/** 503 - a IA nao esta configurada ou nao respondeu (limite do plano gratuito, rede, timeout). */
public class IaIndisponivelException extends RuntimeException {

    public IaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
