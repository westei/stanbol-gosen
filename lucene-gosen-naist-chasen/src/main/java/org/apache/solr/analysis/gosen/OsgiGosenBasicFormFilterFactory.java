package org.apache.solr.analysis.gosen;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.gosen.GosenBasicFormFilter;
import org.apache.solr.analysis.BaseTokenFilterFactory;

/**
 * Same as the OsgiGosenBasicFormFilterFactory of 
 * <a href="http://code.google.com/p/lucene-gosen/">Lucene-Gosen</a> but in
 * an other package to allow usage from OSGI (see package level documentation
 * for more information).
 */
public class OsgiGosenBasicFormFilterFactory extends BaseTokenFilterFactory {

  public TokenStream create(TokenStream stream) {
    return new GosenBasicFormFilter(stream);
  }
}
