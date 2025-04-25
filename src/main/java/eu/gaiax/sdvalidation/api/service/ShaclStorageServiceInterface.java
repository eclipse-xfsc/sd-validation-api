package eu.gaiax.sdvalidation.api.service;

import org.springframework.web.multipart.MultipartFile;

public interface ShaclStorageServiceInterface {
    void uploadShacl(MultipartFile shaclFile);
}
