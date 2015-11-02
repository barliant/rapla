/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.swing.internal.action;
import org.rapla.client.PopupContext;
import org.rapla.client.swing.EditController;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class DynamicTypeAction extends  RaplaObjectAction {
	String classificationType;
    public DynamicTypeAction(RaplaContext sm,PopupContext popupContext, EditController editController)  {
        super(sm, popupContext, editController);
    }

    public DynamicTypeAction setNewClassificationType(String classificationType) {
    	this.classificationType = classificationType;
    	super.setNew( null );
    	return this;
    }

    protected void newEntity() throws RaplaException {
        DynamicType newDynamicType = getModification().newDynamicType(classificationType);
        editController.edit(newDynamicType, getPopupContext());
    }


}
