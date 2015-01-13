/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.ctl.debug;



public class DebugCommand {

	public enum CommandType {
		GET_AST, 
		GET_CALLSTACK, 
		GET_VAR,
		GET_IN_RECORDS,
		GET_OUT_RECORDS,
		INFO, 
		LIST_BREAKPOINTS, 
		LIST_VARS, 
		REMOVE_BREAKPOINT, 
		RESUME, 
		SET_BREAKPOINT, 
		SET_BREAKPOINTS, 
		SET_VAR,
		STEP_IN, 
		STEP_OUT, 
		STEP_OVER, 
		SUSPEND;
	}
	protected CommandType type;

	protected Object value;

	public DebugCommand(CommandType type) {
		this.type = type;
		value = null;
	}

	public CommandType getType() {
		return type;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}


	@Override
	public String toString(){
		return type.toString();
	}

}