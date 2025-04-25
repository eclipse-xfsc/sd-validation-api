package eu.gaiax.sdvalidation.api.service;

import eu.gaiax.sdvalidation.api.dto.ShaclModel;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface ValidationServiceInterface {
    List<File> getShaclFiles();

    String validate(String dataGraphPath, String shaclShapesPath);

    String validatePreDefined(String dataGraphPath);

    ShaclModel convertToJson(MultipartFile file) throws IOException;

}

