/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common;

import eumetsat.pn.common.util.FileSystem;
import eumetsat.pn.common.util.JSONPrettyWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
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
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Guilaume Aubert
 */
public class ISO2JSON {

    /**
     * Parse the hierarchy name to tentatively form facets
     *
     * @param hierarchyNames
     */
    @SuppressWarnings("unchecked")
    public static JSONObject parseThemeHierarchy(String fid, JSONArray hierarchyNames) {
        String dummy = null;
        JSONObject jsonObject = new JSONObject();

        for (Object hName : hierarchyNames) {
            dummy = (String) hName;

            String[] elems = dummy.split("\\.");

            System.out.println("Analyze " + dummy);

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
                    System.out.println("***  ALERT ALERT. DIS is different: " + hName);
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
                    System.out.println("***  ALERT ALERT. SBA is different: " + hName);
                }
            }
        }

        return jsonObject;
    }

    @SuppressWarnings("unchecked")
    public static void createInfoToIndex(String aSourceDirPath, String aDestDirPath) {

        try {
            String expression = null;
            String result = null;
            FileInputStream fileInput = null;

            for (File file : FileSystem.listDirectory(aSourceDirPath)) {

                // FileInputStream file = new FileInputStream(new
                // File("./etc/metadata/EO-EUM-DAT-METOP-AMSU-AHRPT.xml"));
                fileInput = new FileInputStream(file);

                DocumentBuilderFactory builderFactory = DocumentBuilderFactory
                        .newInstance();

                DocumentBuilder builder = builderFactory.newDocumentBuilder();

                Document xmlDocument = builder.parse(fileInput);

                XPath xPath = XPathFactory.newInstance().newXPath();

                String xpathFileID = "//*[local-name()='fileIdentifier']/*[local-name()='CharacterString']";

                JSONObject jsonObject = new JSONObject();

                // Get fileIDs
                System.out.println("*************************");
                // String expression = "/Employees/Employee[@emplid='3333']/email";
                System.out.println(xpathFileID);
                String fileID = xPath.compile(xpathFileID).evaluate(xmlDocument);
                System.out.println("result=" + fileID);

                if (fileID != null) {
                    jsonObject.put("fileIdentifier", fileID);
                }

                // Get hierarchyLevelNames
                System.out.println("*************************");
                expression = "//*[local-name()='hierarchyLevelName']/*[local-name()='CharacterString']";
                System.out.println(expression);
                JSONArray list = new JSONArray();
                NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(
                        xmlDocument, XPathConstants.NODESET);
                for (int i = 0; i < nodeList.getLength(); i++) {
                    list.add(nodeList.item(i).getFirstChild().getNodeValue());
                    System.out.println(nodeList.item(i).getFirstChild().getNodeValue());
                }

                if (list.size() > 0) {
                    JSONObject hierarchies = parseThemeHierarchy((String) jsonObject.get("fileIdentifier"), list);
                    Writer writer = new JSONPrettyWriter(); // this is the new writter that
                    // adds indentation.
                    hierarchies.writeJSONString(writer);
                    System.out.println("JSON Result hierarchies: " + writer.toString());

                    jsonObject.put("hierarchyNames", hierarchies);
                }

                // Get Contact info
                String deliveryPoint = "//*[local-name()='address']//*[local-name()='deliveryPoint']/*[local-name()='CharacterString']";
                String city = "//*[local-name()='address']//*[local-name()='city']/*[local-name()='CharacterString']";
                String administrativeArea = "//*[local-name()='address']//*[local-name()='administrativeArea']/*[local-name()='CharacterString']";
                String postalCode = "//*[local-name()='address']//*[local-name()='postalCode']/*[local-name()='CharacterString']";
                String country = "//*[local-name()='address']//*[local-name()='country']/*[local-name()='CharacterString']";
                String email = "//*[local-name()='address']//*[local-name()='electronicMailAddress']/*[local-name()='CharacterString']";

                String addressString = "";
                String emailString = "";

                result = xPath.compile(deliveryPoint).evaluate(xmlDocument);

                if (result != null) {
                    addressString += result.trim();
                }

                result = xPath.compile(postalCode).evaluate(xmlDocument);

                if (result != null) {
                    addressString += "\n" + result.trim();
                }

                result = xPath.compile(city).evaluate(xmlDocument);

                if (result != null) {
                    addressString += " " + result.trim();
                }
                result = xPath.compile(administrativeArea).evaluate(xmlDocument);

                if (result != null) {
                    addressString += "\n" + result.trim();
                }

                result = xPath.compile(country).evaluate(xmlDocument);

                if (result != null) {
                    addressString += "\n" + result.trim();
                }

                System.out.println("address =" + addressString);

                result = xPath.compile(email).evaluate(xmlDocument);

                if (result != null) {
                    emailString += result.trim();
                }

                System.out.println("email =" + emailString);
                HashMap<String, String> map = new HashMap<String, String>();
                map.put("address", addressString);
                map.put("email", emailString);

                jsonObject.put("contact", map);

                // add identification info
                String abstractStr = "//*[local-name()='identificationInfo']//*[local-name()='abstract']/*[local-name()='CharacterString']";
                String titleStr = "//*[local-name()='identificationInfo']//*[local-name()='title']/*[local-name()='CharacterString']";
                String statusStr = "//*[local-name()='identificationInfo']//*[local-name()='status']/*[local-name()='MD_ProgressCode']/@codeListValue";
                String keywords = "//*[local-name()='keyword']/*[local-name()='CharacterString']";

                HashMap<String, Object> idMap = new HashMap<String, Object>();

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
                    System.out.println(nodeList.item(i).getFirstChild()
                            .getNodeValue());
                }

                if (list.size() > 0) {
                    idMap.put("keywords", list);
                }

                System.out.println("idMap =" + idMap);

                jsonObject.put("identificationInfo", idMap);

                // get thumbnail product
                String browseThumbnailStr = "//*[local-name()='graphicOverview']//*[local-name()='MD_BrowseGraphic']//*[local-name()='fileName']//*[local-name()='CharacterString']";

                result = xPath.compile(browseThumbnailStr).evaluate(xmlDocument);

                if (result != null) {
                    idMap.put("thumbnail", result.trim());
                }

                // add Geo spatial information
                String westBLonStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='westBoundLongitude']/*[local-name()='Decimal']";
                String eastBLonStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='eastBoundLongitude']/*[local-name()='Decimal']";
                String northBLatStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='northBoundLatitude']/*[local-name()='Decimal']";
                String southBLatStr = "//*[local-name()='extent']//*[local-name()='geographicElement']//*[local-name()='southBoundLatitude']/*[local-name()='Decimal']";

                // create a GeoJSON envelope object
                HashMap<String, Object> latlonMap = new HashMap<String, Object>();
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

                Writer writer = new JSONPrettyWriter();
                jsonObject.writeJSONString(writer);

                System.out.println("JSON Result Object: " + writer.toString());

                String fName = aDestDirPath + "/" + FilenameUtils.getBaseName(file.getName()) + ".json";

                System.out.println("Write result in " + fName);

                FileUtils.writeStringToFile(new File(fName), jsonObject.toJSONString());

            }

            System.out.println("*************************");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    public static void indexDirContent(String aSrcDir) {
        String jsonStr = null;
        JSONParser parser = new JSONParser();
        JSONObject jsObj = null;

        Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elasticsearch").build();
        TransportClient client = new TransportClient(settings);
        client.addTransportAddress(new InetSocketTransportAddress("localhost", 9300));

        try {

            int cpt = 0;
            for (File file : FileSystem.listDirectory(aSrcDir, new SuffixFileFilter(".json"))) {
                jsonStr = FileUtils.readFileToString(file);
                jsObj = (JSONObject) parser.parse(jsonStr);

                IndexResponse response = client.prepareIndex("eumetsat-catalogue", "product", (String) jsObj.get("fileIdentifier"))
                        .setSource(jsObj.toJSONString())
                        .execute()
                        .actionGet();

                cpt++;

                System.out.println("response = " + response.getId() + " [] version = " + response.getVersion());
            }

            System.out.println("Indexed " + cpt + " files.");

        } catch (Exception e) {
            e.printStackTrace();
        }

        // on shutdown
        client.close();
    }

    public static void main(String[] args) {
        String srcDir = "etc/metadata";
        String destDir = "/tmp/json-results";

        createInfoToIndex(srcDir, destDir);

        indexDirContent(destDir);
    }
}
