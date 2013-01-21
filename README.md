stanbol-gosen
=============

This modules allow to use the [lucene-gosen](https://code.google.com/p/lucene-gosen/) for processing Japanese Texts with Apache Stanbol.

This project contains the following modules:

1. Bundle that provides Lucene Gosen support to the Stanbol Commons Solr Core module
2. LabelTokenizer implementation for the EntityLinking engine
3. Stanbol NLP processing Engine
4. Bundlelist for users to include in their custom Stanbol Launcher configurations.

See the README files of those modules for more information


## Installation

As the Lucene-Gosen modules are not available on maven cent ray (see [Issue 20](http://code.google.com/p/lucene-gosen/issues/detail?id=20)) users will need to download the Gosen artifacts from the Project Homepage and install them manually to their local maven repository.

The Lucene Gosen 2.0.2 with the Naist Chasen dictionary can be downloaded for [here](http://code.google.com/p/lucene-gosen/downloads/detail?name=lucene-gosen-2.0.2-naist-chasen.jar). After that you need to call

    mvn install:install-file -Dfile=lucene-gosen-2.0.2-naist-chasen.jar \
        -DgroupId=com.google.code -DartifactId=lucene-gosen-naist-chasen \
        -Dversion=2.0.2 -Dpackaging=jar

The used `groupId` and `artifactId` is based on the one used by [lucene-gosen-ipadic:1.2.1](http://search.maven.org/#artifactdetails|com.google.code|lucene-gosen-ipadic|1.2.1|jar) that is available on mvm central.

After that you can build this project by calling

    mvn install

## Using the Gosen Analyzers with Stanbol

This section provides information on how to configure a SolrCore to correctly index Japanese Text using Lucene-Gosen.

### Solr Field Configuration

To use the Gosen Analyzers for Japanese you need first to define an `fieldType`. The following configuration is based on the [recommendation](http://lucene-gosen.googlecode.com/svn/trunk/example/schema.xml.snippet) of the Lucene-Gosen project.

    :::xml
    <fieldType name="text_ja" class="solr.TextField" positionIncrementGap="100" autoGeneratePhraseQueries="false" >
        <analyzer type="index">
            <charFilter class="solr.MappingCharFilterFactory" mapping="mapping-japanese.txt"/>
            <tokenizer class="solr.GosenTokenizerFactory" /> <!-- compositePOS="compositePOS.txt" dictionaryDir="dictionary/naist-chasen" -->
            <filter class="solr.GosenWidthFilterFactory"/>
            <filter class="solr.GosenPunctuationFilterFactory" enablePositionIncrements="true"/>
            <filter class="solr.GosenPartOfSpeechStopFilterFactory" tags="stoptags_ja.txt" enablePositionIncrements="true"/>
            <filter class="solr.StopFilterFactory" ignoreCase="true" words="stopwords_ja.txt" enablePositionIncrements="true"/>
            <filter class="solr.KeywordMarkerFilterFactory" ignoreCase="false"/>
            <filter class="solr.GosenBasicFormFilterFactory"/>
            <filter class="solr.GosenKatakanaStemFilterFactory"/>
            <filter class="solr.LowerCaseFilterFactory"/>
        </analyzer>
        <analyzer type="query">
            <charFilter class="solr.MappingCharFilterFactory" mapping="mapping-japanese.txt"/>
            <tokenizer class="solr.GosenTokenizerFactory" /> <!-- compositePOS="compositePOS.txt" dictionaryDir="dictionary/naist-chasen" -->
            <filter class="solr.GosenWidthFilterFactory"/>
            <filter class="solr.GosenPunctuationFilterFactory" enablePositionIncrements="true"/>
            <filter class="solr.GosenPartOfSpeechStopFilterFactory" tags="stoptags_ja.txt" enablePositionIncrements="true"/>
            <filter class="solr.StopFilterFactory" ignoreCase="true" words="stopwords_ja.txt" enablePositionIncrements="true"/>
            <filter class="solr.KeywordMarkerFilterFactory" ignoreCase="false"/>
            <filter class="solr.GosenBasicFormFilterFactory"/>
            <filter class="solr.GosenKatakanaStemFilterFactory"/>
            <filter class="solr.LowerCaseFilterFactory"/>
        </analyzer>
    </fieldType>

In addition you need to use this fiedType for Japanese Texts. In the case of the Stanbol Entityhub this is done by adding the `dynamicField` definition for fields starting with `@ja`

    :::xml
    <!--
        Dynamic field for Japanese languages.
     -->
    <dynamicField name="@ja*"  type="text_ja" indexed="true" stored="true" multiValued="true" omitNorms="false"/>

If you want special field configuration for some properties you will also need to define field configurations for those. The following example shows how to enable termVectors (as used by Solr MLT queries) for the `rdfs:comment` field

    <field name="@ja/rdfs:comment/" type="text_ja" indexed="true" stored="true" multiValued="true" omitNorms="false" termVectors="true"/>

Typically it is recommended to start from the [default.solrindex.zip](https://svn.apache.org/repos/asf/stanbol/trunk/entityhub/yard/solr/src/main/resources/solr/core/default.solrindex.zip) and apply the desired changes.

### Usage with the EntityhubIndexing Tool

1. Extract the [default.solrindex.zip](https://svn.apache.org/repos/asf/stanbol/trunk/entityhub/yard/solr/src/main/resources/solr/core/default.solrindex.zip) to the "indexing/config" directory.

2. Copy the `lucene-gosen-2.0.2-naist-chasen.jar` (downloaded during the installation step) in the lib directory of the Solr Core configuration "indexing/config/paoding/lib". Solr includes all jar files within this directory in the Classpath. Because of that the Gosen analyzers will be available during indexing

3. Rename the "indexing/config/default" directory to the {site-name} (the value of the "name" property of the "indexing/config/indexing.properties" file). As an alternative it is also possible to explicitly define the name of the configuration for the `SolrYardIndexingDestination`.

        :::text
        indexingDestination=org.apache.stanbol.entityhub.indexing.destination.solryard.SolrYardIndexingDestination,solrConf:{config-dir-name},boosts:fieldboosts

After that the Entityhub Indexing Tool will use the custom SolrCore configuration including Gosen for indexing.

### Usage with the Entityhub SolrYard

If you want to create an empty SolrYard instance that uses a SolrCore configuration with Gosen than you need to 

1. create a SolrCore configuration that includes the required `fieldType` and `field` definitions (see start of this section)
2. ZIP the edited SolrCore configuration and name the resulting archive `{name}.solrindex.zip`
3. copy the `{name}.solrindex.zip` to the datafile directory of your Stanbol instance ({working-dir}/stanbol/datafiles)
3. create the SolrYard instance and configure the "Solr Index/Core" (org.apache.stanbol.entityhub.yard.solr.solrUri) to {name}. Make sure the "Use default SolrCore configuration" (org.apache.stanbol.entityhub.yard.solr.useDefaultConfig) is disabled.

If you want to use your SolrCore configuration as default for creating SolrYards you need to use `default` as `{name}` (name the file `default.solrindex.zip`) and keep this file in the datafiles folder. 

See also the documentation on how to [configure a managed site](http://stanbol.apache.org/docs/trunk/components/entityhub/managedsite#configuration-of-managedsites)).

## Japanese Language Support for the Stanbol Enhancer 

The typical [Enhancement Chain](http://stanbol.apache.org/docs/trunk/components/enhancer/chains/) for Japanese Texts will include the following Engines:

* __[Language Detection](http://stanbol.apache.org/docs/trunk/components/enhancer/engines/langdetectengine)__: By default this engine uses the name `langdetect`. Make sure to NOT use the Apache Tika based language detection engine, as it does not support the detection of Japanese.
* __Gosen NLP Engine__: This engine is provided by this project. It is based on the [Stanbol NLP processing module](http://stanbol.apache.org/docs/trunk/components/enhancer/nlp/) and supports Tokenizing, Sentence Detection, Part of Speech (POS) tagging as well as Named Entity Recognition (NER).
* __[Entity Linking Engine](http://stanbol.apache.org/docs/trunk/components/enhancer/engines/entitylinking)__: The Stanbol EntityLinking engine can consume NLP processing results and used them to lookup Entities from a controlled vocabulary. If you have indexed Entities with Japanese Labels in an Entityhub Site, than you will need to create and configure an [Entityhub Linking Engine](http://stanbol.apache.org/docs/trunk/components/enhancer/engines/entityhublinking).
    * For proper processing of Japanese labels of Entities the __Gosen LabelTokenizer__ module (provided by this project) needs to be installed in the Stanbol instance

__Note__: As of now the [Named Entity Linking Engine](http://stanbol.apache.org/docs/trunk/components/enhancer/engines/namedentitytaggingengine) SHOULD NOT be used for Japanese Texts as it does not use Japanese specific processing of the Entity Labels. It is recommended to use the [Entityhub Linking Engine](http://stanbol.apache.org/docs/trunk/components/enhancer/engines/entityhublinking) and filter results based on the types of the Entities.

## Configuring your Stanbol Instance for Japanese

There are several options how this can be done.

1. if you want to create your own Stanbol Launcher, that you need simple to add the `gosen-bundlelist` as dependency to the `pom.xml` file of your launcher configuration.
    
    <dependency>
      <groupId>at.salzburgresearch.stanbol</groupId>
      <artifactId>at.salzburgresearch.stanbol.launchers.bundlelists.languageextras.gosen</artifactId>
      <version>0.10.0-SNAPSHOT</version>
      <type>partialbundlelist</type>
      <scope>provided</scope>
    </dependency>

    Doing so will ensure that the modules referenced by this bundle list will be included in your Stanbol Launcher. For more information on how to build custom launchers please see the [documentation](http://stanbol.apache.org/docs/trunk/production-mode/your-launcher.html) on the Stanbol Webpage

2. manually add the required modules to an Stanbol Instance. For that you need to install the following modules to your Stanbol instance.
    * `at.salzburgresearch.stanbol.commons.solr.extras.gosen:0.11.0-SNAPSHOT`
    * `at.salzburgresearch.stanbol.enhancer.engines.gosennlp:0.10.0-SNAPSHOT`
    * `at.salzburgresearch.stanbol.enhancer.engines.entitylinking.labeltokenizer.gosen`

    This is typically done by either using the Apache Felix Webconsole or by copying the according jar files to the `stanbol/fileinstall` directory of your stanbol instance. If this directory does not yet exist you need to create it. But there are also other possibilities to install the required bundles to the Stanbol OSGI environment. The implementation of the modules are independent of the used start levels.


Please also note the [Stanbol Production Mode](http://stanbol.apache.org/docs/trunk/production-mode/) section for additional information.

## License

This modules are dual licensed under [GNU Lesser General Public License](http://www.gnu.org/licenses/lgpl.html) (as used by the Lucene-Gosen project) and the [Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).