package eu.gaiax.sdvalidation.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "eu.gaiax.sdvalidation")
public class VicShapeProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VicShapeProcessorApplication.class, args);
    }
}
