package pnuts.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/*
 * A workaround for http://developer.java.sun.com/developer/bugParade/bugs/4507707.html
 */
public class DriverManager {

	public static Connection getConnection(String url,
										   String user,
										   String password)
		throws SQLException
	{
			return java.sql.DriverManager.getConnection(url, user, password);
	}

	public static Connection getConnection(String url, Properties props)
		throws SQLException
	{
			return java.sql.DriverManager.getConnection(url, props);
	}

	public static Connection getConnection(String url)
		throws SQLException
	{
			return java.sql.DriverManager.getConnection(url);
	}
}
