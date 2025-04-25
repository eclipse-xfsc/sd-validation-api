package eu.gaiax.sdvalidation.api;

import eu.gaiax.sdvalidation.api.service.ValidationService;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationServiceTest {
    static Path base_path = Paths.get(".").toAbsolutePath().normalize();

    private String readFile(String path) {
        StringBuilder result = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String line = "";
            do {
                result.append(line);
                line = reader.readLine();
            } while (line != null);
        } catch (FileNotFoundException e) {
            System.out.println(path);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result.toString();
    }

    @org.junit.jupiter.api.Test
    void validateJSON_LD_valid_Example_1() {
        String path =
                base_path.toFile().getAbsolutePath() + "/src/main/resources/JSON-LD-Tests/valid1.jsonld";
        String json = readFile(path);

        assertTrue(ValidationService.validateJSON_LD(json).isValid());
    }

    @org.junit.jupiter.api.Test
    void validateJSON_LD_valid_Example_2() {
        String path =
                base_path.toFile().getAbsolutePath() + "/src/main/resources/JSON-LD-Tests/valid2.jsonld";
        String json = readFile(path);

        assertTrue(ValidationService.validateJSON_LD(json).isValid());
    }

    @org.junit.jupiter.api.Test
    void validateJSON_LD_invalid_Example_1() {
        String path =
                base_path.toFile().getAbsolutePath() + "/src/main/resources/JSON-LD-Tests/invalid1.jsonld";
        String json = readFile(path);

        assertFalse(ValidationService.validateJSON_LD(json).isValid());
    }

    @org.junit.jupiter.api.Test
    void validateJSON_LD_invalid_Example_2() {
        String path =
                base_path.toFile().getAbsolutePath() + "/src/main/resources/JSON-LD-Tests/invalid2.jsonld";
        String json = readFile(path);

        assertFalse(ValidationService.validateJSON_LD(json).isValid());
    }
}