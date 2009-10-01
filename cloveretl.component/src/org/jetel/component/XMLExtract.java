package org.jetel.component;

import java.io.File;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.io.SAXContentHandler;
import org.jetel.data.DataField;
import org.jetel.data.DataRecord;
import org.jetel.data.Defaults;
import org.jetel.data.StringDataField;
import org.jetel.data.sequence.Sequence;
import org.jetel.exception.AttributeNotFoundException;
import org.jetel.exception.BadDataFormatException;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.exception.JetelException;
import org.jetel.exception.XMLConfigurationException;
import org.jetel.graph.Node;
import org.jetel.graph.OutputPort;
import org.jetel.graph.Result;
import org.jetel.graph.TransformationGraph;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.sequence.PrimitiveSequence;
import org.jetel.util.AutoFilling;
import org.jetel.util.ReadableChannelIterator;
import org.jetel.util.file.FileUtils;
import org.jetel.util.property.ComponentXMLAttributes;
import org.jetel.util.property.PropertyRefResolver;
import org.jetel.util.string.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * <h3>XMLExtract Component</h3>
 *
 * <!-- Provides the logic to parse a xml file and filter to different ports based on
 * a matching element. The element and all children will be turned into a
 * Data record -->
 *
 * <table border="1">
 *  <th>Component:</th>
 * <tr><td><h4><i>Name:</i></h4></td>
 * <td>XMLExtract</td></tr>
 * <tr><td><h4><i>Category:</i></h4></td>
 * <td></td></tr>
 * <tr><td><h4><i>Description:</i></h4></td>
 * <td>Provides the logic to parse a xml file and filter to different ports based on
 * a matching element. The element and all children will be turned into a
 * Data record.</td></tr>
 * <tr><td><h4><i>Inputs:</i></h4></td>
 * <td>0</td></tr>
 * <tr><td><h4><i>Outputs:</i></h4></td>
 * <td>Output port[0] defined/connected. Depends on mapping definition.</td></tr>
 * <tr><td><h4><i>Comment:</i></h4></td>
 * <td></td></tr>
 * </table>
 *  <br>
 *  <table border="1">
 *  <th>XML attributes:</th>
 *  <tr><td><b>type</b></td><td>"XML_EXTRACT"</td></tr>
 *  <tr><td><b>id</b></td><td>component identification</td>
 *  <tr><td><b>sourceUri</b></td><td>location of source XML data to process</td>
 *  <tr><td><b>useNestedNodes</b></td><td><b>true</b> if nested unmapped XML elements will be used as data source; <b>false</b> if will be ignored</td>
 *  <tr><td><b>mapping</b></td><td>&lt;mapping&gt;</td>
 *  </tr>
 *  </table>
 *
 * Provides the logic to parse a xml file and filter to different ports based on
 * a matching element. The element and all children will be turned into a
 * Data record.<br>
 * Mapping attribute contains mapping hierarchy in XML form. DTD of mapping:<br>
 * <code>
 * &lt;!ELEMENT Mappings (Mapping*)&gt;<br>
 * 
 * &lt;!ELEMENT Mapping (Mapping*)&gt;<br>
 * &lt;!ATTLIST Mapping<br>
 * &nbsp;element NMTOKEN #REQUIRED<br>      
 * &nbsp;&nbsp;//name of binded XML element<br>  
 * &nbsp;outPort NMTOKEN #IMPLIED<br>      
 * &nbsp;&nbsp;//name of output port for this mapped XML element<br>
 * &nbsp;parentKey NMTOKEN #IMPLIED<br>     
 * &nbsp;&nbsp;//field name of parent record, which is copied into field of the current record<br>
 * &nbsp;&nbsp;//passed in generatedKey atrribute<br> 
 * &nbsp;generatedKey NMTOKEN #IMPLIED<br>  
 * &nbsp;&nbsp;//see parentKey comment<br>
 * &nbsp;sequenceField NMTOKEN #IMPLIED<br> 
 * &nbsp;&nbsp;//field name, which will be filled by value from sequence<br>
 * &nbsp;&nbsp;//(can be used to generate new key field for relative records)<br> 
 * &nbsp;sequenceId NMTOKEN #IMPLIED<br>    
 * &nbsp;&nbsp;//we can supply sequence id used to fill a field defined in a sequenceField attribute<br>
 * &nbsp;&nbsp;//(if this attribute is omited, non-persistent PrimitiveSequence will be used)<br>
 * &nbsp;xmlFields NMTOKEN #IMPLIED<br>     
 * &nbsp;&nbsp;//comma separeted xml element names, which will be mapped on appropriate record fields<br>
 * &nbsp;&nbsp;//defined in cloverFields attribute<br>
 * &nbsp;cloverFields NMTOKEN #IMPLIED<br>  
 * &nbsp;&nbsp;//see xmlFields comment<br>
 * &gt;<br>
 * </code>
 * All nested XML elements will be recognized as record fields and mapped by name
 * (except elements serviced by other nested Mapping elements), if you prefere other mapping
 * xml fields and clover fields than 'by name', use xmlFields and cloveFields attributes
 * to setup custom fields mapping. 'useNestedNodes' component attribute defines
 * if also child of nested xml elements will be mapped on the current clover record.
 * Record from nested Mapping element could be connected via key fields with parent record produced
 * by parent Mapping element (see parentKey and generatedKey attribute notes).
 * In case that fields are unsuitable for key composing, extractor could fill
 * one or more fields with values comming from sequence (see sequenceField and sequenceId attribute). 
 * 
 * For example: given an xml file:<br>
 * <code>
 * &lt;myXML&gt; <br>
 * &nbsp;&lt;phrase&gt; <br>
 * &nbsp;&nbsp;&lt;text&gt;hello&lt;/text&gt; <br>
 * &nbsp;&nbsp;&lt;localization&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;chinese&gt;how allo yee dew ying&lt;/chinese&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;german&gt;wie gehts&lt;/german&gt; <br>
 * &nbsp;&nbsp;&lt;/localization&gt; <br>
 * &nbsp;&lt;/phrase&gt; <br>
 * &nbsp;&lt;locations&gt; <br>
 * &nbsp;&nbsp;&lt;location&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;name&gt;Stormwind&lt;/name&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;description&gt;Beautiful European architecture with a scenic canal system.&lt;/description&gt; <br>
 * &nbsp;&nbsp;&lt;/location&gt; <br>
 * &nbsp;&nbsp;&lt;location&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;name&gt;Ironforge&lt;/name&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;description&gt;Economic capital of the region with a high population density.&lt;/description&gt; <br>
 * &nbsp;&nbsp;&lt;/location&gt; <br>
 * &nbsp;&lt;/locations&gt; <br>
 * &nbsp;&lt;someUselessElement&gt;...&lt;/someUselessElement&gt; <br>
 * &nbsp;&lt;someOtherUselessElement/&gt; <br>
 * &nbsp;&lt;phrase&gt; <br>
 * &nbsp;&nbsp;&lt;text&gt;bye&lt;/text&gt; <br>
 * &nbsp;&nbsp;&lt;localization&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;chinese&gt;she yee lai ta&lt;/chinese&gt; <br>
 * &nbsp;&nbsp;&nbsp;&lt;german&gt;aufweidersehen&lt;/german&gt; <br>
 * &nbsp;&nbsp;&lt;/localization&gt; <br>
 * &nbsp;&lt;/phrase&gt; <br>
 * &lt;/myXML&gt; <br>
 * </code> Suppose we want to pull out "phrase" as one datarecord,
 * "localization" as another datarecord, and "location" as the final datarecord
 * and ignore the useless elements. First we define the metadata for the
 * records. Then create the following mapping in the graph: <br>
 * <code>
 * &lt;node id="myId" type="com.lrn.etl.job.component.XMLExtract"&gt; <br>
 * &nbsp;&lt;attr name="mapping"&gt;<br>
 * &nbsp;&nbsp;&lt;Mapping element="phrase" outPort="0" sequenceField="id"&gt;<br>
 * &nbsp;&nbsp;&nbsp;&lt;Mapping element="localization" outPort="1" parentKey="id" generatedKey="parent_id"/&gt;<br>
 * &nbsp;&nbsp;&lt;/Mapping&gt; <br>
 * &nbsp;&nbsp;&lt;Mapping element="location" outPort="2"/&gt;<br>
 * &nbsp;&lt;/attr&gt;<br>
 * &lt;/node&gt;<br>
 * </code> Port 0 will get the DataRecords:<br>
 * 1) id=1, text=hello<br>
 * 2) id=2, text=bye<br>
 * Port 1 will get:<br>
 * 1) parent_id=1, chinese=how allo yee dew ying, german=wie gehts<br>
 * 2) parent_id=2, chinese=she yee lai ta, german=aufwiedersehen<br>
 * Port 2 will get:<br>
 * 1) name=Stormwind, description=Beautiful European architecture with a scenic
 * canal system.<br>
 * 2) name=Ironforge, description=Economic capital of the region with a high
 * population density.<br>
 * <hr>
 * Issue: Enclosing elements having values are not supported.<br>
 * i.e. <br>
 * <code>
 *   &lt;x&gt; <br>
 *     &lt;y&gt;z&lt;/y&gt;<br>
 *     xValue<br>
 *   &lt;/x&gt;<br>
 * </code> there will be no column x with value xValue.<br>
 * Issue: Namespaces are not considered.<br>
 * i.e. <br>
 * <code>
 *   &lt;ns1:x&gt;xValue&lt;/ns1:x&gt;<br>
 *   &lt;ns2:x&gt;xValue2&lt;/ns2:x&gt;<br>
 * </code> will be considered the same x.
 *
 * @author KKou
 */
public class XMLExtract extends Node {

    // Logger
    private static final Log LOG = LogFactory.getLog(XMLExtract.class);

    // xml attributes
    private static final String XML_SOURCEURI_ATTRIBUTE = "sourceUri";
    private static final String XML_SCHEMA_ATTRIBUTE = "schema";
    private static final String XML_USENESTEDNODES_ATTRIBUTE = "useNestedNodes";
    private static final String XML_MAPPING_ATTRIBUTE = "mapping";
    private static final String XML_CHARSET_ATTRIBUTE = "charset";

    // mapping attributes
    private static final String XML_MAPPING = "Mapping";
	private final static String XML_MAPPING_URL_ATTRIBUTE = "mappingURL";
    private static final String XML_ELEMENT = "element";
    private static final String XML_OUTPORT = "outPort";
    private static final String XML_PARENTKEY = "parentKey";
    private static final String XML_GENERATEDKEY = "generatedKey";
    private static final String XML_XMLFIELDS = "xmlFields";
    private static final String XML_CLOVERFIELDS = "cloverFields";
    private static final String XML_SEQUENCEFIELD = "sequenceField";
    private static final String XML_SEQUENCEID = "sequenceId";
    private static final String XML_SKIP_ROWS_ATTRIBUTE = "skipRows";
    private static final String XML_NUMRECORDS_ATTRIBUTE = "numRecords";
//	private static final String XML_SKIP_SOURCE_ROWS_ATTRIBUTE = "skipSourceRows";
//	private static final String XML_NUM_SOURCE_RECORDS_ATTRIBUTE = "numSourceRecords";
	private static final String XML_TRIM_ATTRIBUTE = "trim";
    private static final String XML_VALIDATE_ATTRIBUTE = "validate";
    private static final String XML_XML_FEATURES_ATTRIBUTE = "xmlFeatures";

    private static final String FEATURES_DELIMETER = ";";
    private static final String FEATURES_ASSIGN = ":=";

    // component name
    public final static String COMPONENT_TYPE = "XML_EXTRACT";
    
    // from which input port to read
	private final static int INPUT_PORT = 0;

    // Map of elementName => output port
    private Map<String, Mapping> m_elementPortMap = new HashMap<String, Mapping>();
    
    // Where the XML comes from
    private InputSource m_inputSource;

    // input file
    private String inputFile;
	private ReadableChannelIterator readableChannelIterator;

	// can I use nested nodes for mapping processing?
    private boolean useNestedNodes = true;

    // global skip and numRecords
    private int skipRows=0; // do not skip rows by default
    private int numRecords = -1;

    // autofilling support
    private AutoFilling autoFilling = new AutoFilling();

    //
	private String schemaFile;

	private String xmlFeatures;
	
	private boolean validate;

	private String charset = Defaults.DataParser.DEFAULT_CHARSET_DECODER;

	private boolean trim = true;

	private String mappingURL;

	private String mapping;

	private NodeList mappingNodes;

    /**
     * SAX Handler that will dispatch the elements to the different ports.
     */
    private class SAXHandler extends SAXContentHandler {
        
        // depth of the element, used to determine when we hit the matching
        // close element
        private int m_level = 0;
        
        // flag set if we saw characters, otherwise don't save the column (used
        // to set null values)
        private boolean m_hasCharacters = false;
        //flag to skip text value immediately after end xml tag, for instance
        //<root>
        //	<subtag>text</subtag>
        //	another text
        //</root>
        //"another text" will be ignored
        private boolean m_grabCharacters = true;
        
        // buffer for node value
        private StringBuffer m_characters = new StringBuffer();
        
        // the active mapping
        private Mapping m_activeMapping = null;
        
        /**
         * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
         */
        public void startElement(String prefix, String namespace, String localName, Attributes attributes) throws SAXException {
            m_level++;
            m_grabCharacters = true;
            
            Mapping mapping = null;
            if (m_activeMapping == null) {
                mapping = (Mapping) m_elementPortMap.get(localName);
            } else {
                mapping = (Mapping) m_activeMapping.getChildMapping(localName);
            }
            if (mapping != null) {
                // We have a match, start converting all child nodes into
                // the DataRecord structure
                m_activeMapping = mapping;
                m_activeMapping.setLevel(m_level);
                
                if (mapping.getOutRecord() == null) {
                	// Former comment was reading:
                    	// If it's null that means that there's no edge mapped to
                    	// the output port
                    	// remove this mapping so we don't repeat this logic (and
                    	// logging)
                	// Improved behaviour: (jlehotsky)
                	    // If it's null that means either that there's no edge mapped
                	    // to the output port, or output port is not specified.
                	    // This is OK, we simply ignore the fact and continue.
                	    // Thus the original code is commented out
                    /*LOG.warn("XML Extract: " + getId() + " Element ("
                            + localName
                            + ") does not have an edge mapped to that port.");
                    if(m_activeMapping.getParent() != null) {
                        m_activeMapping.getParent().removeChildMapping(m_activeMapping);
                        m_activeMapping = m_activeMapping.getParent();
                    } else {
                        m_elementPortMap.remove(m_activeMapping);
                        m_activeMapping = null;
                    }*/
                    
                    return;
                }

                //sequence fields initialization
                String sequenceFieldName = m_activeMapping.getSequenceField();
                if(sequenceFieldName != null && m_activeMapping.getOutRecord().hasField(sequenceFieldName)) {
                    Sequence sequence = m_activeMapping.getSequence();
                    DataField sequenceField = m_activeMapping.getOutRecord().getField(sequenceFieldName);
                    if(sequenceField.getType() == DataFieldMetadata.INTEGER_FIELD) {
                        sequenceField.setValue(sequence.nextValueInt());
                    } else if(sequenceField.getType() == DataFieldMetadata.LONG_FIELD
                            || sequenceField.getType() == DataFieldMetadata.DECIMAL_FIELD
                            || sequenceField.getType() == DataFieldMetadata.NUMERIC_FIELD) {
                        sequenceField.setValue(sequence.nextValueLong());
                    } else {
                        sequenceField.fromString(sequence.nextValueString());
                    }
                }
               	m_activeMapping.prepareDoMap();
               	m_activeMapping.incCurrentRecord4Mapping();
                
                // This is the closing element of the matched element that
                // triggered the processing
                // That should be the end of this record so send it off to the
                // next Node
                if (runIt) {
                    try {
                        DataRecord outRecord = m_activeMapping.getOutRecord();
                        String[] generatedKey = m_activeMapping.getGeneratedKey();
                        String[] parentKey = m_activeMapping.getParentKey();
                        if (parentKey != null) {
                            //if generatedKey is a single array, all parent keys are concatenated into generatedKey field
                            //I know it is ugly code...
                            if(generatedKey.length != parentKey.length && generatedKey.length != 1) {
                                LOG
                                        .warn(getId()
                                        + ": XML Extract Mapping's generatedKey and parentKey attribute has different number of field.");
                                m_activeMapping.setGeneratedKey(null);
                                m_activeMapping.setParentKey(null);
                            } else {
                                for(int i = 0; i < parentKey.length; i++) {
                                    boolean existGeneratedKeyField = (outRecord != null) 
                                    			&& (generatedKey.length == 1 ? outRecord.hasField(generatedKey[0]) : outRecord.hasField(generatedKey[i]));
                                    boolean existParentKeyField = m_activeMapping.getParent().getOutRecord() != null 
                                    					&& m_activeMapping.getParent().getOutRecord().hasField(parentKey[i]);
                                    if (!existGeneratedKeyField) {
                                        LOG
                                                .warn(getId()
                                                + ": XML Extract Mapping's generatedKey field was not found. "
                                                + (generatedKey.length == 1 ? generatedKey[0] : generatedKey[i]));
                                        m_activeMapping.setGeneratedKey(null);
                                        m_activeMapping.setParentKey(null);
                                    } else if (!existParentKeyField) {
                                        LOG
                                                .warn(getId()
                                                + ": XML Extract Mapping's parentKey field was not found. "
                                                + parentKey[i]);
                                        m_activeMapping.setGeneratedKey(null);
                                        m_activeMapping.setParentKey(null);
                                    } else {
                                    	// both outRecord and m_activeMapping.getParrent().getOutRecord are not null
                                    	// here, because of if-else if-else chain
                                        DataField generatedKeyField = generatedKey.length == 1 ? outRecord.getField(generatedKey[0]) : outRecord.getField(generatedKey[i]);
                                        DataField parentKeyField = m_activeMapping.getParent().getOutRecord().getField(parentKey[i]);
                                        if(generatedKey.length != parentKey.length) {
                                            if(generatedKeyField.getType() != DataFieldMetadata.STRING_FIELD) {
                                                LOG
                                                        .warn(getId()
                                                        + ": XML Extract Mapping's generatedKey field has to be String type (keys are concatened to this field).");
                                                m_activeMapping.setGeneratedKey(null);
                                                m_activeMapping.setParentKey(null);
                                            } else {
                                                ((StringDataField) generatedKeyField).append(parentKeyField.toString());
                                            }
                                        } else {
                                            generatedKeyField.setValue(parentKeyField.getValue());
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        throw new SAXException(" for output port number '" + m_activeMapping.getOutPort() + "'. Check also parent mapping. ", ex);
                    }
                } else {
                    throw new SAXException("Stop Signaled");
                }
            }
            
            if(m_activeMapping != null //used only if we right now recognize new mapping element or if we want to use nested unmapped nodes as a source of data
                    && (useNestedNodes || mapping != null)) {
                // In a matched element (i.e. we are creating a DataRecord)
                // Store all attributes as columns (this hasn't been
                // used/tested)                
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrName = attributes.getQName(i);
                    
                    //use fields mapping
                    Map<String, String> xmlCloverMap = m_activeMapping.getXml2CloverFieldsMap();
                    if(xmlCloverMap != null && xmlCloverMap.containsKey(attrName)) {
                       attrName = xmlCloverMap.get(attrName);
                    }
                    
                    if (m_activeMapping.getOutRecord() != null && m_activeMapping.getOutRecord().hasField(attrName)) {
                        m_activeMapping.getOutRecord().getField(attrName).fromString(attributes.getValue(i));
                    }
                }
            }
            
            // Regardless of starting element type, reset the length of the buffer and flag
            m_characters.setLength(0);
            m_hasCharacters = false;
        }
        
        /**
         * @see org.xml.sax.ContentHandler#characters(char[], int, int)
         */
        public void characters(char[] data, int offset, int length) throws SAXException {
            // Save the characters into the buffer, endElement will store it
            // into the field
            if (m_activeMapping != null && m_grabCharacters) {
                m_characters.append(data, offset, length);
                m_hasCharacters = true;
            }
        }
        
        /**
         * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
         */
        public void endElement(String prefix, String namespace, String localName) throws SAXException {
            if (m_activeMapping != null) {
                //use fields mapping
                Map<String, String> xml2clover = m_activeMapping.getXml2CloverFieldsMap();
                if(xml2clover != null && xml2clover.containsKey(localName)) {
                    localName = xml2clover.get(localName);
                }
                // Store the characters processed by the characters() call back
                //only if we have corresponding output field and we are on the right level or we want to use data from nested unmapped nodes
                if (m_activeMapping.getOutRecord() != null && m_activeMapping.getOutRecord().hasField(localName) 
                        && (useNestedNodes || m_level - 1 <= m_activeMapping.getLevel())) {
                    DataField field = m_activeMapping.getOutRecord().getField(localName);
                    // If field is nullable and there's no character data set it
                    // to null
                    if (m_hasCharacters) {
	                    try {
	                        field.fromString(trim ? m_characters.toString().trim() : m_characters.toString());
	                    } catch (BadDataFormatException ex) {
	                        // This is a bit hacky here SOOO let me explain...
	                        if (field.getType() == DataFieldMetadata.DATE_FIELD) {
	                            // XML dateTime format is not supported by the
	                            // DateFormat oject that clover uses...
	                            // so timezones are unparsable
	                            // i.e. XML wants -5:00 but DateFormat wants
	                            // -500
	                            // Attempt to munge and retry... (there has to
	                            // be a better way)
	                            try {
	                                // Chop off the ":" in the timezone (it HAS
	                                // to be at the end)
	                                String dateTime = m_characters.substring(0,
	                                        m_characters.lastIndexOf(":"))
	                                        + m_characters
	                                        .substring(m_characters
	                                        .lastIndexOf(":") + 1);
	                                DateFormat format = new SimpleDateFormat(field.getMetadata().getFormatStr());
	                                field.setValue(format.parse(trim ? dateTime.trim() : dateTime));
	                            } catch (Exception ex2) {
	                                // Oh well we tried, throw the originating
	                                // exception
	                                throw ex;
	                            }
	                        } else {
	                            throw ex;
	                        }
	                    }
                    }
                }
                
                // Regardless of whether this was saved, reset the length of the
                // buffer and flag
                m_characters.setLength(0);
                m_hasCharacters = false;
            }
            
            if (m_activeMapping != null && m_level == m_activeMapping.getLevel()) {
                // This is the closing element of the matched element that
                // triggered the processing
                // That should be the end of this record so send it off to the
                // next Node
                if (runIt) {
                    try {
                        OutputPort outPort = getOutputPort(m_activeMapping.getOutPort());
                        
                        if (outPort != null) {
                            // we just ignore creating output, if port is empty (without metadata) or not specified
	                        DataRecord outRecord = m_activeMapping.getOutRecord();
	                        
	                        // skip or process row
	                    	if (skipRows > 0) {
	                    		if (m_activeMapping.getParent() == null) skipRows--;
	                    	} else {
	                            //check for index of last returned record
	                            if(!(numRecords >= 0 && numRecords == autoFilling.getGlobalCounter())) {
	                            	// set autofilling
	                                autoFilling.setAutoFillingFields(outRecord);
	                                
	                                // can I do the map? it depends on skip and numRecords.
	                                if (m_activeMapping.doMap()) {
		                                //send off record
	                                	outPort.writeRecord(outRecord);
	                                }
//	                                if (m_activeMapping.getParent() == null) autoFilling.incGlobalCounter();
	                            }
	                    	}
	                    	
	                    	// resets all child's mappings for skip and numRecords 
                           	m_activeMapping.resetCurrentRecord4ChildMapping();

                        	// reset record
	                        outRecord.reset();
                        }
                       
                        m_activeMapping = m_activeMapping.getParent();
                    } catch (Exception ex) {
                        throw new SAXException(ex);
                    }
                } else {
                    throw new SAXException("Stop Signaled");
                }
            }
            
            //text value immediately after end tag element should not be stored
            m_grabCharacters = false;
            
            //ended an element so decrease our depth
            m_level--; 
        }
    }
    
    /**
     * Mapping holds a single mapping.
     */
    public class Mapping {
        String m_element;								// name of an element for this mapping
        int m_outPort;									// output port number
        DataRecord m_outRecord;							// output record
        String[] m_parentKey;							// parent keys
        String[] m_generatedKey;						// generated keys
        Map<String, Mapping> m_childMap;				// direct children for this mapping 
        WeakReference<Mapping> m_parent;				// direct parent mapping
        int m_level;									// original xml tree level (a deep of this element) 
        String m_sequenceField;							// sequence field
        String m_sequenceId;							// sequence ID
        Sequence sequence;								// sequence (Simple, Db,..)
        
        // mapping - xml name -> clover field name
        Map<String, String> xml2CloverFieldsMap = new HashMap<String, String>();
        
        // for skip and number a record attribute for this mapping
		int skipRecords4Mapping;						// skip records
		int numRecords4Mapping = Integer.MAX_VALUE;		// number records
//		int skipSourceRecords4Mapping;					// skip records
//		int numSourceRecords4Mapping = -1;              // number records
		int currentRecord4Mapping;						// record counter for this mapping
		boolean processSkipOrNumRecords;				// what xml element can be skiped
		boolean bDoMap = true;							// should I skip an xml element? depends on processSkipOrNumRecords
		boolean bReset4CurrentRecord4Mapping;			// should I reset submappings?
        
        /*
         * Minimally required information.
         */
        public Mapping(String element, int outPort) {
            m_element = element;
            m_outPort = outPort;
        }
        
		/**
         * Gives the optional attributes parentKey and generatedKey.
         */
        public Mapping(String element, int outPort, String parentKey[],
                String[] generatedKey) {
            this(element, outPort);
            
            m_parentKey = parentKey;
            m_generatedKey = generatedKey;
        }
        
        /**
         * Gets original xml tree level (a deep of this element)
         * @return
         */
        public int getLevel() {
            return m_level;
        }
        
        /**
         * Sets original xml tree level (a deep of this element)
         * @param level
         */
        public void setLevel(int level) {
            m_level = level;
        }
        
        /**
         * Sets direct children for this mapping. 
         * @return
         */
        public Map<String, Mapping> getChildMap() {
            return m_childMap;
        }
        
        /**
         * Gets direct children for this mapping. 
         * @param element
         * @return
         */
        public Mapping getChildMapping(String element) {
            if (m_childMap == null) {
                return null;
            }
            return m_childMap.get(element);
        }
        
        /**
         * Adds a direct child for this mapping.
         * @param mapping
         */
        public void addChildMapping(Mapping mapping) {
            if (m_childMap == null) {
                m_childMap = new HashMap<String, Mapping>();
            }
            m_childMap.put(mapping.getElement(), mapping);
        }
        
        /**
         * Removes a direct child for this mapping.
         * @param mapping
         */
        public void removeChildMapping(Mapping mapping) {
            if (m_childMap == null) {
                return;
            }
            m_childMap.remove(mapping.getElement());
        }
        
        /**
         * Gets an element name for this mapping.
         * @return
         */
        public String getElement() {
            return m_element;
        }
        
        /**
         * Sets an element name for this mapping.
         * @param element
         */
        public void setElement(String element) {
            m_element = element;
        }
        
        /**
         * Gets generated keys of for this mapping.
         * @return
         */
        public String[] getGeneratedKey() {
            return m_generatedKey;
        }
        
        /**
         * Sets generated keys of for this mapping.
         * @param generatedKey
         */
        public void setGeneratedKey(String[] generatedKey) {
            m_generatedKey = generatedKey;
        }
        
        /**
         * Gets an output port.
         * @return
         */
        public int getOutPort() {
            return m_outPort;
        }
        
        /**
         * Sets an output port.
         * @param outPort
         */
        public void setOutPort(int outPort) {
            m_outPort = outPort;
        }
        
        /**
         * Gets mapping - xml name -> clover field name 
         * @return
         */
        public Map<String, String> getXml2CloverFieldsMap() {
            return xml2CloverFieldsMap;
        }
        
        /**
         * Sets mapping - xml name -> clover field name
         * @param xml2CloverFieldsMap
         */
        public void setXml2CloverFieldsMap(Map<String, String> xml2CloverFieldsMap) {
            this.xml2CloverFieldsMap = xml2CloverFieldsMap;
        }
        
        /**
         * Gets an output record.
         * @return
         */
        public DataRecord getOutRecord() {
            if (m_outRecord == null) {
                OutputPort outPort = getOutputPort(getOutPort());
                if (outPort != null) {
                	DataRecordMetadata dataRecordMetadata = outPort.getMetadata();
                	autoFilling.addAutoFillingFields(dataRecordMetadata);
                    m_outRecord = new DataRecord(dataRecordMetadata);
                    m_outRecord.init();
                    m_outRecord.reset();
                } // Original code is commented, it is valid to have null port now
                /* else {
                    LOG
                            .warn(getId()
                            + ": Port "
                            + getOutPort()
                            + " does not have an edge connected.  Please connect the edge or remove the mapping.");
                }*/ 
            }
            return m_outRecord;
        }
        
        /**
         * Sets an output record.
         * @param outRecord
         */
        public void setOutRecord(DataRecord outRecord) {
            m_outRecord = outRecord;
        }
        
        /**
         * Gets parent key.
         * @return
         */
        public String[] getParentKey() {
            return m_parentKey;
        }
        
        /**
         * Sets parent key.
         * @param parentKey
         */
        public void setParentKey(String[] parentKey) {
            m_parentKey = parentKey;
        }
        
        /**
         * Gets a parent mapping.
         * @return
         */
        public Mapping getParent() {
            if (m_parent != null) {
                return m_parent.get();
            } else {
                return null;
            }
        }
        
        /**
         * Sets a parent mapping.
         * @param parent
         */
        public void setParent(Mapping parent) {
            m_parent = new WeakReference<Mapping>(parent);
        }

        /**
         * Gets a sequence name.
         * @return
         */
        public String getSequenceField() {
            return m_sequenceField;
        }

        /**
         * Sets a sequence name.
         * @param field
         */
        public void setSequenceField(String field) {
            m_sequenceField = field;
        }

        /**
         * Gets a sequence ID.
         * @return
         */
        public String getSequenceId() {
            return m_sequenceId;
        }

        /**
         * Sets a sequence ID.
         * @param id
         */
        public void setSequenceId(String id) {
            m_sequenceId = id;
        }
        
        /**
         * Gets a Sequence (simple sequence, db sequence, ...).
         * @return
         */
        public Sequence getSequence() {
            if(sequence == null) {
                String element = StringUtils.trimXmlNamespace(getElement());

                if(getSequenceId() == null) {
                    sequence = new PrimitiveSequence(element, getGraph(), element);
                } else {
                    sequence = getGraph().getSequence(getSequenceId());

                    if(sequence == null) {
                        LOG.warn(getId() + ": Sequence " + getSequenceId() + " does not exist in "
                                + "transformation graph. Primitive sequence is used instead.");
                        sequence = new PrimitiveSequence(element, getGraph(), element);
                    }
                }
            }

            return sequence;
        }
        
        /**
         * processSkipOrNumRecords is true - mapping can be skipped
         */
		public boolean getProcessSkipOrNumRecords() {
			if (processSkipOrNumRecords) return true;
			Mapping parent = getParent();
			if (parent == null) {
				return processSkipOrNumRecords;
			}
			return parent.getProcessSkipOrNumRecords();
		}
		
		/**
		 * Sets inner variables for processSkipOrNumRecords.
		 */
		public void prepareProcessSkipOrNumRecords() {
			Mapping parentMapping = getParent();
			processSkipOrNumRecords = parentMapping != null && parentMapping.getProcessSkipOrNumRecords() ||
				(skipRecords4Mapping > 0 || numRecords4Mapping < Integer.MAX_VALUE);
		}
		
		/**
		 * Sets inner variables for bReset4CurrentRecord4Mapping.
		 */
		public void prepareReset4CurrentRecord4Mapping() {
			bReset4CurrentRecord4Mapping = processSkipOrNumRecords;
        	if (m_childMap != null) {
        		Mapping mapping;
        		for (Iterator<Entry<String, Mapping>> it=m_childMap.entrySet().iterator(); it.hasNext();) {
        			mapping = it.next().getValue();
        			if (mapping.processSkipOrNumRecords) {
        				bReset4CurrentRecord4Mapping = true;
        				break;
        			}
        		}
        	}
		}
		
		/**
		 * skipRecords for this mapping.
		 * @param skipRecords4Mapping
		 */
        public void setSkipRecords4Mapping(int skipRecords4Mapping) {
        	this.skipRecords4Mapping = skipRecords4Mapping;
        }
        
        /**
         * numRecords for this mapping.
         * @param numRecords4Mapping
         */
        public void setNumRecords4Mapping(int numRecords4Mapping) {
        	this.numRecords4Mapping = numRecords4Mapping;
        }
        
//		/**
//		 * skipRecords for this mapping.
//		 * @param skipRecords4Mapping
//		 */
//        public void setSkipSourceRecords4Mapping(int skipSourceRecords4Mapping) {
//        	this.skipSourceRecords4Mapping = skipSourceRecords4Mapping;
//        }
//        
//        /**
//         * numRecords for this mapping.
//         * @param numRecords4Mapping
//         */
//        public void setNumSourceRecords4Mapping(int numSourceRecords4Mapping) {
//        	this.numSourceRecords4Mapping = numSourceRecords4Mapping;
//        }
//
        /**
         * Counter for this mapping.
         */
        public void incCurrentRecord4Mapping() {
        	currentRecord4Mapping++;
		}
        
        /**
         * Resets submappings.
         */
        public void resetCurrentRecord4ChildMapping() {
        	if (!bReset4CurrentRecord4Mapping) return;
        	if (m_childMap != null) {
        		Mapping mapping;
        		for (Iterator<Entry<String, Mapping>> it=m_childMap.entrySet().iterator(); it.hasNext();) {
        			mapping = it.next().getValue();
        			mapping.currentRecord4Mapping = 0;
        			mapping.resetCurrentRecord4ChildMapping();
        		}
        	}
		}

        /**
         * Sets if this and child mapping should be skipped.
         */
		public void prepareDoMap() {
			if (!processSkipOrNumRecords) return;
			Mapping parent = getParent();
        	bDoMap = (parent == null || parent.doMap()) && 
        		currentRecord4Mapping >= skipRecords4Mapping && currentRecord4Mapping-skipRecords4Mapping < numRecords4Mapping;
        	if (m_childMap != null) {
        		Mapping mapping;
        		for (Iterator<Entry<String, Mapping>> it=m_childMap.entrySet().iterator(); it.hasNext();) {
        			mapping = it.next().getValue();
        			mapping.prepareDoMap();
        		}
        	}
		}
		
		/**
		 * Can process this mapping? It depends on currentRecord4Mapping, skipRecords4Mapping and numRecords4Mapping
		 * for this and parent mappings.
		 * @return
		 */
        public boolean doMap() {
        	return !processSkipOrNumRecords || (processSkipOrNumRecords && bDoMap);
        }
    }
    
    /**
     * Constructs an XML Extract node with the given id.
     */
    public XMLExtract(String id) {
        super(id);
    }
    

    /**
     * Creates an inctence of this class from a xml node.
     * @param graph
     * @param xmlElement
     * @return
     * @throws XMLConfigurationException
     */
    public static Node fromXML(TransformationGraph graph, Element xmlElement) throws XMLConfigurationException {
        ComponentXMLAttributes xattribs = new ComponentXMLAttributes(xmlElement, graph);
        XMLExtract extract;
        
        try {
        	// constructor
            extract = new XMLExtract(xattribs.getString(XML_ID_ATTRIBUTE));
            
            // set input file
            extract.setInputFile(xattribs.getString(XML_SOURCEURI_ATTRIBUTE));
            
            // set dtd schema
            if (xattribs.exists(XML_SCHEMA_ATTRIBUTE)) {
            	extract.setSchemaFile(xattribs.getString(XML_SCHEMA_ATTRIBUTE));
            }
            
            // if can use nested nodes.
            if(xattribs.exists(XML_USENESTEDNODES_ATTRIBUTE)) {
                extract.setUseNestedNodes(xattribs.getBoolean(XML_USENESTEDNODES_ATTRIBUTE));
            }
            
            // set mapping
            String mappingURL = xattribs.getString(XML_MAPPING_URL_ATTRIBUTE, null);
            String mapping = xattribs.getString(XML_MAPPING_ATTRIBUTE, null);
            NodeList nodes = xmlElement.getChildNodes();
            if (mappingURL != null) extract.setMappingURL(mappingURL);
            else if (mapping != null) extract.setMapping(mapping);
            else if (nodes != null && nodes.getLength() > 0){
                //old-fashioned version of mapping definition
                //mapping xml elements are child nodes of the component
            	extract.setNodes(nodes);
            } else {
            	xattribs.getString(XML_MAPPING_URL_ATTRIBUTE); // throw configuration exception
            }

            // set a skip row attribute
            if (xattribs.exists(XML_SKIP_ROWS_ATTRIBUTE)){
            	extract.setSkipRows(xattribs.getInteger(XML_SKIP_ROWS_ATTRIBUTE));
            }
            
            // set a numRecord attribute
            if (xattribs.exists(XML_NUMRECORDS_ATTRIBUTE)){
            	extract.setNumRecords(xattribs.getInteger(XML_NUMRECORDS_ATTRIBUTE));
            }
            
            if (xattribs.exists(XML_XML_FEATURES_ATTRIBUTE)){
            	extract.setXmlFeatures(xattribs.getString(XML_XML_FEATURES_ATTRIBUTE));
            }
            if (xattribs.exists(XML_VALIDATE_ATTRIBUTE)){
            	extract.setValidate(xattribs.getBoolean(XML_VALIDATE_ATTRIBUTE));
            }
            if (xattribs.exists(XML_CHARSET_ATTRIBUTE)){
            	extract.setCharset(xattribs.getString(XML_CHARSET_ATTRIBUTE));
            }
            
			if (xattribs.exists(XML_TRIM_ATTRIBUTE)){
				extract.setTrim(xattribs.getBoolean(XML_TRIM_ATTRIBUTE));
			}
            return extract;
        } catch (Exception ex) {
            throw new XMLConfigurationException(COMPONENT_TYPE + ":" + xattribs.getString(XML_ID_ATTRIBUTE," unknown ID ") + ":" + ex.getMessage(),ex);
        }
    }
    
	@Deprecated
    private void setNodes(NodeList nodes) {
    	this.mappingNodes = nodes;
	}


	public void setMappingURL(String mappingURL) {
    	this.mappingURL = mappingURL;
	}


	public void setMapping(String mapping) {
		this.mapping = mapping;
	}


	/**
     * Sets the trim indicator.
     * @param trim
     */
	public void setTrim(boolean trim) {
		this.trim = trim;
	}

	/**
     * Creates org.w3c.dom.Document object from the given String.
     * 
     * @param inString
     * @return
     * @throws XMLConfigurationException
     */
    private static Document createDocumentFromString(String inString) throws XMLConfigurationException {
        InputSource is = new InputSource(new StringReader(inString));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setCoalescing(true);
        Document doc;
        try {
            doc = dbf.newDocumentBuilder().parse(is);
        } catch (Exception e) {
            throw new XMLConfigurationException("Mapping parameter parse error occur.", e);
        }
        return doc;
    }
    
    /**
     * Creates org.w3c.dom.Document object from the given ReadableByteChannel.
     * 
     * @param readableByteChannel
     * @return
     * @throws XMLConfigurationException
     */
    public static Document createDocumentFromChannel(ReadableByteChannel readableByteChannel) throws XMLConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc;
        try {
            doc = dbf.newDocumentBuilder().parse(Channels.newInputStream(readableByteChannel));
        } catch (Exception e) {
            throw new XMLConfigurationException("Mapping parameter parse error occur.", e);
        }
        return doc;
    }

    
    /**
     * Creates mappings.
     * 
     * @param graph
     * @param extract
     * @param parentMapping
     * @param nodeXML
     */
    private void processMappings(TransformationGraph graph, Mapping parentMapping, org.w3c.dom.Node nodeXML) {
        if (XML_MAPPING.equals(nodeXML.getNodeName())) {
            // for a mapping declaration, process all of the attributes
            // element, outPort, parentKeyName, generatedKey
            ComponentXMLAttributes attributes = new ComponentXMLAttributes((Element)nodeXML, graph);
            Mapping mapping = null;
            
            try {
            	int outputPort = -1;
            	if (attributes.exists(XML_OUTPORT)) 
            		outputPort = attributes.getInteger(XML_OUTPORT); 
                mapping = new Mapping(attributes.getString(XML_ELEMENT), outputPort);
            } catch(AttributeNotFoundException ex) {
                LOG
                        .warn(getId()
                        + ": XML Extract : Mapping missing a required attribute - element."
                        + "  Skipping this mapping and all children.");
                return;
            }
            
            // Add this mapping to the parent
            if (parentMapping != null) {
                parentMapping.addChildMapping(mapping);
                mapping.setParent(parentMapping);
            } else {
                addMapping(mapping);
            }

            boolean parentKeyPresent = false;
            boolean generatedKeyPresent = false;
            if (attributes.exists(XML_PARENTKEY)) {
                mapping.setParentKey(attributes.getString(XML_PARENTKEY, null).split(Defaults.Component.KEY_FIELDS_DELIMITER_REGEX));
                parentKeyPresent = true;
            }
            
            if (attributes.exists(XML_GENERATEDKEY)) {
                mapping.setGeneratedKey(attributes.getString(XML_GENERATEDKEY, null).split(Defaults.Component.KEY_FIELDS_DELIMITER_REGEX));
                generatedKeyPresent = true;
            }
            
            if (parentKeyPresent != generatedKeyPresent) {
                LOG.warn(getId() + ": XML Extract Mapping for element: " + 
                		mapping.getElement() + " must either have both parentKey and generatedKey attributes or neither.");
                mapping.setParentKey(null);
                mapping.setGeneratedKey(null);
            }

            if (parentKeyPresent && mapping.getParent() == null) {
                LOG.warn(getId() + ": XML Extact Mapping for element: "
                        + mapping.getElement() + " may only have parentKey or generatedKey attributes if it is a nested mapping.");
                mapping.setParentKey(null);
                mapping.setGeneratedKey(null);
            }

            //mapping between xml fields and clover fields initialization
            if (attributes.exists(XML_XMLFIELDS) && attributes.exists(XML_CLOVERFIELDS)) {
                String[] xmlFields = attributes.getString(XML_XMLFIELDS, null).split(Defaults.Component.KEY_FIELDS_DELIMITER);
                String[] cloverFields = attributes.getString(XML_CLOVERFIELDS, null).split(Defaults.Component.KEY_FIELDS_DELIMITER_REGEX);

                if(xmlFields.length == cloverFields.length){
                    Map<String, String> xmlCloverMap = new HashMap<String, String>();
                    for (int i = 0; i < xmlFields.length; i++) {
                        xmlCloverMap.put(xmlFields[i], cloverFields[i]);
                    }
                    mapping.setXml2CloverFieldsMap(xmlCloverMap);
                } else {
                    LOG
                    .warn(getId()
                    + ": XML Extact Mapping for element: "
                    + mapping.getElement()
                    + " must have same number of the xml fields and the clover fields attribute.");
                }
            }
            
            //sequence field
            if (attributes.exists(XML_SEQUENCEFIELD)) {
                mapping.setSequenceField(attributes.getString(XML_SEQUENCEFIELD, null));
                mapping.setSequenceId(attributes.getString(XML_SEQUENCEID, null));
            }
            
            //skip rows field
            if (attributes.exists(XML_SKIP_ROWS_ATTRIBUTE)) {
                mapping.setSkipRecords4Mapping(attributes.getInteger(XML_SKIP_ROWS_ATTRIBUTE, 0));
            }
            
            //number records field
            if (attributes.exists(XML_NUMRECORDS_ATTRIBUTE)) {
                mapping.setNumRecords4Mapping(attributes.getInteger(XML_NUMRECORDS_ATTRIBUTE, Integer.MAX_VALUE));
            }
            
//            //skip source rows field
//            if (attributes.exists(XML_SKIP_SOURCE_ROWS_ATTRIBUTE)) {
//                mapping.setSkipSourceRecords4Mapping(attributes.getInteger(XML_SKIP_SOURCE_ROWS_ATTRIBUTE, 0));
//            }
//            
//            //number source records field
//            if (attributes.exists(XML_NUM_SOURCE_RECORDS_ATTRIBUTE)) {
//                mapping.setNumSourceRecords4Mapping(attributes.getInteger(XML_NUM_SOURCE_RECORDS_ATTRIBUTE, Integer.MAX_VALUE));
//            }
//
            // prepare variables for skip and numRecords for this mapping
        	mapping.prepareProcessSkipOrNumRecords();

            // Process all nested mappings
            NodeList nodes = nodeXML.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Node node = nodes.item(i);
                processMappings(graph, mapping, node);
            }
            
            // prepare variable reset of skip and numRecords' attributes
            mapping.prepareReset4CurrentRecord4Mapping();
            
        } else if (nodeXML.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
            // Ignore text values inside nodes
        } else {
            LOG.warn(getId() + ": Unknown element: "
                    + nodeXML.getLocalName()
                    + " ignoring it and all child elements.");
        }
    }
    
    @Override
    public Result execute() throws Exception {
    	Result result;
    	
    	// parse xml from input file(s).
    	if (parseXML()) {
    		// finished successfully
    		result = runIt ? Result.FINISHED_OK : Result.ABORTED;
    		
    	} else {
    		// an error occurred 
    		result = runIt ? Result.ERROR : Result.ABORTED;
    	}

    	broadcastEOF();
		return result;
    }
    
     /**
     * Parses the inputSource. The SAXHandler defined in this class will handle
     * the rest of the events. Returns false if there was an exception
     * encountered during processing.
     */
    private boolean parseXML() throws JetelException{
    	// create new sax factory
        SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(validate);
		initXmlFeatures(factory);
        SAXParser parser;
        
        try {
        	// create new sax parser
            parser = factory.newSAXParser();
        } catch (Exception ex) {
        	throw new JetelException(ex.getMessage(), ex);
        }
        
        try {
        	// prepare next source
            if (readableChannelIterator.isGraphDependentSource()) {
                try {
                    if(!nextSource()) return true;
                } catch (JetelException e) {
                    throw new ComponentNotReadyException(e.getMessage()/*"FileURL attribute (" + inputFile + ") doesn't contain valid file url."*/, e);
                }
            }
    		do {
    			// parse the input source
                parser.parse(m_inputSource, new SAXHandler());
                
                // get a next source
    		} while (nextSource());
    		
        } catch (SAXException ex) {
        	// process error
            if (!runIt) {
                return true; // we were stopped by a stop signal... probably
            }
            LOG.error("XML Extract: " + getId() + " Parse Exception" + ex.getMessage(), ex);
            throw new JetelException("XML Extract: " + getId() + " Parse Exception", ex);
        } catch (Exception ex) {
            LOG.error("XML Extract: " + getId() + " Unexpected Exception", ex);
            throw new JetelException("XML Extract: " + getId() + " Unexpected Exception", ex);
        }
        return true;
    }
    
	/**
	 * Xml features initialization.
	 * @throws JetelException 
	 */
	private void initXmlFeatures(SAXParserFactory factory) throws JetelException {
		if (xmlFeatures == null) return;
		String[] aXmlFeatures = xmlFeatures.split(FEATURES_DELIMETER);
		String[] aOneFeature;
	    try {
			for (String oneFeature: aXmlFeatures) {
				aOneFeature = oneFeature.split(FEATURES_ASSIGN);
				if (aOneFeature.length != 2) 
					throw new JetelException("The xml feature '" + oneFeature + "' has wrong format");
					factory.setFeature(aOneFeature[0], Boolean.parseBoolean(aOneFeature[1]));
			}
		} catch (Exception e) {
			throw new JetelException(e.getMessage(), e);
		}
	}

    /**
     * Perform sanity checks.
     */
    public void init() throws ComponentNotReadyException {
        if(isInitialized()) return;
		super.init();

    	TransformationGraph graph = getGraph();
    	URL projectURL = graph != null ? graph.getProjectURL() : null;

		// prepare mapping
		if (mappingURL != null) {
			try {
				ReadableByteChannel ch = FileUtils.getReadableChannel(projectURL, mappingURL);
				Document doc = createDocumentFromChannel(ch);
                Element rootElement = doc.getDocumentElement();
                mappingNodes = rootElement.getChildNodes();
			} catch (Exception e) {
				throw new ComponentNotReadyException(e);
			}
		} else if (mapping != null) {
			Document doc;
			try {
				doc = createDocumentFromString(mapping);
			} catch (XMLConfigurationException e) {
				throw new ComponentNotReadyException(e);
			}
			Element rootElement = doc.getDocumentElement();
			mappingNodes = rootElement.getChildNodes();
		}
        //iterate over 'Mapping' elements
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            org.w3c.dom.Node node = mappingNodes.item(i);
            processMappings(graph, null, node);
        }
		
        // test that we have at least one input port and one output
        if (outPorts.size() < 1) {
            throw new ComponentNotReadyException(getId()
            + ": At least one output port has to be defined!");
        }
        
        if (m_elementPortMap.size() < 1) {
            throw new ComponentNotReadyException(
                    getId()
                    + ": At least one mapping has to be defined.  <Mapping element=\"elementToMatch\" outPort=\"123\" [parentKey=\"key in parent\" generatedKey=\"new foreign key in target\"]/>");
        }
        
        // sets input file to readableChannelIterator and sets its settings (directory, charset, input port,...)
        if (inputFile != null) {
            this.readableChannelIterator = new ReadableChannelIterator(
            		getInputPort(INPUT_PORT), 
            		projectURL,
            		inputFile);
            this.readableChannelIterator.setCharset(charset);
            this.readableChannelIterator.setPropertyRefResolver(new PropertyRefResolver(graph.getGraphProperties()));
            this.readableChannelIterator.setDictionary(graph.getDictionary());
            this.readableChannelIterator.init();
            if (!readableChannelIterator.isGraphDependentSource()) prepareNextSource();
        }
    }
    
	@Override
	public synchronized void reset() throws ComponentNotReadyException {
		super.reset();
		autoFilling.reset();
        this.readableChannelIterator.reset();
        if (!readableChannelIterator.isGraphDependentSource()) prepareNextSource();
	}
	
	/**
	 * Prepares a next source.
	 * @throws ComponentNotReadyException
	 */
	private void prepareNextSource() throws ComponentNotReadyException {
        try {
            if(!nextSource()) {
                throw new ComponentNotReadyException("FileURL attribute (" + inputFile + ") doesn't contain valid file url.");
            }
        } catch (JetelException e) {
            throw new ComponentNotReadyException(e.getMessage()/*"FileURL attribute (" + inputFile + ") doesn't contain valid file url."*/, e);
        }
	}

	/**
     * Switch to the next source file.
	 * @return
	 * @throws JetelException 
	 */
	private boolean nextSource() throws JetelException {
		ReadableByteChannel stream = null; 
		while (readableChannelIterator.hasNext()) {
			autoFilling.resetSourceCounter();
			autoFilling.resetGlobalSourceCounter();
			stream = readableChannelIterator.next();
			if (stream == null) continue; // if record no record found
			autoFilling.setFilename(readableChannelIterator.getCurrentFileName());
			File tmpFile = new File(autoFilling.getFilename());
			long timestamp = tmpFile.lastModified();
			autoFilling.setFileSize(tmpFile.length());
			autoFilling.setFileTimestamp(timestamp == 0 ? null : new Date(timestamp));				
			m_inputSource = new InputSource(Channels.newInputStream(stream));
			return true;
		}
        readableChannelIterator.blankRead();
		return false;
	}

    public String getType() {
        return COMPONENT_TYPE;
    }
    
    @Override
    public ConfigurationStatus checkConfig(ConfigurationStatus status) {
        //TODO
        return status;
    }
    
    public org.w3c.dom.Node toXML() {
        return null;
    }
    
    /**
     * Set the input source containing the XML this will parse.
     */
    public void setInputSource(InputSource inputSource) {
        m_inputSource = inputSource;
    }
    
    /**
     * Sets an input file.
     * @param inputFile
     */
    public void setInputFile(String inputFile) {
    	this.inputFile = inputFile;
    }
    
    /**
     * Sets a dtd schema.
     * @param schemaFile
     */
    public void setSchemaFile(String schemaFile) {
    	this.schemaFile = schemaFile;
    }

    /**
     * 
     * @param useNestedNodes
     */
    public void setUseNestedNodes(boolean useNestedNodes) {
        this.useNestedNodes = useNestedNodes;
    }
    
    /**
     * Accessor to add a mapping programatically.
     */
    public void addMapping(Mapping mapping) {
        m_elementPortMap.put(mapping.getElement(), mapping);
    }
    
    /**
     * Returns the mapping. Maybe make this read-only?
     */
    public Map getMappings() {
        // return Collections.unmodifiableMap(m_elementPortMap); // return a
        // read-only map
        return m_elementPortMap;
    }

    /**
     * Sets skipRows - how many elements to skip.
     * @param skipRows
     */
    public void setSkipRows(int skipRows) {
        this.skipRows = skipRows;
    }
    
    /**
     * Sets numRecords - how many elements to process.
     * @param numRecords
     */
    public void setNumRecords(int numRecords) {
        this.numRecords = Math.max(numRecords, 0);
    }

    /**
     * Sets the xml feature.
     * @param xmlFeatures
     */
    public void setXmlFeatures(String xmlFeatures) {
    	this.xmlFeatures = xmlFeatures;
	}

    /**
     * Sets validation option.
     * @param validate
     */
    public void setValidate(boolean validate) {
    	this.validate = validate;
	}
    
    /**
     * Sets charset for dictionary and input port reading.
     * @param string
     */
    public void setCharset(String charset) {
    	this.charset = charset;
	}

//    private void resetRecord(DataRecord record) {
//        // reset the record setting the nullable fields to null and default
//        // values. Unfortunately init() does not do this, so if you have a field
//        // that's nullable and you never set a value to it, it will NOT be null.
//        
//        // the reason we need to reset data records is the fact that XML data is
//        // not as rigidly
//        // structured as csv fields, so column values are regularly "missing"
//        // and without a reset
//        // the prior row's value will be present.
//        for (int i = 0; i < record.getNumFields(); i++) {
//            DataFieldMetadata fieldMetadata = record.getMetadata().getField(i);
//            DataField field = record.getField(i);
//            if (fieldMetadata.isNullable()) {
//                // Default all nullables to null
//                field.setNull(true);
//            } else if(fieldMetadata.isDefaultValue()) {
//                //Default all default values to their given defaults
//                field.setToDefaultValue();
//            } else {
//                // Not nullable so set it to the default value (what init does)
//                switch (fieldMetadata.getType()) {
//                    case DataFieldMetadata.INTEGER_FIELD:
//                        ((IntegerDataField) field).setValue(0);
//                        break;
//                        
//                    case DataFieldMetadata.STRING_FIELD:
//                        ((StringDataField) field).setValue("");
//                        break;
//                        
//                    case DataFieldMetadata.DATE_FIELD:
//                    case DataFieldMetadata.DATETIME_FIELD:
//                        ((DateDataField) field).setValue(0);
//                        break;
//                        
//                    case DataFieldMetadata.NUMERIC_FIELD:
//                        ((NumericDataField) field).setValue(0);
//                        break;
//                        
//                    case DataFieldMetadata.LONG_FIELD:
//                        ((LongDataField) field).setValue(0);
//                        break;
//                        
//                    case DataFieldMetadata.DECIMAL_FIELD:
//                        ((NumericDataField) field).setValue(0);
//                        break;
//                        
//                    case DataFieldMetadata.BYTE_FIELD:
//                        ((ByteDataField) field).setValue((byte) 0);
//                        break;
//                        
//                    case DataFieldMetadata.UNKNOWN_FIELD:
//                    default:
//                        break;
//                }
//            }
//        }
//    }
}
