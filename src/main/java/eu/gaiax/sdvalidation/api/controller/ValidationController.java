package eu.gaiax.sdvalidation.api.controller;

import eu.gaiax.sdvalidation.api.ValidationResult;
import eu.gaiax.sdvalidation.api.dto.ShaclModel;
import eu.gaiax.sdvalidation.api.service.ShaclStorageService;
import eu.gaiax.sdvalidation.api.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main entry point for REST calls. Provides access to pre-defined files as well as a Turtle to JSON preprocessor for the frontend.
 */
@RestController
public class ValidationController {
    private static final Logger logger = LoggerFactory.getLogger(ValidationController.class);

    private final ValidationService validationService;
    private final ShaclStorageService shaclStorageService;

    @Autowired
    public ValidationController(ValidationService validationService, ShaclStorageService shaclStorageService) {
        this.validationService = validationService;
        this.shaclStorageService = shaclStorageService;
    }

    /**
     * Converts a given SHACL file to a specific JSON representation
     *
     * @param shaclFile a multipart SHACL file with *.ttl extension
     * @return a JSON serialization of the processed content to be used in VIC
     */
    @PostMapping(value = "/convertFile", produces = "application/json")
    @CrossOrigin(origins = "*")
    @ResponseBody
    public ResponseEntity<ShaclModel> convertShacl(@RequestParam("file") MultipartFile shaclFile) {
        logger.info("Converting SHACL file: {}", shaclFile.getOriginalFilename());
        ShaclModel shaclModel;
        try {
            shaclModel = validationService.convertToJson(shaclFile);
        } catch (IOException | IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (null == shaclModel || (shaclModel.getPrefixList().isEmpty() && shaclModel.getShapes().isEmpty()))
            return new ResponseEntity<>(shaclModel, HttpStatus.BAD_REQUEST);
        return new ResponseEntity<>(shaclModel, HttpStatus.OK);
    }

    /**
     * Returns all available predefined SHACL files. All *.json files same directory will be considered.
     *
     * @return a set of file names of all available SHACL files
     */
    @GetMapping(value = "/getAvailableShapes")
    @CrossOrigin(origins = "*")
    public Set<String> getAvailableShapes() {
        Set<String> availableShapes = new HashSet<>();

        try (var stream = Files.list(Paths.get("./"))) {
            availableShapes = stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(fileName -> fileName.endsWith(".json"))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            e.printStackTrace();
            // We intentionally drop any exception so that the list just stays empty.
        }

        return availableShapes;
    }

    /**
     * To be used after /getAvailableShapes. Returns the content of a predefined JSON file.
     *
     * @param name the file name to return. Can be chosen from /getAvailableShapes' response.
     * @return the full content of the respective JSON file.
     */
    @GetMapping(value = "/getJSON")
    @CrossOrigin(origins = "*")
    @ResponseBody
    public ResponseEntity<String> getJSON(String name) {
        if (name == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        String s;
        try {
            s = Files.readString(Paths.get(name).getFileName());
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(s, HttpStatus.OK);
    }

    /**
     * A GET request method that validates pre-defined data graph and shacl shape files against
     * each other
     *
     * @param dataGraphPath   string indicates the data graph file path
     * @param shaclShapesPath string indicates the shacl shapes file path
     * @return serialization of the validation report for the validation of data graph against a shapes graph .
     */
    @RequestMapping(value = "/validate", method = RequestMethod.GET)
    @CrossOrigin(origins = "*")
    public ResponseEntity<String> validate(@RequestParam("dataGraphPath") String dataGraphPath, @RequestParam("shaclShapesPath") String shaclShapesPath) {
        String serializedJsonResponseReport = validationService.validate(dataGraphPath, shaclShapesPath);
        return new ResponseEntity<>(serializedJsonResponseReport, HttpStatus.OK);
    }


    /**
     * A GET request method for retrieving all SHACL files in the shacl Folder
     *
     * @return list of shacl files of *.ttl extension
     */
    @RequestMapping(value = "/getShacl", method = RequestMethod.GET)
    @CrossOrigin(origins = "*")
    public List<File> getShacl() {
        return validationService.getShaclFiles();
    }

    /**
     * A POST request method to upload a local shacl files to the pre-defined shacl folder in the Remote repository
     * NOTE: FURTHER DEVELOPMENT NEEDED
     *
     * @param shaclFile shacl file to be uploaded
     * @return response entity contains the status message for uploading
     */
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    @CrossOrigin(origins = "*")
    public ResponseEntity<String> uploadShacl(@RequestParam("shaclFile") MultipartFile shaclFile) {
        String statusMessage;
        try {
            shaclStorageService.uploadShacl(shaclFile);
            statusMessage = "Uploaded the file successfully ";
            return new ResponseEntity<>(statusMessage, HttpStatus.OK);
        } catch (Exception e) {
            statusMessage = "Could not upload the file ";
            return new ResponseEntity<>(statusMessage, HttpStatus.BAD_REQUEST);
        }

    }

    /**
     * A GET Request method for validating a dataGraph against a shacl shape of its type, It searches for all the
     * related shapes to validate against
     * NOTE: FURTHER DEVELOPMENT NEEDED
     *
     * @param dataGraph string indicates the data graph file path
     * @return Serialization for The JSON report Result
     */
    @RequestMapping(value = "/validate/sd", method = RequestMethod.GET)
    @CrossOrigin(origins = "*")
    public ResponseEntity<String> validatePreDefined(@RequestParam("dataGraph") String dataGraph) {
        String serializedJsonResponseReport = validationService.validatePreDefined(dataGraph);
        return new ResponseEntity<>(serializedJsonResponseReport, HttpStatus.OK);
    }

    @RequestMapping(value = "/test", method = RequestMethod.GET)
    @CrossOrigin(origins = "*")
    public void test() {
        validationService.test();
    }

    /**
     * Validate the syntactic validity of given JSON-LD
     *
     * @return if valid: 200 "OK", else: 400 "reason"
     */
    @PutMapping(value = "/validateSyntax")
    @CrossOrigin(origins = "*")
    @ResponseBody
    public ResponseEntity<String> validateSyntax(@RequestBody String body) {
        ValidationResult result = ValidationService.validateJSON_LD(body);
        if (result.isValid()) {
            return new ResponseEntity<>("", HttpStatus.OK);
        } else {
            return new ResponseEntity<>(result.getReason(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Validate the given JSON-LD has Signature
     *
     * @return if valid: 200 "OK", else: 400 "reason"
     */
    @PutMapping(value = "/hasSignature")
    @CrossOrigin(origins = "*")
    @ResponseBody
    public ResponseEntity<String> hasSignature(@RequestBody String body) {
        ValidationResult result = ValidationService.SDhasCrypto(body);
        if (result.isValid()) {
            return new ResponseEntity<>("", HttpStatus.OK);
        } else {
            return new ResponseEntity<>(result.getReason(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Validate the given JSON-LD has Signature
     * @return if valid: 200 "OK", else: 400 "reason"
     */
    @PutMapping(value = "/extractCredentialSubjects")
    @CrossOrigin(origins = "*")
    @ResponseBody
    public ResponseEntity<String> extractCredentialSubjects(@RequestBody String body) {
        ValidationResult result = validationService.validateJSON_LD(body);
        if (!result.isValid()) {
            return new ResponseEntity<>(result.getReason(), HttpStatus.BAD_REQUEST);
        }
        result = validationService.SDhasCrypto(body);
        if (!result.isValid()) {
            return new ResponseEntity<>(result.getReason(), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(validationService.extractCredentialSubjects(body), HttpStatus.OK);
    }
}
