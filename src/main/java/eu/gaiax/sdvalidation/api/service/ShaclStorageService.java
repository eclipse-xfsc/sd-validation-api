package eu.gaiax.sdvalidation.api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ShaclStorageService implements ShaclStorageServiceInterface {
    private static final Path BASE_PATH = Paths.get(".").toAbsolutePath().normalize();


    /**
     * Upload a local shacl files to the pre-defined shacl folder in the Remote repository
     *
     * @param shaclFile shacl file to be uploaded
     */
    @Override
    public void uploadShacl(MultipartFile shaclFile) {
        String data = BASE_PATH.toFile().getAbsolutePath() + "/src/main/resources/shacl/" + shaclFile.getOriginalFilename();
        try {
            Files.copy(shaclFile.getInputStream(), Path.of(data));
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());

        }

    }
}
