/*******************************************************************************
 * Copyright (c) 2015 EmbSysRegView
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ravenclaw78 - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.embsysregview.parser;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;
import org.eclipse.cdt.embsysregview.Activator;
import org.eclipse.cdt.embsysregview.views.Interpretations;
import org.eclipse.cdt.embsysregview.views.TreeField;
import org.eclipse.cdt.embsysregview.views.TreeGroup;
import org.eclipse.cdt.embsysregview.views.TreeParent;
import org.eclipse.cdt.embsysregview.views.TreeRegister;
import org.eclipse.cdt.embsysregview.views.TreeRegisterGroup;
import org.eclipse.cdt.embsysregview.views.TreeElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.preference.IPreferenceStore;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.osgi.framework.Bundle;

public class RegisterXMLParser {
	private String store_board;

	private Document OpenXML() throws JDOMException, IOException {
		Bundle bundle = Platform.getBundle("org.eclipse.cdt.embsysregview.data");
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		String store_architecture = store.getString("architecture");
		String store_vendor = store.getString("vendor");
		String store_chip = store.getString("chip");
		store_board = store.getString("board");
		URL fileURL = bundle.getEntry("data/" + store_architecture + "/" + store_vendor + "/" + store_chip + ".xml");

		SAXBuilder builder = new SAXBuilder();
		// builder.setValidation(true);
		builder.setValidation(false);

		return builder.build(fileURL);
	}

	public TreeParent LoadXML() throws ParseException, DataConversionException {
		TreeParent tempRoot = new TreeParent("", "");

		Document doc;
		try {
			doc = OpenXML();
		} catch (Exception e) {
			return tempRoot;
		}

		Element root = doc.getRootElement();

		if (root.getName() == "device") {
			return ParseSVD(root, tempRoot);
		} else {
			return ParseXML(root, tempRoot);
		}
	}
	
	 /* Tree Hierarchy (Base Class TreeElement):
	  * TreeParant
	  *   TreeGroup
	  *     TreeRegisterGroup
	  *        TreeRegister
	  *          TreeField
	  */

	/*
	 * Parse an SVD xml file
	 */
	private TreeParent ParseSVD(Element root, TreeParent tempRoot) throws ParseException, DataConversionException {
		
		TreeGroup oldTreeGroup = null;

		Element peripherals = root.getChild("peripherals");
		if (peripherals != null) {
			List<Element> grouplist = peripherals.getChildren("peripheral");

			for (Element group : grouplist) {

				// Mandatory attribute name
				Element attr_rgname = group.getChild("name");
				String rgname;
				if (attr_rgname != null)
					rgname = attr_rgname.getValue();
				else
					throw new ParseException("peripheral requires name", 1);

				// Mandatory attribute baseAddress
				Element element_baseAddress = group.getChild("baseAddress");
				String baseAddress;
				if (element_baseAddress != null)
					baseAddress = element_baseAddress.getValue();
				else
					throw new ParseException("peripheral requires baseAddress", 1);

				// Optional attribute description
				Element attr_gdescription = group.getChild("description");
				String gdescription;
				if (attr_gdescription != null)
					gdescription = attr_gdescription.getValue().replaceAll("( )+", " ");
				else
					gdescription = "";
				
				// Handle derivedFrom
				Attribute attr_derivedFrom = group.getAttribute("derivedFrom");
				String derivedFrom;
				if (attr_derivedFrom != null) {
					derivedFrom = attr_derivedFrom.getValue();

					for (Element linkedgroup : grouplist) {
						// Mandatory attribute name
						Element attr_lname = linkedgroup.getChild("name");
						String lname;
						if (attr_lname != null)
							lname = attr_lname.getValue();
						else
							throw new ParseException("derived peripheral requires name of super peripheral", 1);
						if (lname.equals(derivedFrom)) {
							group = linkedgroup;
							Element attr_lgdescription = linkedgroup.getChild("description");
							if (attr_lgdescription != null && gdescription.isEmpty()) {
								gdescription = attr_lgdescription.getValue().replaceAll("( )+", " ");
								// TODO: add better description cleanup because Vendors often don't provide
								// description Element for derived Register groups
								String expression = ".*[0-9]$";
								if(gdescription.matches(expression)&&
										gdescription.charAt(gdescription.length()-1)==lname.charAt(lname.length()-1)) {
									gdescription = gdescription.substring(0, gdescription.length()-1) + 
											rgname.charAt(rgname.length()-1);
								}
							}
							break;
						}
					}
				}

				// Group similar peripherals in TreeGroup
				TreeGroup obj_group = null;
				// Optional attribute groupName
				Element attr_gname = group.getChild("groupName");
				String gname;
				if (attr_gname != null) {
					gname = attr_gname.getValue();
					for(TreeElement te: tempRoot.getChildren()) {
						if(te.getName().equals(gname) && (te instanceof TreeGroup)) {
							obj_group = (TreeGroup)te;
							break;
						}
					}
					if(obj_group==null) {
						// TODO: add better description cleanup
						String expression = ".*[0-9]$";
						String tmpgdescription;
						if(gdescription.matches(expression)&&
								gdescription.charAt(gdescription.length()-1)==rgname.charAt(rgname.length()-1)) {
							tmpgdescription = gdescription.substring(0, gdescription.length()-1).trim();
						} else
							tmpgdescription = gdescription;
						
						obj_group = new TreeGroup(gname, tmpgdescription);
						tempRoot.addChild(obj_group);
					}
					oldTreeGroup = obj_group;
				} else {
					String expression = "_?[0-9]+$";
					String uniqgroup = rgname.replaceAll(expression, "");
					
					if(oldTreeGroup!=null && oldTreeGroup.getName().equals(uniqgroup))
						obj_group = oldTreeGroup;
					else {
						obj_group = new TreeGroup(uniqgroup, gdescription);
						tempRoot.addChild(obj_group);
						oldTreeGroup = obj_group;
					}
				}

				TreeRegisterGroup obj_registergroup = new TreeRegisterGroup(rgname, gdescription);
				obj_group.addChild(obj_registergroup);
				

				Element registers = group.getChild("registers");
				if (registers != null) {
					List<Element> registerlist = registers.getChildren("register");
					// List<Element> registergrouplist = group.getChildren();

					for (Element register : registerlist) {
						// Mandatory attribute name
						Element attr_rname = register.getChild("name");
						String rname;
						if (attr_rname != null)
							rname = attr_rname.getValue();
						else
							throw new ParseException("register requires name", 1);

						// Optional attribute description
						Element attr_rdescription = register.getChild("description");
						String rdescription;
						if (attr_rdescription != null)
							rdescription = attr_rdescription.getValue();
						else
							rdescription = "";

						// Mandatory attribute address
						Element attr_roffsetaddress = register.getChild("addressOffset");
						long raddress;
						if (attr_roffsetaddress != null)
							if (attr_roffsetaddress.getValue().startsWith("0x"))
								raddress = Long.parseLong(attr_roffsetaddress.getValue().substring(2), 16);
							else
								// handle missing 0x ... grrrr
								raddress = Long.parseLong(attr_roffsetaddress.getValue());
																							
																								
						// throw new ParseException("register address invalid: "
						// + attr_roffsetaddress.getValue(), 1);
						else
							throw new ParseException("register requires address", 1);

						// Optional attribute resetvalue
						Element attr_rresetvalue = register.getChild("resetValue");
						long rresetvalue = 0x00000000;
						if (attr_rresetvalue != null && attr_rresetvalue.getValue() != "")
							try{
							if (attr_rresetvalue.getValue().startsWith("0x"))
								rresetvalue = Long.parseLong(attr_rresetvalue.getValue().substring(2), 16);
							else
								rresetvalue = Long.parseLong(attr_rresetvalue.getValue());
							}catch (Exception e) { 
								// just keep initialized value 0x00000000								
							}

						// Optional attribute access
						Element attr_raccess = register.getChild("access");
						String raccess;
						if (attr_raccess != null)
							raccess = attr_raccess.getValue();
						else
							raccess = "RO";

						// TODO: map access to RO/RW/...
						raccess = raccess.equals("read-write") ? "RW" : raccess;
						raccess = raccess.equals("write-only") ? "WO" : raccess;
						raccess = raccess.equals("read-only") ? "RO" : raccess;
						raccess = raccess.equals("read-write") ? "RW" : raccess;

						// Optional attribute size (in byte)
						Element attr_rsize = register.getChild("size");
						int rsize;
						if (attr_rsize != null)
							if (attr_rsize.getValue().startsWith("0x"))
								rsize = Integer.parseInt(attr_rsize.getValue().substring(2), 16) / 8;
							else
								rsize = Integer.parseInt(attr_rsize.getValue()) / 8;
						else
							rsize = 4;
						

						// TODO: calculate real address ... baseAddress+raddress
						long lbaseAddress = Long.parseLong(baseAddress.substring(2), 16);

						/////////////////////////////////////////////////////////////////////////////////////////////
						// TODO: << EST Handle <dim>, see http://www.keil.com/pack/doc/cmsis/SVD/html/group__dim_element_group__gr.html
						//  <dim>4</dim>
				        //  <dimIncrement>4</dimIncrement>
				        //  <dimIndex>0,1,2,3</dimIndex>
				        //  <name>RELOAD[%s]</name>

						Element attr_rdim = register.getChild("dim");
						int rdim;
						if (attr_rdim != null)
							rdim = Integer.parseInt(attr_rdim.getValue(), 10);
						else
							rdim = 1; // just one element
						
						Element attr_rdimIncrement = register.getChild("dimIncrement");
						int rdimIncrement;
						// TODO: handle numbers with 0x
						if (attr_rdimIncrement != null)
							if (attr_rdimIncrement.getValue().startsWith("0x"))
								rdimIncrement = Integer.parseInt(attr_rdimIncrement.getValue().substring(2), 16);
							else
								rdimIncrement = Integer.parseInt(attr_rdimIncrement.getValue(), 10);
						else
							rdimIncrement = 0;

						Element attr_rdimIndex = register.getChild("dimIndex");
						String rdimIndex;
						if (attr_rdimIndex != null)
							rdimIndex = attr_rdimIndex.getValue();
						else
							rdimIndex = "";
						
						if (rdim>1) {
							System.out.println("DIM Register: " + rname + " " + rdescription + " " + lbaseAddress + " "
									+ raddress + " " + rresetvalue + " " + raccess + " " + rsize + " " + rdim + " " + rdimIncrement + " " + rdimIndex);
						}
						int begIdx=0, commaIdx=0;
						Boolean isCommaList, isRangeList; 
						
						isCommaList = rdimIndex.indexOf(',', 0)!=-1; /* e.g. "0,1,2,3" */
						isRangeList = rdimIndex.indexOf('-', 0)!=-1; /* e.g. "0-3" */ // \todo not handled yet! */
						TreeRegister obj_register;

					for (int i=0; i<rdim; i++) { // << EST
						if (rdim>1) {
							String s, r;
							
							if (isCommaList) {
								if (i<rdim-1) {
									commaIdx = rdimIndex.indexOf(',', begIdx); /* find position of ',' in "1,2,3,4" */
								} else {
									commaIdx = rdimIndex.length(); /* there is no comma any more at the end of the list */
								}
							    if (commaIdx!=-1) { /* found! */
							    	r = rdimIndex.substring(begIdx, commaIdx);
							    	begIdx = commaIdx+1; /* move after comma */
							    } else {
							    	r = "x"; /* \todo dummy value, need to handle e.g. "3-5" */
							    	begIdx = 0;
							    }
							} else if (isRangeList) {
						    	r = "x"; /* \todo dummy value, need to handle e.g. "3-5" */
						    	begIdx = 0; /* reset index */
							} else { /* unkonwn error? */
						    	r = "[err]";
						    	begIdx = 0; /* reset index */
							}
							s = rname.replace("%s", r); // replace %s with new string
							obj_register = new TreeRegister(s, rdescription, lbaseAddress + raddress,
									rresetvalue, raccess, rsize);
							lbaseAddress += rdimIncrement;  // increment register
						} else {
							obj_register = new TreeRegister(rname, rdescription, lbaseAddress + raddress,
								rresetvalue, raccess, rsize);
						}
						/////////////////////////////////////////////////////////////////////////////////////////////
						//old: // TreeRegister obj_register = new TreeRegister(rname, rdescription, lbaseAddress + raddress,
						//		rresetvalue, raccess, rsize);
						/////////////////////////////////////////////////////////////////////////////////////////////

						System.out.println("Register: " + rname + " " + rdescription + " " + lbaseAddress + " "
								+ raddress + " " + rresetvalue + " " + raccess + " " + rsize);
						obj_registergroup.addChild(obj_register);

						Element fields_element = register.getChild("fields");
						if (fields_element != null) {
							List<Element> fieldlist = fields_element.getChildren("field");
							for (Element field : fieldlist) {

								// Mandatory attribute name
								Element attr_fname = field.getChild("name");
								String fname;
								if (attr_fname != null)
									fname = attr_fname.getValue();
								else
									throw new ParseException("field requires name", 1);

								// Optional attribute description
								Element attr_fdescription = field.getChild("description");
								String fdescription;
								if (attr_fdescription != null)
									fdescription = attr_fdescription.getValue();
								else
									fdescription = "";

								// Mandatory attribute bitoffset
								Element attr_fbitoffset = field.getChild("bitOffset");
								byte fbitoffset;
								byte fbitlength;
								if (attr_fbitoffset != null) {
									fbitoffset = Byte.parseByte(attr_fbitoffset.getValue());

									// Mandatory attribute bitlength
									Element attr_fbitlength = field.getChild("bitWidth");

									if (attr_fbitlength != null)
										fbitlength = Byte.parseByte(attr_fbitlength.getValue());
									else
										throw new ParseException("field requires bitlength", 1);
								} else {
									Element attr_fbitrange = field.getChild("bitRange");

									if(attr_fbitrange != null)
									{
										String range_string = attr_fbitrange.getValue();
										String range = range_string.substring(1, range_string.length() - 1);
	
										String[] rangeArray = range.split(":");
										// so 0:0 means [endoffset:startoffset] -> offset=startoffset  length=endoffset-startoffset+1 -> offset 0 ...length 1
										fbitoffset = Byte.valueOf(rangeArray[1]);
										fbitlength = (byte) (Byte.valueOf(rangeArray[0]) - Byte.valueOf(rangeArray[1]) + 1);
									} else {
										Element element_lsb = field.getChild("lsb");
										Element element_msb = field.getChild("msb");
										if(element_lsb != null && element_msb != null)
										{
											fbitoffset = Byte.valueOf(element_lsb.getValue());
											fbitlength = (byte) (Byte.valueOf(element_msb.getValue()) - Byte.valueOf(element_lsb.getValue()));
										} else
											throw new ParseException("field requires some sort of start-end bit marker", 1);
									}
										
								}

								Interpretations interpretations = new Interpretations();

								Element enumeratedValues = field.getChild("enumeratedValues");
								if (enumeratedValues != null) {
									List<Element> interpretationlist = enumeratedValues.getChildren("enumeratedValue");
									// Mandatory attribute value
									for (Element interpretation : interpretationlist) {
										Element attr_key = interpretation.getChild("value");
										long key;
										if (attr_key != null)
											if (attr_key.getValue().startsWith("0x"))
												key = Long.valueOf(attr_key.getValue().substring(2),16);
											else if (attr_key.getValue().startsWith("#")) {
												// Substitue x in #1xx binary patterns
												String str = attr_key.getValue().substring(1);
												str = str.replaceAll("x", "0");
												key = Long.valueOf(str,2);
											}
											else
												key = Long.valueOf(attr_key.getValue());
										else
											throw new ParseException("enumeratedValue requires value", 1);

										// Mandatory attribute name
										Element attr_name = interpretation.getChild("name");
										String name;
										if (attr_name != null)
											name = attr_name.getValue();
										else
											throw new ParseException("enumeratedValue requires name", 1);
										
										// Mandatory/Optional attribute description
										// Optional for SiliconLabortories SVD Files .. they sometimes miss descriptions
										Element attr_text = interpretation.getChild("description");
										String text;
										if (attr_text != null)
											text = attr_text.getValue();
										else
											text = name;
											//throw new ParseException("enumeratedValue requires description", 1);

										

										interpretations.addInterpretation(key, name + ": " + text);
									}
								}

									System.out.println("Field: " + fname + " " + fdescription + " " + fbitoffset + " "
											+ fbitlength + " " + interpretations);

									TreeField obj_field = new TreeField(fname, fdescription, fbitoffset, fbitlength,
											interpretations);
									interpretations.setTreeField(obj_field);
									obj_register.addChild(obj_field);
								}
							}
						}
					}
				}
			}
		}
		return tempRoot;
	}

	/*
	 * Parse an embsysregview xml file
	 */
	private TreeParent ParseXML(Element root, TreeParent tempRoot) throws ParseException, DataConversionException {

		List<Element> grouplist = root.getChildren("group");

		for (Element group : grouplist) {

			// Mandatory attribute name
			Attribute attr_gname = group.getAttribute("name");
			String gname;
			if (attr_gname != null)
				gname = attr_gname.getValue();
			else
				throw new ParseException("group requires name", 1);

			// Optional attribute description
			Attribute attr_gdescription = group.getAttribute("description");
			String gdescription;
			if (attr_gname != null)
				gdescription = attr_gdescription.getValue();
			else
				gdescription = "";

			TreeGroup obj_group = new TreeGroup(gname, gdescription);
			tempRoot.addChild(obj_group);

			List<Element> registergrouplist = group.getChildren();
			for (Element registergroup : registergrouplist) {
				// Mandatory attribute name
				Attribute attr_rgname = registergroup.getAttribute("name");
				String rgname;
				if (attr_rgname != null)
					rgname = attr_rgname.getValue();
				else
					throw new ParseException("registergroup requires name", 1);

				// Optional attribute description
				Attribute attr_rgdescription = registergroup.getAttribute("description");
				String rgdescription;
				if (attr_rgdescription != null)
					rgdescription = attr_rgdescription.getValue();
				else
					rgdescription = "";

				TreeRegisterGroup obj_registergroup = new TreeRegisterGroup(rgname, rgdescription);
				obj_group.addChild(obj_registergroup);

				List<Element> registerlist = registergroup.getChildren();
				for (Element register : registerlist) {
					// Mandatory attribute name
					Attribute attr_rname = register.getAttribute("name");
					String rname;
					if (attr_rgname != null)
						rname = attr_rname.getValue();
					else
						throw new ParseException("register requires name", 1);

					// Optional attribute description
					Attribute attr_rdescription = register.getAttribute("description");
					String rdescription;
					if (attr_rdescription != null)
						rdescription = attr_rdescription.getValue();
					else
						rdescription = "";

					// Mandatory attribute address
					Attribute attr_roffsetaddress = register.getAttribute("address");
					long raddress;
					if (attr_roffsetaddress != null)
						raddress = Long.parseLong(attr_roffsetaddress.getValue().substring(2), 16);
					else
						throw new ParseException("register requires address", 1);

					// Optional attribute resetvalue
					Attribute attr_rresetvalue = register.getAttribute("resetvalue");
					long rresetvalue;
					if (attr_rresetvalue != null && attr_rresetvalue.getValue() != "")
						rresetvalue = Long.parseLong(attr_rresetvalue.getValue().substring(2), 16);
					else
						rresetvalue = 0x00000000;

					// Optional attribute access
					Attribute attr_raccess = register.getAttribute("access");
					String raccess;
					if (attr_raccess != null)
						raccess = attr_raccess.getValue();
					else
						raccess = "RO";

					// Optional attribute size (in byte)
					Attribute attr_rsize = register.getAttribute("size");
					int rsize;
					if (attr_rsize != null)
						rsize = Integer.parseInt(attr_rsize.getValue());
					else
						rsize = 4;

					TreeRegister obj_register = new TreeRegister(rname, rdescription, raddress, rresetvalue, raccess,
							rsize);
					obj_registergroup.addChild(obj_register);

					Element register_description = register.getChild("description");
					if (register_description != null) {
						rdescription = register_description.getText();
						obj_register.setDescription(rdescription);
					}

					List<Element> fieldlist = register.getChildren("field");
					for (Element field : fieldlist) {
						// Optional attribute board_id
						Attribute attr_fboard_id = field.getAttribute("board_id");
						String fboard_id;
						if (attr_fboard_id != null)
							fboard_id = attr_fboard_id.getValue();
						else
							fboard_id = "";

						// only digg deeper if field is going to be
						// added

						if (fboard_id.equals("") || fboard_id.equals(store_board)) {
							// Mandatory attribute name
							Attribute attr_fname = field.getAttribute("name");
							String fname;
							if (attr_fname != null)
								fname = attr_fname.getValue();
							else
								throw new ParseException("field requires name", 1);

							// Optional attribute description
							Attribute attr_fdescription = field.getAttribute("description");
							String fdescription;
							if (attr_fdescription != null)
								fdescription = attr_fdescription.getValue();
							else
								fdescription = "";

							// Mandatory attribute bitoffset
							Attribute attr_fbitoffset = field.getAttribute("bitoffset");
							byte fbitoffset;
							if (attr_fbitoffset != null)
								fbitoffset = Byte.parseByte(attr_fbitoffset.getValue());
							else
								throw new ParseException("field requires bitoffset", 1);

							// Mandatory attribute bitlength
							Attribute attr_fbitlength = field.getAttribute("bitlength");
							byte fbitlength;
							if (attr_fbitlength != null)
								fbitlength = Byte.parseByte(attr_fbitlength.getValue());
							else
								throw new ParseException("field requires bitlength", 1);

							Element field_description = field.getChild("description");
							if (field_description != null)
								fdescription = field_description.getText();

							Interpretations interpretations = new Interpretations();
							List<Element> interpretationlist = field.getChildren("interpretation");
							for (Element interpretation : interpretationlist) {
								// Mandatory attribute key
								Attribute attr_key = interpretation.getAttribute("key");
								String skey;
								long key;
								if (attr_key != null){
									skey = attr_key.getValue();
									if(skey.startsWith("0x"))
										key = Long.parseLong(skey.substring(2),16);
									else
										key = Long.parseLong(skey);
								}
								else
									throw new ParseException("interpretation requires key", 1);

								// Mandatory attribute text
								Attribute attr_text = interpretation.getAttribute("text");
								String text;
								if (attr_text != null)
									text = attr_text.getValue();
								else
									throw new ParseException("interpretation requires text", 1);

								interpretations.addInterpretation(key, text);
							}

							TreeField obj_field = new TreeField(fname, fdescription, fbitoffset, fbitlength,
									interpretations);
							interpretations.setTreeField(obj_field);
							obj_register.addChild(obj_field);
						}
					}
				}
			}
		}

		return tempRoot;
	}

	/*
	 * public boolean isReadWrite() { return
	 * (getType().toUpperCase().equals("RW") ||
	 * getType().toUpperCase().equals("RW1") ||
	 * getType().toUpperCase().equals("RW1C") ||
	 * getType().toUpperCase().equals("RW1S") ||
	 * getType().toUpperCase().equals("RWH")); }
	 * 
	 * public boolean isReadOnly() { return
	 * (getType().toUpperCase().equals("RO") ||
	 * getType().toUpperCase().equals("RC") ||
	 * getType().toUpperCase().equals("R")); }
	 * 
	 * public boolean isWriteOnly() { return
	 * (getType().toUpperCase().equals("WO") ||
	 * getType().toUpperCase().equals("W") ||
	 * getType().toUpperCase().equals("W1C") ||
	 * getType().toUpperCase().equals("W1S") ||
	 * getType().toUpperCase().equals("W1")); }
	 */
}
