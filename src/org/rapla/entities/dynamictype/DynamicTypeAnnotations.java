/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities.dynamictype;

public interface DynamicTypeAnnotations
{
    String KEY_NAME_FORMAT="nameformat";
    String KEY_NAME_FORMAT_PLANING="nameformat_planing";
    
    String KEY_CLASSIFICATION_TYPE="classification-type";
    String VALUE_CLASSIFICATION_TYPE_RESOURCE="resource";
    String VALUE_CLASSIFICATION_TYPE_RESERVATION="reservation";
    String VALUE_CLASSIFICATION_TYPE_PERSON="person";
    String VALUE_CLASSIFICATION_TYPE_RAPLATYPE="rapla";
    
    String KEY_COLORS="colors";
	String COLORS_AUTOMATED = "rapla:automated";
	String COLORS_COLOR_ATTRIBUTE = "color";
	String COLORS_DISABLED = "rapla:disabled";
	
	String KEY_CONFLICTS="conflicts";
	String VALUE_CONFLICTS_NONE="never";
	String VALUE_CONFLICTS_ALWAYS="always";
	String VALUE_CONFLICTS_WITH_OTHER_TYPES="withOtherTypes";
	
	String KEY_TRANSFERED_TO_CLIENT = "transferedToClient";
	String VALUE_TRANSFERED_TO_CLIENT_NEVER = "never";
}












