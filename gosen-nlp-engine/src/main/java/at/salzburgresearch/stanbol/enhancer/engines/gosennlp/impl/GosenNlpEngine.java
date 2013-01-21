package at.salzburgresearch.stanbol.enhancer.engines.gosennlp.impl;

import static at.salzburgresearch.stanbol.enhancer.engines.gosennlp.Constants.GOSEN_NER_TAG_SET;
import static at.salzburgresearch.stanbol.enhancer.engines.gosennlp.Constants.GOSEN_POS_TAG_SET;
import static org.apache.stanbol.enhancer.nlp.NlpAnnotations.POS_ANNOTATION;
import static org.apache.stanbol.enhancer.nlp.utils.NlpEngineHelper.getLanguage;
import static org.apache.stanbol.enhancer.nlp.utils.NlpEngineHelper.initAnalysedText;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.DC_TYPE;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.ENHANCER_END;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.ENHANCER_SELECTED_TEXT;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.ENHANCER_SELECTION_CONTEXT;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.ENHANCER_START;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.clerezza.rdf.core.Language;
import org.apache.clerezza.rdf.core.LiteralFactory;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.apache.lucene.analysis.CharReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.gosen.tokenAttributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.gosen.tokenAttributes.SentenceStartAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.Version;
import org.apache.solr.analysis.MappingCharFilterFactory;
import org.apache.solr.analysis.TokenizerFactory;
import org.apache.solr.analysis.gosen.OsgiGosenTokenizerFactory;
import org.apache.solr.common.ResourceLoader;
import org.apache.stanbol.commons.solr.utils.StanbolResourceLoader;
import org.apache.stanbol.enhancer.nlp.NlpAnnotations;
import org.apache.stanbol.enhancer.nlp.NlpProcessingRole;
import org.apache.stanbol.enhancer.nlp.NlpServiceProperties;
import org.apache.stanbol.enhancer.nlp.model.AnalysedText;
import org.apache.stanbol.enhancer.nlp.model.AnalysedTextFactory;
import org.apache.stanbol.enhancer.nlp.model.Chunk;
import org.apache.stanbol.enhancer.nlp.model.Sentence;
import org.apache.stanbol.enhancer.nlp.model.Token;
import org.apache.stanbol.enhancer.nlp.model.annotation.Value;
import org.apache.stanbol.enhancer.nlp.ner.NerTag;
import org.apache.stanbol.enhancer.nlp.pos.PosTag;
import org.apache.stanbol.enhancer.nlp.utils.NlpEngineHelper;
import org.apache.stanbol.enhancer.servicesapi.Blob;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.EngineException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.ServiceProperties;
import org.apache.stanbol.enhancer.servicesapi.helper.EnhancementEngineHelper;
import org.apache.stanbol.enhancer.servicesapi.impl.AbstractEnhancementEngine;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component(immediate = true, metatype = true, 
policy = ConfigurationPolicy.OPTIONAL) //create a default instance with the default configuration
@Service
@Properties(value={
    @Property(name= EnhancementEngine.PROPERTY_NAME,value="gosen-nlp"),
    @Property(name=Constants.SERVICE_RANKING,intValue=0) //give the default instance a ranking < 0
})
public class GosenNlpEngine extends AbstractEnhancementEngine<RuntimeException,RuntimeException> implements ServiceProperties {

    private Logger log = LoggerFactory.getLogger(GosenNlpEngine.class);

    /*
     * Analyzer configuration constants
     */
    private static final String LUCENE_VERSION = Version.LUCENE_36.toString();
    private static final Map<String,String> CHAR_FILTER_FACTORY_CONFIG = new HashMap<String,String>();
    private static final Map<String,String> TOKENIZER_FACTORY_CONFIG = new HashMap<String,String>();
    static {
        CHAR_FILTER_FACTORY_CONFIG.put("luceneMatchVersion", LUCENE_VERSION);
        CHAR_FILTER_FACTORY_CONFIG.put("mapping", "gosen-mapping-japanese.txt");
        TOKENIZER_FACTORY_CONFIG.put("luceneMatchVersion", LUCENE_VERSION);
    }
    
    /**
     * Service Properties of this Engine
     */
    private static final Map<String,Object> SERVICE_PROPERTIES;
    static {
        Map<String,Object> props = new HashMap<String,Object>();
        props.put(ServiceProperties.ENHANCEMENT_ENGINE_ORDERING, 
            ServiceProperties.ORDERING_NLP_TOKENIZING);
        props.put(NlpServiceProperties.ENHANCEMENT_ENGINE_NLP_ROLE, 
            NlpProcessingRole.Tokenizing);
        SERVICE_PROPERTIES = Collections.unmodifiableMap(props);
    }

    @Reference(cardinality=ReferenceCardinality.OPTIONAL_UNARY)
    protected ResourceLoader parentResourceLoader;

    protected ResourceLoader resourceLoader;

    private MappingCharFilterFactory charFilterFactory;
    private TokenizerFactory tokenizerFactory;
    
    @Reference
    protected AnalysedTextFactory analysedTextFactory;
    
    protected LiteralFactory lf = LiteralFactory.getInstance();
    
    /**
     * holds {@link PosTag}s that are not contained in the 
     * {@link at.salzburgresearch.stanbol.enhancer.engines.gosennlp.Constants#GOSEN_POS_TAG_SET}
     */
    private Map<String,PosTag> adhocTags = new HashMap<String,PosTag>();
  
    @Override
    protected void activate(ComponentContext ctx) throws ConfigurationException {
        super.activate(ctx);
        //init the Solr ResourceLoader used for initialising the components
        resourceLoader = new StanbolResourceLoader(parentResourceLoader);
        charFilterFactory = new MappingCharFilterFactory();
        charFilterFactory.init(CHAR_FILTER_FACTORY_CONFIG);
        charFilterFactory.inform(resourceLoader);
        tokenizerFactory = new OsgiGosenTokenizerFactory();
        tokenizerFactory.init(TOKENIZER_FACTORY_CONFIG);
    }

    @Override
    protected void deactivate(ComponentContext ctx) {
        super.deactivate(ctx);
        resourceLoader = null;
        charFilterFactory = null;
        tokenizerFactory = null;
    }
    
    
    
    @Override
    public int canEnhance(ContentItem ci) throws EngineException {
        // check if content is present
        Map.Entry<UriRef,Blob> entry = NlpEngineHelper.getPlainText(this, ci, false);
        if(entry == null || entry.getValue() == null) {
            return CANNOT_ENHANCE;
        }

        String language = getLanguage(this,ci,false);
        if("ja".equals(language) || (language != null && language.startsWith("ja-"))) {
            log.trace(" > can enhance ContentItem {} with language {}",ci,language);
            return ENHANCE_ASYNC;
        } else {
            return CANNOT_ENHANCE;
        }
        
        
    }

    @Override
    public void computeEnhancements(ContentItem ci) throws EngineException {
        final AnalysedText at = initAnalysedText(this,analysedTextFactory,ci);
        String language = getLanguage(this,ci,false);
        if(!("ja".equals(language) || (language != null && language.startsWith("ja-")))) {
            throw new IllegalStateException("The detected language is NOT 'ja'! "
                + "As this is also checked within the #canEnhance(..) method this "
                + "indicates an Bug in the used EnhancementJobManager implementation. "
                + "Please report this on the dev@apache.stanbol.org or create an "
                + "JIRA issue about this.");
        }
        TokenStream tokenizer = tokenizerFactory.create(charFilterFactory.create(
            CharReader.get(new CharSequenceReader(at.getText()))));
        //Sentence data
        int sentStartOffset = -1;
        //NER data
        List<NerData> nerList = new ArrayList<NerData>();
        int nerSentIndex = 0; //the next index where the NerData.context need to be set
        NerData ner = null;
        OffsetAttribute offset = null;
        try {
            while (tokenizer.incrementToken()){
                offset = tokenizer.addAttribute(OffsetAttribute.class);
                Token token = at.addToken(offset.startOffset(), offset.endOffset());
                SentenceStartAttribute sentStart = tokenizer.addAttribute(SentenceStartAttribute.class);
                if(sentStart.getSentenceStart()){
                    if(sentStartOffset >= 0){
                        Sentence sent = at.addSentence(sentStartOffset, offset.startOffset());
                        //add the sentence as context to the NerData instances
                        while(nerSentIndex < nerList.size()){
                            nerList.get(nerSentIndex).context = sent.getSpan();
                            nerSentIndex++;
                        }
                    }
                    sentStartOffset = offset.startOffset();
                }
                //POS
                PartOfSpeechAttribute pos = tokenizer.addAttribute(PartOfSpeechAttribute.class);
                PosTag posTag = GOSEN_POS_TAG_SET.getTag(pos.getPartOfSpeech());
                if(posTag == null){
                    posTag = adhocTags.get(pos.getPartOfSpeech());
                    if(posTag == null){
                        posTag = new PosTag(pos.getPartOfSpeech());
                        adhocTags.put(pos.getPartOfSpeech(), posTag);
                        log.info(" ... missing PosTag mapping for {}",pos.getPartOfSpeech());
                    }
                }
                token.addAnnotation(POS_ANNOTATION, Value.value(posTag));
                //NER
                NerTag nerTag = GOSEN_NER_TAG_SET.getTag(pos.getPartOfSpeech());
                if(ner != null && (nerTag == null || !ner.tag.getType().equals(nerTag.getType()))){
                    //write NER annotation
                    Chunk chunk = at.addChunk(ner.start, ner.end);
                    chunk.addAnnotation(NlpAnnotations.NER_ANNOTATION, Value.value(ner.tag));
                    //NOTE that the fise:TextAnnotation are written later based on the nerList
                    //clean up
                    ner = null;
                }
                if(nerTag != null){
                    if(ner == null){
                        ner = new NerData(nerTag, offset.startOffset());
                        nerList.add(ner);
                    }
                    ner.end = offset.endOffset();
                }
            }
            //we still need to write the last sentence
            Sentence lastSent = null;
            if(offset != null && sentStartOffset >= 0 && offset.endOffset() > sentStartOffset){
                lastSent = at.addSentence(sentStartOffset, offset.endOffset());
            }
            //and set the context off remaining named entities
            while(nerSentIndex < nerList.size()){
                if(lastSent != null){
                    nerList.get(nerSentIndex).context = lastSent.getSpan();
                } else { //no sentence detected
                    nerList.get(nerSentIndex).context = at.getSpan();
                }
                nerSentIndex++;
            }
        } catch (IOException e) {
            throw new EngineException(this, ci, "Exception while reading from "
                + "AnalyzedText contentpart",e);
        } finally {
            try {
                tokenizer.close();
            } catch (IOException e) {/* ignore */}
        }
        //finally write the NER annotations to the metadata of the ContentItem
        final MGraph metadata = ci.getMetadata();
        ci.getLock().writeLock().lock();
        try {
            Language lang = new Language("ja");
            for(NerData nerData : nerList){
                UriRef ta = EnhancementEngineHelper.createTextEnhancement(ci, this);
                metadata.add(new TripleImpl(ta, ENHANCER_SELECTED_TEXT, new PlainLiteralImpl(
                    at.getSpan().substring(nerData.start, nerData.end),lang)));
                metadata.add(new TripleImpl(ta, DC_TYPE, nerData.tag.getType()));
                metadata.add(new TripleImpl(ta, ENHANCER_START, lf.createTypedLiteral(nerData.start)));
                metadata.add(new TripleImpl(ta, ENHANCER_END, lf.createTypedLiteral(nerData.end)));
                metadata.add(new TripleImpl(ta, ENHANCER_SELECTION_CONTEXT, 
                    new PlainLiteralImpl(nerData.context, lang)));
            }
        } finally{
            ci.getLock().writeLock().unlock();
        }

    }

    @Override
    public Map<String,Object> getServiceProperties() {
        return SERVICE_PROPERTIES;
    }

    /**
     * Used as intermediate representation of NER annotations so that one needs
     * not to obtain a write lock on the {@link ContentItem} for each detected 
     * entity
     * @author Rupert Westenthaler
     *
     */
    private class NerData {
        
        protected final NerTag tag;
        protected final int start;
        protected int end;
        protected String context;
        
        protected NerData(NerTag ner, int start){
            this.tag = ner;
            this.start = start;
        }
        
    }
    
}
