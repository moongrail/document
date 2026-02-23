package ru.itq.fun.generator.api;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeneratorRunner implements ApplicationRunner {

    private final DocumentGeneratorClient client;

    @Override
    public void run(ApplicationArguments args) {
        client.generateDocuments();
    }
}
