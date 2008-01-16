/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
package org.jetel.component;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jetel.data.DataRecord;
import org.jetel.data.Defaults;
import org.jetel.data.InternalSortDataRecord;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.ConfigurationProblem;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.exception.JetelException;
import org.jetel.exception.XMLConfigurationException;
import org.jetel.graph.InputPort;
import org.jetel.graph.Node;
import org.jetel.graph.Result;
import org.jetel.graph.TransformationGraph;
import org.jetel.util.property.ComponentXMLAttributes;
import org.jetel.util.string.StringUtils;
import org.w3c.dom.Element;
/**
 *  <h3>Sort Component</h3>
 *
 * <!-- Sorts the incoming records based on specified key -->
 *
 * <table border="1">
 *  <th>Component:</th>
 * <tr><td><h4><i>Name:</i></h4></td>
 * <td>Sort</td></tr>
 * <tr><td><h4><i>Category:</i></h4></td>
 * <td></td></tr>
 * <tr><td><h4><i>Description:</i></h4></td>
 * <td>Sorts the incoming records based on specified key.<br>
 *  The key is name (or combination of names) of field(s) from input record.
 *  The sort order is either Ascending (default) or Descending.</td></tr>
 * <tr><td><h4><i>Inputs:</i></h4></td>
 * <td>[0]- input records</td></tr>
 * <tr><td><h4><i>Outputs:</i></h4></td>
 * <td>At least one connected output port.</td></tr>
 * <tr><td><h4><i>Comment:</i></h4></td>
 * <td></td></tr>
 * </table>
 *  <br>
 *  <table border="1">
 *  <th>XML attributes:</th>
 *  <tr><td><b>type</b></td><td>"SORT"</td></tr>
 *  <tr><td><b>id</b></td><td>component identification</td>
 *  <tr><td><b>sortKey</b></td><td>field names separated by :;|  {colon, semicolon, pipe}</td>
 *  <tr><td><b>sortOrder</b><br><i>optional</i></td><td>one of "Ascending|Descending" {the fist letter is sufficient, if not defined, then Ascending}</td></tr>
 *  <tr><td><b>useI18N</b><br><i>optional</i></td><td>true/false perform sorting according to national rules - e.g. Czech or German handling of characters like "i","í". Default
 *  is false.<br>Use it only if you are sorting data according to key which can contain accented characters or
 *  you want sorter to follow certain locale specific rules.</td></tr>
 *  <tr><td><b>locale</b><br><i>optional</i></td><td>locale to be used when sorting using I18N rules. If not specified, then system
 *  default is used.<br><i>Example: "fr"</i></td></tr>
 *  </table>
 *
 *  <h4>Example:</h4>
 *  <pre>&lt;Node id="SORT_CUSTOMER" type="SORT" sortKey="Name:Address" sortOrder="A"/&gt;</pre>
 *  
 *  <pre>&lt;Node id="SORT_CUSTOMER" type="SORT" sortKey="Name:Address" sortOrder="A" useI18N="true" locale="fr"/&gt;</pre>
 *
 * @author      David Pavlis
 * @since       April 4, 2002
 * @revision    $Revision$
 */
public class Sort extends Node {

	private static final String XML_SORTORDER_ATTRIBUTE = "sortOrder";
	private static final String XML_SORTKEY_ATTRIBUTE = "sortKey";
    private static final String XML_USE_I18N_ATTRIBUTE = "useI18N";
    private static final String XML_LOCALE_ATTRIBUTE = "locale";
    
	/**  Description of the Field */
	public final static String COMPONENT_TYPE = "SORT";

	private final static int WRITE_TO_PORT = 0;
	private final static int READ_FROM_PORT = 0;

	private InternalSortDataRecord newSorter;
	private boolean sortOrderAscending;
	private String[] sortKeys;
	private ByteBuffer recordBuffer;
    private String localeStr;
    private boolean useI18N;

	private final static boolean DEFAULT_ASCENDING_SORT_ORDER = true; 

	/**
	 *Constructor for the Sort object
	 *
	 * @param  id         Description of the Parameter
	 * @param  sortKeys   Description of the Parameter
	 * @param  sortOrder  Description of the Parameter
	 */
	public Sort(String id, String[] sortKeys, boolean sortOrder) {
		super(id);
		this.sortOrderAscending = sortOrder;
		this.sortKeys = sortKeys;
	}


	/**
	 *Constructor for the Sort object
	 *
	 * @param  id        Description of the Parameter
	 * @param  sortKeys  Description of the Parameter
	 */
	public Sort(String id, String[] sortKeys) {
		this(id,sortKeys,DEFAULT_ASCENDING_SORT_ORDER);
	}

	@Override
	public Result execute() throws Exception {
		InputPort inPort = getInputPort(READ_FROM_PORT);
		DataRecord inRecord = new DataRecord(inPort.getMetadata());
		inRecord.init();
		//InputPortDirect inPort = (InputPortDirect) getInputPort(READ_FROM_PORT);
		// --- store input records into internal buffer
		while (inRecord != null && runIt) {
			inRecord = inPort.readRecord(inRecord);// readRecord(READ_FROM_PORT,inRecord);
			if (inRecord != null) {
				if (!newSorter.put(inRecord)) {
					System.err.println("Sorter " + getId() + " has no more capacity to sort additional records."
									+ "The output will be incomplete !");
					break; // no more capacity
				}
			}
		}
		try {
			newSorter.sort();
		} catch (Exception ex) {
			throw new JetelException( "Error when sorting: " + ex.getMessage(),ex);
		}
		while (newSorter.get(recordBuffer) && runIt) {
			writeRecordBroadcastDirect(recordBuffer);
			recordBuffer.clear();
		}
		broadcastEOF();
        return runIt ? Result.FINISHED_OK : Result.ABORTED;
	}

	@Override
	public void free() {
        if(!isInitialized()) return;
		super.free();
		
		newSorter.free();
	}
	/**
	 *  Sets the sortOrderAscending attribute of the Sort object
	 *
	 * @param  ascending  The new sortOrderAscending value
	 */
	public void setSortOrderAscending(boolean ascending) {
		sortOrderAscending = ascending;
	}


	/**
	 *  Description of the Method
	 *
	 * @exception  ComponentNotReadyException  Description of the Exception
	 * @since                                  April 4, 2002
	 */
	public void init() throws ComponentNotReadyException {
        if(isInitialized()) return;
		super.init();
		
		recordBuffer = ByteBuffer.allocateDirect(Defaults.Record.MAX_RECORD_SIZE);
		if (recordBuffer == null) {
			throw new ComponentNotReadyException("Can NOT allocate internal record buffer ! Required size:" +
					Defaults.Record.MAX_RECORD_SIZE);
		}
		// create sorter
		boolean[] sortOrderings = new boolean[sortKeys.length];
		Arrays.fill(sortOrderings, sortOrderAscending);
		newSorter = new InternalSortDataRecord(
		        getInputPort(READ_FROM_PORT).getMetadata(), sortKeys, sortOrderings);
        if (useI18N){
            newSorter.setUseCollator(true);
        }
        if (localeStr!=null){
            newSorter.setCollatorLocale(localeStr);
        }
	}


	/**
	 *  Description of the Method
	 *
	 * @return    Description of the Returned Value
	 * @since     May 21, 2002
	 */
	public void toXML(Element xmlElement) {
		super.toXML(xmlElement);
		if (sortKeys != null) {
			StringBuffer buf = new StringBuffer(sortKeys[0]);
			for (int i=1; i< sortKeys.length; i++) {
				buf.append(Defaults.Component.KEY_FIELDS_DELIMITER + sortKeys[i]); 
			}
			xmlElement.setAttribute(XML_SORTKEY_ATTRIBUTE,buf.toString());
		}
		if (sortOrderAscending == false) {
			xmlElement.setAttribute(XML_SORTORDER_ATTRIBUTE, "Descending");
		}
        
        if (useI18N){
            xmlElement.setAttribute(XML_USE_I18N_ATTRIBUTE, String.valueOf(useI18N));
        }
        
        if (localeStr!=null){
            xmlElement.setAttribute(XML_LOCALE_ATTRIBUTE, localeStr);
        }
	}


	/**
	 *  Description of the Method
	 *
	 * @param  nodeXML  Description of Parameter
	 * @return          Description of the Returned Value
	 * @since           May 21, 2002
	 */
	   public static Node fromXML(TransformationGraph graph, Element xmlElement) throws XMLConfigurationException {
		ComponentXMLAttributes xattribs = new ComponentXMLAttributes(xmlElement, graph);
		Sort sort;
		try {
			sort = new Sort(xattribs.getString(XML_ID_ATTRIBUTE),
					xattribs.getString(XML_SORTKEY_ATTRIBUTE).split(Defaults.Component.KEY_FIELDS_DELIMITER_REGEX));
			if (xattribs.exists(XML_SORTORDER_ATTRIBUTE)) {
				sort.setSortOrderAscending(xattribs.getString(XML_SORTORDER_ATTRIBUTE).matches("^[Aa].*"));
			}
            if (xattribs.exists(XML_USE_I18N_ATTRIBUTE)){
                sort.setUseI18N(xattribs.getBoolean(XML_USE_I18N_ATTRIBUTE));
            }
            if (xattribs.exists(XML_LOCALE_ATTRIBUTE)){
                sort.setLocaleStr(xattribs.getString(XML_LOCALE_ATTRIBUTE));
            }
            
		} catch (Exception ex) {
	           throw new XMLConfigurationException(COMPONENT_TYPE + ":" + xattribs.getString(XML_ID_ATTRIBUTE," unknown ID ") + ":" + ex.getMessage(),ex);
		}
		return sort;
	}


	/**
	 *  Description of the Method
	 *
	 * @return    Description of the Return Value
	 */
        @Override
        public ConfigurationStatus checkConfig(ConfigurationStatus status) {
    		super.checkConfig(status);
   		 
    		if(!checkInputPorts(status, 1, 1)
    				|| !checkOutputPorts(status, 1, Integer.MAX_VALUE)) {
    			return status;
    		}
    		
            checkMetadata(status, getInMetadata(), getOutMetadata());

            try {
                init();
            } catch (ComponentNotReadyException e) {
                ConfigurationProblem problem = new ConfigurationProblem(e.getMessage(), ConfigurationStatus.Severity.ERROR, this, ConfigurationStatus.Priority.NORMAL);
                if(!StringUtils.isEmpty(e.getAttributeName())) {
                    problem.setAttributeName(e.getAttributeName());
                }
                status.add(problem);
            } finally {
            	free();
            }
            
            return status;
       }
	
	public String getType(){
		return COMPONENT_TYPE;
	}


    /**
     * @return the localeStr
     */
    public String getLocaleStr() {
        return localeStr;
    }


    /**
     * @param localeStr the localeStr to set
     */
    public void setLocaleStr(String localeStr) {
        this.localeStr = localeStr;
    }


    /**
     * @return the useI18N
     */
    public boolean isUseI18N() {
        return useI18N;
    }


    /**
     * @param useI18N the useI18N to set
     */
    public void setUseI18N(boolean useI18N) {
        this.useI18N = useI18N;
    }
}

