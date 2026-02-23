package ru.itq.fun.generator;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.itq.fun.generator.config.GeneratorProperties;

@SpringBootApplication
@EnableConfigurationProperties(GeneratorProperties.class)
@Slf4j
public class GeneratorApplication {

    @SneakyThrows
    public static void main(String[] args) {
        //Можно было и покрасивее сделать. и вообще отказаться
        SpringApplication.run(GeneratorApplication.class, args);
        Thread.sleep(20000);
        log.info("Graceful shutdown");
        System.exit(0);
    }

}
