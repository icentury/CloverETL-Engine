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
package org.jetel.ctl.extensions;

import org.jetel.util.formatter.DateFormatter;
import org.jetel.util.formatter.DateFormatterFactory;

/**
 * @author jakub (jakub.lehotsky@javlin.eu)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created May 25, 2010
 */
public class TLDateFormatCache extends TLCache {

	private DateFormatter cachedFormatter;

	public TLDateFormatCache(TLFunctionCallContext context, int position) {
		createCachedFormat(context, position);
	}
	
	public void createCachedFormat(TLFunctionCallContext context, int position) {
		if (context.getLiteralsSize() > position && context.isLiteral(position)) {
			cachedFormatter = DateFormatterFactory.getFormatter((String) context.getParamValue(position));
		}
	}

	public DateFormatter getCachedFormatter(TLFunctionCallContext context, String pattern, int position) {
		if (!context.isLiteral(position) && (cachedFormatter == null || !pattern.equals(cachedFormatter.getPattern()))) {
			cachedFormatter = DateFormatterFactory.getFormatter(pattern);
		}

		return cachedFormatter;
	}

}
