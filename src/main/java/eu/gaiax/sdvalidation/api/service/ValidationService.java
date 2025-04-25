package eu.gaiax.sdvalidation.api.service;

import com.github.jsonldjava.utils.JsonUtils;
import eu.gaiax.sdvalidation.api.Constants;
import eu.gaiax.sdvalidation.api.ValidationResult;
import eu.gaiax.sdvalidation.api.dto.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.topbraid.jenax.util.JenaUtil;
import org.topbraid.shacl.engine.Shape;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.model.SHPropertyShape;
import org.topbraid.shacl.model.SHShape;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 'convertToJson' is the primary method called from the controller class. It
 * converts an input multipart shacl file into a json object for the frontend.
 */
@Service
public class ValidationService implements ValidationServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);
    private static final Path BASE_PATH = Paths.get(".").toAbsolutePath().normalize();
    private static final Marker WTF_MARKER = MarkerFactory.getMarker("WTF");

    /**
     * Accepts a multipart RDF/Turtle file and converts it to a specific JSON file structure
     *
     * @param file a multipart RDF/Turtle file to process
     * @return a processed SHACL model to be serialized to JSON and processed by the frontend later
     * @throws IOException in case of access errors (if the temporary store fails)
     */
    public ShaclModel convertToJson(MultipartFile file) throws IOException {

        logger.info("Converting to JSON: {}", file.getOriginalFilename());

        var model = ModelFactory.createDefaultModel();
        model.read(file.getInputStream(), null, Constants.TTL);
        var prefixList = createPrefixList(model);

        var shapes = new ShapesGraph(model).getRootShapes().stream()
                .map(Shape::getShapeResource)
                .map(shape -> constructVicShape(shape, prefixList))
                .collect(Collectors.toList());

        return new ShaclModel(prefixList, shapes);
    }

    public static ValidationResult validateJSON_LD(String json) {
        Object parsed;
        try {
            parsed = JsonUtils.fromString(json);
        } catch (IOException e) {
            return new ValidationResult(false, e.getMessage());
        }

        Map parsedLHM = (LinkedHashMap) parsed;
        Set<String> keys = parsedLHM.keySet();
        UrlValidator urlValidator = new UrlValidator();
        for (String k : keys) {
            if (k.charAt(0) == '@') {
                // is valid url:
                if (!urlValidator.isValid((String) parsedLHM.get(k))) {
                    return new ValidationResult(false, "context is no valid URL");
                }
            }
        }
        return new ValidationResult(true, "");
    }

    public static ValidationResult SDhasCrypto(String json) {
        Object parsed;
        try {
            parsed = JsonUtils.fromString(json);
        } catch (IOException e) {
            return new ValidationResult(false, e.getMessage());
        }

        Map parsedLHM = (LinkedHashMap) parsed;

        if (!parsedLHM.containsKey("proof")) {
            return new ValidationResult(false, "no proof found");
        }

        Map proofLHM = (LinkedHashMap) parsedLHM.get("proof");
        if (!proofLHM.containsKey("type") || !proofLHM.get("type").equals("JsonWebSignature2020")) {
            return new ValidationResult(false, "wrong type of proof");
        }

        if (!proofLHM.containsKey("created")) {
            return new ValidationResult(false, "created timestamp not found");
        }

        String created = (String) proofLHM.get("created");

        if (created.isEmpty() || created.isBlank()) {
            return new ValidationResult(false, "created timestamp is empty");
        }

        String regex_iso8601 = "(\\d{4}-\\d{2}-\\d{2})[A-Z]+(\\d{2}:\\d{2}:\\d{2}).";
        Pattern p = Pattern.compile(regex_iso8601);
        Matcher m = p.matcher(created);
        if (!m.matches()) {
            return new ValidationResult(false, "created timestamp is not in ISO8601 Format");
        }

        if (!proofLHM.containsKey("verificationMethod")) {
            return new ValidationResult(false, "verificationMethod not found");
        }
        String verificationMethod = (String) proofLHM.get("verificationMethod");
        if (verificationMethod.isEmpty() || verificationMethod.isBlank()) {
            return new ValidationResult(false, "verificationMethod is empty");
        }

        if (!proofLHM.containsKey("proofPurpose")) {
            return new ValidationResult(false, "proofPurpose not found");
        }
        String proofPurpose = (String) proofLHM.get("proofPurpose");
        if (proofPurpose.isEmpty() || proofPurpose.isBlank()) {
            return new ValidationResult(false, "proofPurpose is empty");
        }

        if (!proofLHM.containsKey("jws")) {
            return new ValidationResult(false, "jws not found");
        }
        String jws = (String) proofLHM.get("created");
        if (jws.isEmpty() || jws.isBlank()) {
            return new ValidationResult(false, "jws is empty");
        }

        return new ValidationResult(true, "");
    }

    public String extractCredentialSubjects (String json) {
        Object parsed;
        try {
            parsed = JsonUtils.fromString(json);
        } catch (IOException e) {
            // This should never happen, since it's checked before
            return e.getMessage();
        }

        ArrayList credentialSubjects = new ArrayList<>();

        LinkedHashMap parsedLHM = (LinkedHashMap)parsed;
        Object verifiableCredentials = parsedLHM.get("verifiableCredential");

        if (verifiableCredentials instanceof  ArrayList) {
            for (Object credentials : (ArrayList) verifiableCredentials) {
                LinkedHashMap verifiableCredential = (LinkedHashMap) credentials;
                Object _credentialSubjects_Obj = verifiableCredential.get("credentialSubject");
                if (_credentialSubjects_Obj instanceof  ArrayList) {
                    ArrayList _credentialSubjects = (ArrayList) _credentialSubjects_Obj;
                    for (Object _credentialSubject_Obj : _credentialSubjects) {
                        LinkedHashMap _credentialSubject = (LinkedHashMap) _credentialSubject_Obj;
                        credentialSubjects.add(_credentialSubject);
                    }
                } else {
                    LinkedHashMap _credentialSubject = (LinkedHashMap) _credentialSubjects_Obj;
                    credentialSubjects.add(_credentialSubject);
                }
            }
        }

        try {
            return JsonUtils.toString(credentialSubjects);
        } catch (IOException e) {
            return "";
        }
    }

    private List<ShapeProperties> extractProperties(SHShape shape, List<Map<String, String>> prefixList) {
        var res = new ArrayList<ShapeProperties>();


        for (var propertyShape : shape.getPropertyShapes()) {
            var path = getClassConstraint(propertyShape, prefixList, SH.path);
            Map<String, String> datatype = getDatatype(propertyShape, prefixList);
            datatype = processShNodekind(propertyShape, datatype);

            var validations = new ArrayList<ConstraintOption>();
            for (Property property : new Property[]{SH.minLength, SH.maxLength, SH.minInclusive, SH.maxInclusive, SH.minExclusive, SH.maxExclusive}) {
                var value = readIntProperty(propertyShape, property);
                if (value != null) {
                    validations.add(new ConstraintOption(property.getLocalName(), value));
                }
            }

            var group = getNode(propertyShape, SH.group);
            if (group != null) {
                validations.add(new ConstraintOption(SH.group.getLocalName(), group));
            }

            res.add(new ShapeProperties(
                    path,
                    readStringProperty(propertyShape, SH.name),
                    datatype,
                    getClassConstraint(propertyShape, prefixList, SH.class_),
                    readIntProperty(propertyShape, SH.minCount),
                    readIntProperty(propertyShape, SH.maxCount),
                    getInProperties(propertyShape, prefixList),
                    readIntProperty(propertyShape, SH.order),
                    validations,
                    getNode(propertyShape, SH.node),
                    readStringProperty(propertyShape, SH.description),
                    setOrProperties(propertyShape, prefixList)
            ));
        }
        return res;
    }

    private VicShape constructVicShape(SHShape shape, List<Map<String, String>> prefixList) {
        var properties = extractProperties(shape, prefixList);
        var target = shape.getProperty(SH.targetClass).getObject().asResource();

        return new VicShape(
                properties,
                shape.getLocalName(),
                getPrefixAlias(prefixList, target.getNameSpace()),
                target.getLocalName());
    }

    /**
     * Method to create a list of all prefixes used in the shacl file. Consists of
     * list of Java Map objects each with keys 'url' for the prefix urls and
     * 'alias'.
     */
    private List<Map<String, String>> createPrefixList(Model model) {

        List<Map<String, String>> prefixList = null;
        if (!model.getNsPrefixMap().isEmpty()) {
            prefixList = new ArrayList<>();
            for (String key : model.getNsPrefixMap().keySet()) {
                Map<String, String> prefix = new HashMap<>();
                prefix.put(Constants.ALIAS, key);
                prefix.put(Constants.URL.toLowerCase(), model.getNsPrefixMap().get(key));
                prefixList.add(prefix);
            }
        }
        return prefixList;
    }

    private String getPrefixAlias(List<Map<String, String>> prefixList, String url) {

        for (Map<String, String> m : prefixList) {
            if (m.containsValue(url)) {
                return m.get(Constants.ALIAS);
            }
        }
        return null;
    }

    private List<ShapeProperties> setOrProperties(SHPropertyShape propertyShape, List<Map<String, String>> prefixList) {

        if (!propertyShape.hasProperty(SH.or)) {
            return null; // We explicitly return null so that it is skipped during JSON serialization
        }

        var or = new ArrayList<ShapeProperties>();
        for (RDFNode rdfNode : propertyShape.getProperty(SH.or).getList().asJavaList()) {

            Map<String, String> datatype = getDatatype(rdfNode.asResource(), prefixList);
            ClassConstraint clazz = getClassConstraint(rdfNode.asResource(), prefixList, SH.class_);
            ClassConstraint path = getClassConstraint(rdfNode.asResource(), prefixList, SH.path);
            Integer minCount = readIntProperty(rdfNode.asResource(), SH.minCount);
            Integer maxCount = readIntProperty(rdfNode.asResource(), SH.maxCount);

            or.add(new ShapeProperties(path, null, datatype, clazz, minCount, maxCount));
        }
        return or;
    }

    private Map<String, String> getDatatype(Resource resource, List<Map<String, String>> prefixList) {

        var res = new HashMap<String, String>();
        if (resource.hasProperty(SH.datatype)) {
            Resource datatype = resource.getProperty(SH.datatype).getResource();
            res.put(Constants.VALUE, datatype.getLocalName());
            res.put(Constants.PREFIX, getPrefixAlias(prefixList, datatype.getNameSpace()));
        }
        return res;
    }

    private ClassConstraint getClassConstraint(Resource resource, List<Map<String, String>> prefixList, Property property) {
        if (resource.hasProperty(property)) {
            var res = resource.getProperty(property).getResource();
            String prefix = getPrefixAlias(prefixList, res.getNameSpace());
            return new ClassConstraint(prefix, res.getLocalName());
        } else {
            return null;
        }
    }

    private Statement readProperty(Resource resource, Property property) {
        if (resource.hasProperty(property)) {
            return resource.getProperty(property);
        } else {
            return null;
        }
    }

    private Integer readIntProperty(Resource resource, Property property) {
        var value = readProperty(resource, property);
        if (value != null) {
            return value.getInt();
        } else {
            return null;
        }
    }


    private String readStringProperty(Resource resource, Property property) {
        var value = readProperty(resource, property);
        if (value != null) {
            return value.getString();
        } else {
            return null;
        }
    }


    private List<ClassConstraint> getInProperties(Resource resource, List<Map<String, String>> prefixList) {

        var res = new ArrayList<ClassConstraint>();

        if (resource.hasProperty(SH.in)) {
            for (var node : resource.getProperty(SH.in).getList().asJavaList()) {
                if (node.isLiteral()) {
                    res.add(new ClassConstraint(null, node.asLiteral().getString()));
                } else if (node.isResource()) {
                    res.add(new ClassConstraint(getPrefixAlias(prefixList, node.asResource().getNameSpace()),
                            node.asResource().getLocalName()));
                }
            }
        }
        return res;
    }

    /**
     * Whenever sh:nodekind sh:IRI is present, set datatype prefix as 'URL'
     */
    private Map<String, String> processShNodekind(Resource resource, Map<String, String> datatype) {

        if (resource.hasProperty(SH.nodeKind) && SH.IRI.equals(resource.getProperty(SH.nodeKind).getObject())) {
            if (null == datatype)
                datatype = new HashMap<>();
            datatype.put(Constants.PREFIX, Constants.NODE_KIND);
            datatype.put(Constants.VALUE, Constants.IRI);
        }

        return datatype;
    }

    private String getNode(Resource resource, Property property) {
        var value = readProperty(resource, property);
        if (value != null) {
            return value.getAlt().getLocalName();
        } else {
            return null;
        }
    }

    /**
     * Validate a datagraph against shaclShape from pre-defined files stored in the file system
     *
     * @param dataGraphPath   string indicates the data graph file path
     * @param shaclShapesPath string indicates the shacl shapes file path
     * @return Serialization for The JSON report Result
     */
    public String validate(String dataGraphPath, String shaclShapesPath) {
        StringBuilder reportResult = new StringBuilder();
        OutputStream reportOutputStream;
        try {
            String data = BASE_PATH.toFile().getAbsolutePath() + dataGraphPath;
            String shape = BASE_PATH.toFile().getAbsolutePath() + shaclShapesPath;
            Model dataModel = JenaUtil.createDefaultModel();
            dataModel.read(data);
            Model shapeModel = JenaUtil.createDefaultModel();
            shapeModel.read(shape);

            Resource reportResource = ValidationUtil.validateModel(dataModel, shapeModel, true);
            boolean conforms = reportResource.getProperty(SH.conforms).getBoolean();
            logger.trace("Conforms = " + conforms);

            if (!conforms) {
                String report = BASE_PATH.toFile().getAbsolutePath() + "/src/main/resources/report.ttl";
                File reportFile = new File(report);
                reportFile.createNewFile();
                reportOutputStream = new FileOutputStream(reportFile);

                RDFDataMgr.write(reportOutputStream, reportResource.getModel(), RDFFormat.JSONLD);
                try {
                    Scanner scanner = new Scanner(reportFile);
                    while (scanner.hasNextLine()) {
                        reportResult.append(scanner.nextLine());
                    }
                    scanner.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

            }

        } catch (Throwable t) {
            logger.error(WTF_MARKER, t.getMessage(), t);
        }

        return reportResult.toString();

    }

    /**
     * Retrieve the pre-defined shacl shapes files stored in the resources folder of the remote Repository
     *
     * @return list of shacl shape files
     */
    public List<File> getShaclFiles() {
        String shaclFolderPathName = BASE_PATH.toFile().getAbsolutePath() + "/src/main/resources/shacl";
        List<File> shaclFilelistFiles = new ArrayList<>();
        File shaclFolder = new File(shaclFolderPathName);
        File[] list = shaclFolder.listFiles();
        for (File file : list) {
            if (!file.isDirectory()) {
                shaclFilelistFiles.add(file.getAbsoluteFile());
            }
        }
        return shaclFilelistFiles;
    }

    /**
     * Validate a dataGraph against a shacl shape of its type, It searches for all the related shapes to validate against
     *
     * @param dataGraphPath string indicates the data graph file path
     * @return Serialization for The JSON report Result
     */
    @Override
    public String validatePreDefined(String dataGraphPath) {
        return null;
    }

    /**
     * HELPER FUNCTION: prase data graph json file in oder to retrieve its type
     *
     * @param DataGraphFilePath string indicates the data graph file path
     * @return data graph type
     */
    public String getDataGraphType(String DataGraphFilePath) {
        String dataGraphInstance = BASE_PATH.toFile().getAbsolutePath() + DataGraphFilePath;
        JSONParser parser = new JSONParser();
        String dataGraphtype = " ";
        try {
            FileReader dataGraphJSONFile = new FileReader(dataGraphInstance);
            Object object = parser.parse(dataGraphJSONFile);
            JSONObject jsonObject = (JSONObject) object;
            String type = (String) jsonObject.get("@type");
            String[] result = type.split(":");
            dataGraphtype = result[1];
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataGraphtype;
    }

    public void test() {
        String shaclFolderPathName = BASE_PATH.toFile().getAbsolutePath() + "/src/main/resources/shacl";
        File shaclFolder = new File(shaclFolderPathName);
        Iterator<File> it = FileUtils.iterateFiles(shaclFolder, null, false);
        while (it.hasNext()) {
            // if (((File) it.next()).getName().toLowerCase().contains("resource")){
            System.out.println(it.next().getName());
            //  }
        }
    }

}

