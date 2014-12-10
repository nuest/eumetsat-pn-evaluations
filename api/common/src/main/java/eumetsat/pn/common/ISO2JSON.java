/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common;

import com.github.autermann.yaml.Yaml;
import com.github.autermann.yaml.YamlNode;
import com.github.autermann.yaml.YamlNodeFactory;
import eumetsat.pn.common.util.JSONPrettyWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Guilaume Aubert, Daniel Nüst
 */
public abstract class ISO2JSON {

    protected static final Logger log = LoggerFactory.getLogger(ISO2JSON.class);

    protected static final String DEFAULT_CONFIG_FILE = "/feederconfig.yml";

    protected static final String FILE_IDENTIFIER_PROPERTY = "fileIdentifier";

    private Writer writer = new JSONPrettyWriter();

    protected YamlNode config = YamlNodeFactory.createDefault().nullNode();

    public ISO2JSON() {
        this(DEFAULT_CONFIG_FILE);
    }

    public ISO2JSON(String configFile) {
        try (InputStream fis = ISO2JSON.class.getResourceAsStream(configFile)) {
            YamlNode n = new Yaml().load(fis);
            this.config = n.get(getConfigBasename()).get("feeder");
        } catch (IOException e) {
            log.error("Could not load config from file {}", configFile, e);
        }

        log.info("NEW {} based on {}", this.toString(), configFile);
    }

    /**
     * Parse the hierarchy name to tentatively form facets
     *
     * @param hierarchyNames
     */
    @SuppressWarnings("unchecked")
    private JSONObject parseThemeHierarchy(String fid, JSONArray hierarchyNames) {
        String dummy = null;
        JSONObject jsonObject = new JSONObject();

        for (Object hName : hierarchyNames) {
            dummy = (String) hName;

            String[] elems = dummy.split("\\.");

//           log.trace("Analyze " + dummy);
            if (elems[0].equalsIgnoreCase("sat")) {
                if (elems[1].equalsIgnoreCase("METOP")) {
                    jsonObject.put("satellite", "METOP");

                    if (elems.length > 2) {
                        //there is an instrument
                        jsonObject.put("instrument", elems[2]);
                    }
                } else if (elems[1].startsWith("JASON")) {
                    jsonObject.put("satellite", elems[1]);

                    if (elems.length > 2) {
                        //there is an instrument
                        jsonObject.put("instrument", elems[2]);
                    }
                } else {
                    jsonObject.put("satellite", elems[1]);
                }
            } else if (elems[0].equalsIgnoreCase("theme")) {
                if (elems[1].equalsIgnoreCase("par") && elems.length > 2) {
                    if (elems[2].equalsIgnoreCase("sea_surface_temperature")) {
                        elems[2] = "sst";
                    } else if (elems[2].equalsIgnoreCase("level_0_data")) {
                        elems[2] = "level0 ";
                    } else if (elems[2].equalsIgnoreCase("level_1_data")) {
                        elems[2] = "level1";
                    } else if (elems[2].equalsIgnoreCase("level_2_data")) {
                        elems[2] = "level2";
                    }

                    if (!jsonObject.containsKey("category")) {
                        JSONArray array = new JSONArray();
                        array.add(elems[2]);
                        jsonObject.put("category", array);
                    } else {
                        ((JSONArray) (jsonObject.get("category"))).add(elems[2]);
                    }

                }
            } else if (elems[0].equalsIgnoreCase("dis")) {
                if (elems.length == 2) {
                    if (!elems[1].equalsIgnoreCase("Eumetcast")) {
                        if (!jsonObject.containsKey("distribution")) {
                            JSONArray array = new JSONArray();
                            array.add(elems[1]);
                            jsonObject.put("distribution", array);
                        } else {
                            ((JSONArray) (jsonObject.get("distribution"))).add(elems[1]);
                        }
                    }

                } else {
                    log.debug("***  ALERT ALERT. DIS is different: " + hName);
                }
            } else if (elems[0].equalsIgnoreCase("SBA")) {
                if (elems.length == 2) {
                    if (!jsonObject.containsKey("societalBenefitArea")) {
                        JSONArray array = new JSONArray();
                        array.add(elems[1]);
                        jsonObject.put("societalBenefitArea", array);
                    } else {
                        ((JSONArray) (jsonObject.get("societalBenefitArea"))).add(elems[1]);
                    }

                } else {
                    log.debug("***  ALERT ALERT. SBA is different: " + hName);
                }
            }
        }

        return jsonObject;
    }

    @SuppressWarnings("unchecked")
    private Path createInfoToIndex(Path aSourceDirPath, Path aDestDirPath) {
        log.info("Transforming XML in {} to JSON in {}", aSourceDirPath, aDestDirPath);

        DocumentBuilder builder;
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory
                    .newInstance();
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.error("Error transforming directory", e);
            return aDestDirPath;
        }

        Collection<File> inputFiles = FileUtils.listFiles(aSourceDirPath.toFile(), null, false);
        int counter = 0;
        for (File file : inputFiles) {
            try {
                FileInputStream fileInput = new FileInputStream(file);
                Document xmlDocument = builder.parse(fileInput);

                JSONObject json = convert(xmlDocument);

                json.writeJSONString(writer);
                log.trace("JSON Result Object: {}", writer.toString());

                String fName = aDestDirPath + "/" + FilenameUtils.getBaseName(file.getName()) + ".json";

                FileUtils.writeStringToFile(new File(fName), json.toJSONString());
                log.debug("Wrote metadata with id {} (file {}) as json to {} ", json.get(FILE_IDENTIFIER_PROPERTY), file, fName);
                counter++;
            } catch (SAXException | IOException | XPathExpressionException e) {
                log.error("Error transforming file {}", file, e);
            }
        }

        log.info("Transformed {} of {} files", counter, inputFiles.size());
        return aDestDirPath;
    }

    private void appendIfResultNotNull(XPath xpath, Document xml, StringBuilder sb, String expression) throws XPathExpressionException {
        String result = xpath.compile(expression).evaluate(xml);
        if (result != null) {
            sb.append(result.trim());
        }
    }

    private JSONObject convert(Document xmlDocument) throws XPathExpressionException, IOException {
        String expression = null;
        String result = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        JSONObject jsonObject = new JSONObject();

        String xpathFileID = "//*[local-name()='fileIdentifier']/*[local-name()='CharacterString']";
        String fileID = xPath.compile(xpathFileID).evaluate(xmlDocument);
        log.trace("{} >>> {}", xpathFileID, fileID);
        if (fileID != null) {
            jsonObject.put(FILE_IDENTIFIER_PROPERTY, fileID);
        }

        expression = "//*[local-name()='hierarchyLevelName']/*[local-name()='CharacterString']";
        JSONArray list = new JSONArray();
        NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(
                xmlDocument, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            list.add(nodeList.item(i).getFirstChild().getNodeValue());
        }
        if (list.size() > 0) {
            JSONObject hierarchies = parseThemeHierarchy((String) jsonObject.get(FILE_IDENTIFIER_PROPERTY), list);
            hierarchies.writeJSONString(writer);
            jsonObject.put("hierarchyNames", hierarchies);
            log.trace("{} >>> {}", expression, jsonObject.get("hierarchyNames"));
        }

        // Get Contact info
        String deliveryPoint = "//*[local-name()='address']//*[local-name()='deliveryPoint']/*[local-name()='CharacterString']";
        String city = "//*[local-name()='address']//*[local-name()='city']/*[local-name()='CharacterString']";
        String administrativeArea = "//*[local-name()='address']//*[local-name()='administrativeArea']/*[local-name()='CharacterString']";
        String postalCode = "//*[local-name()='address']//*[local-name()='postalCode']/*[local-name()='CharacterString']";
        String country = "//*[local-name()='address']//*[local-name()='country']/*[local-name()='CharacterString']";
        String email = "//*[local-name()='address']//*[local-name()='electronicMailAddress']/*[local-name()='CharacterString']";

        StringBuilder addressString = new StringBuilder();
        StringBuilder emailString = new StringBuilder();

        appendIfResultNotNull(xPath, xmlDocument, addressString, deliveryPoint);

        result = xPath.compile(postalCode).evaluate(xmlDocument);
        if (result != null) {
            addressString.append("\n").append(result.trim());
        }

        result = xPath.compile(city).evaluate(xmlDocument);
        if (result != null) {
            addressString.append(" ").append(result.trim());
        }

        result = xPath.compile(administrativeArea).evaluate(xmlDocument);
        if (result != null) {
            addressString.append("\n").append(result.trim());
        }

        result = xPath.compile(country).evaluate(xmlDocument);
        if (result != null) {
            addressString.append("\n").append(result.trim());
        }

        result = xPath.compile(email).evaluate(xmlDocument);
        if (result != null) {
            emailString.append(result.trim());
        }

        HashMap<String, String> map = new HashMap<>();
        map.put("address", addressString.toString());
        map.put("email", emailString.toString());
        jsonObject.put("contact", map);
        log.trace("contact: {}", Arrays.toString(map.entrySet().toArray()));

        // add identification info
        String abstractStr = "//*[local-name()='identificationInfo']//*[local-name()='abstract']/*[local-name()='CharacterString']";
        String titleStr = "//*[local-name()='identificationInfo']//*[local-name()='title']/*[local-name()='CharacterString']";
        String statusStr = "//*[local-name()='identificationInfo']//*[local-name()='status']/*[local-name()='MD_ProgressCode']/@codeListValue";
        String keywords = "//*[local-name()='keyword']/*[local-name()='CharacterString']";

        HashMap<String, Object> idMap = new HashMap<>();

        result = xPath.compile(titleStr).evaluate(xmlDocument);
        if (result != null) {
            idMap.put("title", result.trim());
        }

        result = xPath.compile(abstractStr).evaluate(xmlDocument);
        if (result != null) {
            idMap.put("abstract", result.trim());
        }

        result = xPath.compile(statusStr).evaluate(xmlDocument);
        if (result != null) {
            idMap.put("status", result.trim());
        }

        list = new JSONArray();
        nodeList = (NodeList) xPath.compile(keywords).evaluate(xmlDocument,
                XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            list.add(nodeList.item(i).getFirstChild().getNodeValue());
        }

        if (list.size() > 0) {
            idMap.put("keywords", list);
        }

        jsonObject.put("identificationInfo", idMap);
        log.trace("idMap: {}", idMap);

        // get thumbnail product
        String browseThumbnailStr = "//*[local-name()='graphicOverview']//*[local-name()='MD_BrowseGraphic']//*[local-name()='fileName']//*[local-name()='CharacterString']";
        result = xPath.compile(browseThumbnailStr).evaluate(xmlDocument);
        if (result != null) {
            idMap.put("thumbnail", result.trim());
            log.trace("thumbnail: {}", result);
        }

        // add Geo spatial information
        String westBLonStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='westBoundLongitude']/*[local-name()='Decimal']";
        String eastBLonStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='eastBoundLongitude']/*[local-name()='Decimal']";
        String northBLatStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='northBoundLatitude']/*[local-name()='Decimal']";
        String southBLatStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='southBoundLatitude']/*[local-name()='Decimal']";

        // create a GeoJSON envelope object
        HashMap<String, Object> latlonMap = new HashMap<>();
        latlonMap.put("type", "envelope");

        JSONArray envelope = new JSONArray();
        JSONArray leftTopPt = new JSONArray();
        JSONArray rightDownPt = new JSONArray();

        result = xPath.compile(westBLonStr).evaluate(xmlDocument);
        if (result != null) {
            leftTopPt.add(Double.parseDouble(result.trim()));
        }

        result = xPath.compile(northBLatStr).evaluate(xmlDocument);
        if (result != null) {
            leftTopPt.add(Double.parseDouble(result.trim()));
        }

        result = xPath.compile(eastBLonStr).evaluate(xmlDocument);
        if (result != null) {
            rightDownPt.add(Double.parseDouble(result.trim()));
        }

        result = xPath.compile(southBLatStr).evaluate(xmlDocument);
        if (result != null) {
            rightDownPt.add(Double.parseDouble(result.trim()));
        }

        envelope.add(leftTopPt);
        envelope.add(rightDownPt);

        latlonMap.put("coordinates", envelope);
        jsonObject.put("location", latlonMap);

        return jsonObject;
    }

    /**
     * iterate through all files in the given directory and store it in the
     * search database.
     *
     * @param dir the directory to index
     */
    protected abstract void indexDirContent(Path dir);

    public void transformAndIndex() throws IOException {
        Path tempdir = Files.createTempDirectory(config.get("tempdirnameprefix").asTextValue());
        createInfoToIndex(Paths.get(config.get("srcdir").asTextValue()), tempdir);

        if (config.get("index").asBooleanValue(true)) {
            indexDirContent(tempdir);
        } else {
            log.info("Testing enabled, not indexing documents from file:///{}", tempdir);
        }
    }

    protected abstract String getConfigBasename();

}
