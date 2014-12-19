/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.elasticsearch;

import com.github.autermann.yaml.YamlNode;
import eumetsat.pn.common.ISO2JSON;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.apache.logging.log4j.core.config.Configurator;

/**
 *
 * @author danu
 */
public class ElasticsearchFeeder extends ISO2JSON {

    public static void main(String[] args) throws IOException {
        try {
            URI configuration = ISO2JSON.class.getResource("/feeder-log4j2.xml").toURI();
            Configurator.initialize("eumetsat.pn", null, configuration);
        } catch (URISyntaxException e) {
            log.error("Could not configure logging: {}", e.getMessage());
        }

        ISO2JSON transformer = new ElasticsearchFeeder();
        transformer.transformAndIndex();

        log.info("Finished.");
    }

    public ElasticsearchFeeder() {
        super();
    }

    public ElasticsearchFeeder(Path configFile) {
        super(configFile);
    }

    protected void indexDirContent(Path aSrcDir) {
        log.info("Indexing dir content {}", aSrcDir);

        JSONParser parser = new JSONParser();

        YamlNode endpointConfig = this.config.get("endpoint");

        log.info("Endpoint configuration: {}", endpointConfig);

        Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", endpointConfig.get("cluster.name").asTextValue()).build();

        try (TransportClient client = new TransportClient(settings);) {
            client.addTransportAddress(new InetSocketTransportAddress(endpointConfig.get("host").asTextValue(), endpointConfig.get("port").asIntValue()));
            int cpt = 0;
            Collection<File> inputFiles = FileUtils.listFiles(aSrcDir.toFile(), new String[]{"json"}, false);
            log.info("Indexing {} files...", inputFiles.size());

            for (File file : inputFiles) {

                try {
                    String jsonStr = FileUtils.readFileToString(file);
                    JSONObject jsObj = (JSONObject) parser.parse(jsonStr);

                    String index = endpointConfig.get("index").asTextValue();
                    String type = endpointConfig.get("type").asTextValue();

                    String id = (String) jsObj.get(FILE_IDENTIFIER_PROPERTY);
                    log.debug("Adding {} (type: {}) to {}", id, type, index);
                    IndexResponse response = client.prepareIndex(index, type, id)
                            .setSource(jsObj.toJSONString())
                            .execute()
                            .actionGet();

                    cpt++;

                    if (response.isCreated()) {
                        log.trace("Response: {} | version: {}", response.getId(), response.getVersion());
                    } else {
                        log.warn("NOT created! ResponseResponse: {}", response.getId());
                    }
                } catch (IOException | ParseException e) {
                    log.error("Error with json file ", file, e);
                }
            }

            log.info("Indexed {} of {} files.", cpt, inputFiles.size());
        } catch (RuntimeException e) {
            log.error("Error indexing json files.", e);
        }
    }

    @Override
    protected String getConfigBasename() {
        return "elasticsearch";
    }
}
