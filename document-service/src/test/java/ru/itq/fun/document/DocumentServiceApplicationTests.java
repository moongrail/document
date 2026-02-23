package ru.itq.fun.document;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.itq.fun.document.config.H2TestConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(H2TestConfig.class)
class DocumentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
