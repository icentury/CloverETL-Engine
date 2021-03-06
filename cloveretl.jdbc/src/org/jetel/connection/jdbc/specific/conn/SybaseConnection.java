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
package org.jetel.connection.jdbc.specific.conn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.jetel.database.sql.DBConnection;
import org.jetel.database.sql.JdbcSpecific.OperationType;
import org.jetel.exception.JetelException;

/**
 * @author Agata Vackova (agata.vackova@javlinconsulting.cz) ; 
 * (c) JavlinConsulting s.r.o.
 *  www.javlinconsulting.cz
 *
 * @since Dec 19, 2008
 *
 */

public class SybaseConnection extends BasicSqlConnection {

	/**
	 * @param dbConnection
	 * @param operationType
	 * @param autoGeneratedKeysType
	 * @throws JetelException
	 */
	public SybaseConnection(DBConnection dbConnection, Connection connection, OperationType operationType) throws JetelException {
		super(dbConnection, connection, operationType);
	}

	/* (non-Javadoc)
	 * @see org.jetel.connection.jdbc.specific.conn.DefaultConnection#prepareStatement(java.lang.String, int[])
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		return super.prepareStatement(sql, columnIndexes);
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.connection.jdbc.specific.conn.DefaultConnection#prepareStatement(java.lang.String, java.lang.String[])
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		return super.prepareStatement(sql, columnNames);
	}
	
	@Override
	protected void optimizeConnection(OperationType operationType) throws Exception {
		switch (operationType) {
		case READ:
			connection.setAutoCommit(false);
			connection.setReadOnly(true);
			connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			break;
		case WRITE:
		case CALL:
			connection.setAutoCommit(false);
			connection.setReadOnly(false);
			connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			break;
		case TRANSACTION:
			connection.setAutoCommit(true);
			connection.setReadOnly(false);
			connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			break;
		}
	}
	
	@Override
	public List<String> getSchemas() throws SQLException {
		return getMetaCatalogs();
	}

	@Override
	public ResultSet getTables(String schema) throws SQLException {
		Statement s = connection.createStatement();
		s.execute("USE " + schema);		
		return s.executeQuery("EXECUTE sp_tables @table_type = \"'TABLE', 'VIEW'\"");
	}

	@Override
	public ResultSetMetaData getColumns(String schema, String owner, String table) throws SQLException {
		Statement s = connection.createStatement();
		s.execute("USE " + schema);		
		return super.getColumns(schema, owner, table);
	}
}
