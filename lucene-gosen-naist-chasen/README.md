Lucene Gosen extension for the Apache Stanbol Commons Solr Module 
=================================================================

This module provides an OSGI bundle that allows to use the [Lucene-Gosen](https://code.google.com/p/lucene-gosen/) Analyzers within Apache Stanbol. 

### Solr Analyzer Factories

This module provides alternate versions of the `Solr**Factory`of Gosen. This in necessary, because the original Gosen project defines them in the `org.apache.solr.analysis` package. A package that is already exported by the Solr Analyzer module. As OSGI does not allow for the same package to be exported by multiple Bundles those implementations are not useable within an OSGI environment. Because of that copies of the original classes are defined (and exported) in the `org.apache.solr.analysis.gosen` package.

This alternate implementations SHOULD only to be used by components that need to run within OSGI. Otherwise it is recommended to use the original classes within the `org.apache.solr.analysis` package.

### Naist Chasen Dictionary

This module embeds `com.google.code:lucene-gosen-naist-chasen:2.0.2` and therefore ships with the Naist Chasen Dictionary. There is also a Lucene Gosen version using the Ipadic dictionary available on the [Lucene Gosen]() Project homepage. Users that want to try out this dictionary can try to change the dependency and embed directive accordingly.

Note that the same dictionary should be used for indexing and runtime.