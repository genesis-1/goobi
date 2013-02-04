package de.sub.goobi.persistence.managers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.log4j.Logger;
import org.goobi.beans.DatabaseObject;
import org.goobi.beans.User;

import de.sub.goobi.helper.exceptions.DAOException;

public class UserManager implements IManager {
	private static final Logger logger = Logger.getLogger(UserManager.class);

	public static User getUserById(int id) throws DAOException {
		User o = null;
		try {
			o = UserMysqlHelper.getUserById(id);
		} catch (SQLException e) {
			logger.error("error while getting User with id " + id, e);
			throw new DAOException(e);
		}
		return o;
	}

	public static void saveUser(User o) throws DAOException {
		try {
			UserMysqlHelper.saveUser(o);
		} catch (SQLException e) {
			logger.error("error while saving User with id " + o.getId(), e);
			throw new DAOException(e);
		}
	}

	public static void deleteUser(User o) throws DAOException {
		try {
			UserMysqlHelper.deleteUser(o);
		} catch (SQLException e) {
			logger.error("error while deleting User with id " + o.getId(), e);
			throw new DAOException(e);
		}
	}
	
	public static void hideUser(User o) throws DAOException {
		try {
			UserMysqlHelper.hideUser(o);
		} catch (SQLException e) {
			logger.error("error while deleting User with id " + o.getId(), e);
			throw new DAOException(e);
		}
	}

	public static List<User> getUsers(String order, String filter, Integer start, Integer count) throws DAOException {
		List<User> answer = new ArrayList<User>();
		try {
			answer = UserMysqlHelper.getUsers(order, filter, start, count);
		} catch (SQLException e) {
			logger.error("error while getting Users", e);
			throw new DAOException(e);
		}
		return answer;
	}

	public List<? extends DatabaseObject> getList(String order, String filter, Integer start, Integer count) throws DAOException {
		return (List<? extends DatabaseObject>) getUsers(order, filter, start, count);
	}

	public int getHitSize(String order, String filter) throws DAOException {
		int num = 0;
		try {
			num = UserMysqlHelper.getUserCount(order, filter);
		} catch (SQLException e) {
			logger.error("error while getting User hit size", e);
			throw new DAOException(e);
		}
		return num;
	}

	public static void addFilter(int userId, String filterstring) {
		if (getFilters(userId).contains(filterstring)) {
			return;
		}
		try {
			UserMysqlHelper.addFilterToUser(userId, filterstring);
		} catch (SQLException e) {
			logger.error("Cannot not add filter to user with id " + userId, e);
		}

	}

	public static void removeFilter(int userId, String filterstring) {
		if (!getFilters(userId).contains(filterstring)) {
			return;
		}
		try {
			UserMysqlHelper.removeFilterFromUser(userId, filterstring);
		} catch (SQLException e) {
			logger.error("Cannot not remove filter from user with id " + userId, e);
		}
	}

	public static List<String> getFilters(int userId) {
		List<String> answer = new ArrayList<String>();
		try {
			answer = UserMysqlHelper.getFilterForUser(userId);
		} catch (SQLException e) {
			logger.error("Cannot not load filter for user with id " + userId, e);
		}

		return answer;
	}
	
	/* +++++++++++++++++++++++++++++++++++++++++ Converter +++++++++++++++++++++++++++++++++++++++++++++++ */

	public static User convert(ResultSet rs) throws SQLException {
		User r = new User();
		r.setId(rs.getInt("BenutzerID"));
		r.setVorname(rs.getString("Vorname")); 
		r.setNachname(rs.getString("Nachname")); 
		r.setLogin(rs.getString("login")); 
		r.setPasswort(rs.getString("passwort"));
		r.setIstAktiv(rs.getBoolean("IstAktiv"));
		r.setStandort(rs.getString("Standort"));
		r.setMetadatenSprache(rs.getString("metadatensprache"));
		r.setCss(rs.getString("css"));
		r.setMitMassendownload(rs.getBoolean("mitMassendownload"));
		r.setConfVorgangsdatumAnzeigen(rs.getBoolean("confVorgangsdatumAnzeigen"));
		r.setTabellengroesse(rs.getInt("Tabellengroesse"));
		r.setSessiontimeout(rs.getInt("sessiontimeout"));
		try {
			r.setLdapGruppe(LdapManager.getLdapById(rs.getInt("ldapgruppenID")));
		} catch (DAOException e) {
			throw new SQLException(e);
		}
		r.setIsVisible(rs.getString("isVisible"));
		r.setLdaplogin(rs.getString("ldaplogin"));		
		return r;
	}

	public static ResultSetHandler<User> resultSetToUserHandler = new ResultSetHandler<User>() {
		@Override
		public User handle(ResultSet rs) throws SQLException {
			if (rs.next()) {
				return convert(rs);
			}
			return null;
		}
	};

	public static ResultSetHandler<List<User>> resultSetToUserListHandler = new ResultSetHandler<List<User>>() {
		@Override
		public List<User> handle(ResultSet rs) throws SQLException {
			List<User> answer = new ArrayList<User>();

			while (rs.next()) {
				User o = convert(rs);
				if (o != null) {
					answer.add(o);
				}
			}
			return answer;
		}
	};

}
