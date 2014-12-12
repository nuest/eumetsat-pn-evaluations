# EUMETSAT Elasticsearch and Solr Evaluations

Comparison of search frameworks for the EUMETSAT Product Navigator

## Elasticsearch

Quick guide to launch the prototype for [Elasticsearch](http://www.elasticsearch.org/).

### 1) Run ElasticSearch

Download Elasticsearch at http://www.elasticsearch.org/overview/elkdownloads/.

Run Elasticsearch with the configuration provided in this repository:

```
cd /<path-to-elasticsearch>/bin/
elasticsearch(.bat) -Des.config=/<path-to-local-repo>/apps/elasticsearch-webapp/src/main/resources/elasticsearch.yml
```

Example (to make this work in Windows PowerShell the parameter is wrapped: "-D..."):

```
cd /<path-to-elasticsearch>/bin/
.\elasticsearch.bat "-Des.config=C:\Users\danu\Documents\2014_EUMETSAT\workspace\eumetsat-pn-evaluations\apps\elasticsearch-webapp\src\main\resources\elasticsearch.yml"
```

This will create a simple Elasticsearch store without persistence (working in RAM only).

You must also define a new mapping for the products to avoid index with an analyzer the content of the hierarchyNames (topic categories) to be used to create the facets:

```
etc/new_index_mapping.sh
```

### 2) Index metadata records

* Run ``eumetsat.pn.elasticsearch.ElasticsearchFeeder.main()`` in the module ``api/elasticsearch-api``
  * This class extracts info from XML records with XPath and create a JSON stored in a file for each record. Then all the JSON files are indexed in the Elasticsearch database.
  * The configuration file is ``api/elasticsearch-api/src/main/resources/feederconfig.yml``
  * An example JSON record is described here https://gist.github.com/gaubert/e26eb189f7e42317fbb1
* The feeder is also executed if the endpoint is not fed yet when starting the app (see below).

### 3) Search web app

The web app is based a on a tiny web framework called [Spark](http://www.sparkjava.com) meant for rapid development. Web pages are built using the simple template engine [Freemarker](http://freemarker.org), Boostrap, and JQuery.

Elastic Search is accessed using the REST interface with a REST client.

* Run ``eumetsat.pn.elasticsearch.webapp.ElasticsearchApp.main()`` in the module ``apps/elasticsearch-webapp``
  * The main method contains an instance of the feeder to feed if the endpoint does not deliver a response for a test document
* Point your browser to http://localhost:4567/
* The configuration file is ``apps/elasticsearch-webapp/src/main/resources/app.yml``

#### Features

* Search is based on having different weights for the different parts of the record (Title, Abstract).
* Use Facetted search to filter out results.
* Highlight found keywords in search results.
* Bookmarkable search.

### 4) Elasticsearch request examples

In elastic-experiment/etc there are also multiple curl requests to experiment with Elasticsearch


## Solr

Quick guide to launch the prototype for [Solr](http://lucene.apache.org/solr/).

### 1) Run Solr

Download and unzip Solr from http://lucene.apache.org/solr/mirrors-solr-latest-redir.html. Install and configure it based on the [quickstart tutorial](http://lucene.apache.org/solr/quickstart.html).

```
cd /<path-to-solr>/example/
java -Dsolr.solr.home=<path-to-workspace>/eumetsat-pn-evaluations/apps/solr-webapp/src/main/resources/ -jar start.jar
```

Example (to make this work in Windows PowerShell the parameter is wrapped: "-D..."):

```
cd /<path-to-solr>/example/
java "-Dsolr.solr.home=C:\Users\danu\Documents\2014_EUMETSAT\workspace\eumetsat-pn-evaluations\apps\solr-webapp\src\main\resources\" -jar start.jar
```

Check if Solr is running by querying the configured collection: http://localhost:8983/solr/eumetsat/query?q=weather

### 2) Configure Solr

To help comparability, we try to stick to default values where possible.

* See https://cwiki.apache.org/confluence/display/solr/A+Step+Closer for details of the `solr.home` - directory
* **core.properties** marks the solr.home directory (https://wiki.apache.org/solr/Core%20Discovery%20%284.4%20and%20beyond%29)
* **conf/schema.xml**
  * http://www.solrtutorial.com/schema-xml.html
  * pre-defined field types suffice
* **conf/solrconfig.xml** - parameters for configuring Solr and Solr search components
  * Run in memory (http://stackoverflow.com/questions/7904802/running-solr-in-memory)
* **solr.xml** - definition of the cores to be used by Solr, the directory is provided as parameter `-s` when starting Solr
* **conf/solrcore.properties** - property configuration file for a core, per each core (optional)

### 2) Index metadata records

* Run ``eumetsat.pn.solr.SolrFeeder.main()`` in the module ``api/solr-api`` manually
* The feeder is also executed if the endpoint is not fed yet when starting the app (see below).

### 3) Search web app

* Run ``eumetsat.pn.solr.webapp.SolrApp.main()`` in the module ``apps/solr-webapp``
  * The main method contains an instance of the feeder to feed if the endpoint does not deliver a response for a test document
* Point your browser to http://localhost:5678/
* The configuration file is ``apps/solr-webapp/src/main/resources/app.yml``


#### Features

* ...

### 4) Solr request examples

* Get by id: `http://localhost:8983/solr/eumetsat/get?id=EO:EUM:DAT:INFO:LFDI`
* Search everything, ask for fields `id` and `title`: `http://localhost:8983/solr/eumetsat/query?q=*:*&fl=id,title`

## Run in a servlet container

Build the webapp with the profile `warfile` and copy the created `.war`-file to the servlet container, such as Tomcat.

Also set the name of the web application in the configuration file `app.yml`.

```
cd <project dir>/apps/{elasticsearch, solr}-webapp
mvn clean install -P warfile
```

## Run standalone

An alternative to running from Java or in a servlet container is to create an executable jar, see [Step 11 of this tutorial](https://blog.openshift.com/developing-single-page-web-applications-using-java-8-spark-mongodb-and-angularjs/).

To create a standalone uber-jar for the Elasticsearch webapp:

```
cd <project dir>/apps/{elasticsearch, solr}-webapp
mvn clean install -P standalone
```

To run it open a command line, navigate to the directory of the created jar file and execute ``java -jar target/{elasticsearch, solr}-webapp-<version>.jar``.
