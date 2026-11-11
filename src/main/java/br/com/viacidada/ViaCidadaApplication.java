package br.com.viacidada;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ViaCidadaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViaCidadaApplication.class, args);
    }
}
