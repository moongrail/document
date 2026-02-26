package ru.itq.fun.generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.itq.fun.generator.config.GeneratorProperties;

@SpringBootApplication
@EnableConfigurationProperties(GeneratorProperties.class)
@Slf4j
public class GeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
    }

}
