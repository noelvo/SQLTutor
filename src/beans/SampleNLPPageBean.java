package beans;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;

import objects.DatabaseTable;
import objects.QueryResult;
import objects.Question;
import edu.gatech.sqltutor.DatabaseManager;
import edu.gatech.sqltutor.IQueryTranslator;

@ManagedBean
@ViewScoped
public class SampleNLPPageBean implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@ManagedProperty(value="#{userBean}")
	private UserBean userBean;
	
	@ManagedProperty(value="#{databaseManager}")
	private DatabaseManager databaseManager;

	private String selectedDatabase;
	private List<DatabaseTable> tables;
	private String query;
	private String feedbackNLP;
	private QueryResult queryResult;
	private boolean queryMalformed;

	@PostConstruct
	public void init() {
		selectedDatabase = userBean.getSelectedSchema();
		try {
			tables = getDatabaseManager().getTables(selectedDatabase);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		setPrepopulatedQuery();
		queryMalformed = false;
	}
	
	public void processSQL() {
		try {
			queryResult = getDatabaseManager().getQueryResult(selectedDatabase, query, userBean.isDevUser());
			IQueryTranslator question = new Question(query, tables);
			String nlp = question.getTranslation();
			feedbackNLP = "Corresponding question: \n" + nlp;
			queryMalformed = false;
		} catch(SQLException e) {
			feedbackNLP = "Your query was malformed. Please try again.\n" + e.getMessage();
			queryMalformed = true;
		}
	}
	
	public void setPrepopulatedQuery() {
		if(selectedDatabase.equals("company")) {
			query = "SELECT first_name, last_name, salary FROM employee";
		} else if(selectedDatabase.equals("sales")) {
			query = "SELECT CUST_NUM FROM customers";
		} else {
			query = "";
		}
	}
	
	public String getQuery() {
		return query;
	}
	
	public void setQuery(String query) {
		this.query = query;
	}

	public QueryResult getQueryResult() {
		return queryResult;
	}

	public UserBean getUserBean() {
		return userBean;
	}

	public void setUserBean(UserBean userBean) {
		this.userBean = userBean;
	}

	public String getFeedbackNLP() {
		return feedbackNLP;
	}
	
	public String getSelectedDatabase() {
		return selectedDatabase;
	}

	public List<DatabaseTable> getTables() {
		return tables;
	}
	
	public boolean getQueryMalformed() {
		return queryMalformed;
	}

	public DatabaseManager getDatabaseManager() {
		return databaseManager;
	}

	public void setDatabaseManager(DatabaseManager databaseManager) {
		this.databaseManager = databaseManager;
	}
}
