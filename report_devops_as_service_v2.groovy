import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.mail.Email
import com.atlassian.mail.server.SMTPMailServer
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter
import com.sun.mail.imap.IMAPProvider
import com.sun.mail.smtp.SMTPProvider
import com.atlassian.sal.api.component.ComponentLocator
import com.sun.mail.imap.IMAPProvider
import com.sun.mail.smtp.SMTPProvider
import com.atlassian.mail.server.MailServerManager;
import com.sun.mail.imap.*
import com.sun.mail.smtp.*
import com.sun.mail.pop3.*
import groovy.time.*
import org.apache.commons.lang3.time.DurationFormatUtils as DurationFormatUtils

public ArrayList<Issue> selectTotalIssues(){
    ArrayList<Issue> totalIssueList = new ArrayList()
    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchProvider = ComponentAccessor.getComponent(SearchService.class)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = ComponentAccessor.userManager.getUserByName("jiradmin")
    log.info user?.displayName
    log.info user?.active
    def query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\"")
    log.info query.getQueryString()
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())

    log.info ("total all issues ${results.total}")
    for (Issue reqIssue: results.getResults()){
        totalIssueList.add(reqIssue)
    }
    return totalIssueList
}

public ArrayList<Issue> selectLineResolvedIssues(String lineNumber){
    ArrayList<Issue> issueList = new ArrayList()
    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchProvider = ComponentAccessor.getComponent(SearchService.class)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = ComponentAccessor.userManager.getUserByName("jiradmin")
    log.info user?.displayName
    log.info user?.active
    def query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed FROM \"${lineNumber} линия\" to \"Решен\"")
    log.info query.getQueryString()
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())

    log.info ("total L${lineNumber} issues ${results.total}")
    for (Issue reqIssue: results.getResults()){
        issueList.add(reqIssue)
    }
    return issueList
}

public ArrayList<Issue> selectLineNonResolvedIssues(String lineNumber){
    ArrayList<Issue> issueList = new ArrayList()
    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchProvider = ComponentAccessor.getComponent(SearchService.class)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = ComponentAccessor.userManager.getUserByName("jiradmin")
    log.info user?.displayName
    log.info user?.active
    def query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status = \"${lineNumber} линия\"")
    log.info query.getQueryString()
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())

    log.info ("total not resolved issues ${results.total}")
    for (Issue reqIssue: results.getResults()){
        issueList.add(reqIssue)
    }
    return issueList
}

def mailSend(String subject, String body, String receipents){
    SMTPMailServer mailServer = ComponentAccessor.getMailServerManager().getDefaultSMTPMailServer();
    if (mailServer) {
    Email email = new Email(receipents);
    email.setSubject(subject);
    email.setBody(body);
    email.setMimeType("text/html")
    ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader()
    Thread.currentThread().setContextClassLoader(SMTPMailServer.class.classLoader)
    mailServer.send(email)
    Thread.currentThread().setContextClassLoader(threadClassLoader)
    } 
    else {
        log.info "problem with sending email" // Problem getting the mail server from JIRA configuration, log this error
    }
    /*SMTPMailServer mailServer = ComponentAccessor.getMailServerManager().getDefaultSMTPMailServer()
    def imapProvider = new IMAPProvider()
    def smtpProvider = new SMTPProvider()
    //def confluenceMailServerManager = ComponentLocator.getComponent(ConfluenceMailServerManager)
    Email email = new Email(receipents)
    email.setMimeType("text/html")
    email.setSubject(subject)
    email.setBody(body)
    mailServer.getSession().addProvider(imapProvider)
    mailServer.getSession().addProvider(smtpProvider)
    mailServer.send(email)
    log.info ("Email was sended")*/
}

def totalIssues = selectTotalIssues()
def line2_inprogress = selectLineNonResolvedIssues("2")
def line2_resolved = selectLineResolvedIssues("2")
def line3_inprogress = selectLineNonResolvedIssues("3")
def line3_resolved = selectLineResolvedIssues("3")
def changeItem
def transition_time_l2
def transition_time_l3
def resolved_time_l2
def resolved_time_l3
def index_wait_support
def index_l2
def index_l3
def tmp
TimeDuration duration_l2
TimeDuration duration_l3
DurationFormatUtils df = new DurationFormatUtils()
def common_duration_l2 = []
def common_duration_l3 = []
def time_resolved_l2 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("DevOps as Service. Время решения")
def time_resolved_l3 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("DevOps as Service. Время решения L3")

def getTimeResolution(Issue issue, CustomField time_resolution){
    try {
        def sum_time = 0
        DurationFormatUtils df = new DurationFormatUtils()
        issue.getCustomFieldValue(time_resolution).getCompleteSLAData().each { sum_time += it.getElapsedTime() }
        return sum_time
    } 
    catch (Exception e) {
        return "Ошибка расчета данных"
    }
}

/*log.info getTimeResolution(issue, time_resolved_l2)
log.info getTimeResolution(issue, time_resolved_l3)
*/

for (Issue l2issue: line2_resolved){
    log.info "\n \t \t \t \t \t \t \t loop L2 - ${l2issue.key}"
    tmp = getTimeResolution(l2issue, time_resolved_l2)
    log.info tmp
    common_duration_l2 << tmp
}

for (Issue l3issue: line3_resolved){
    log.info "\n \t \t \t \t \t \t \t loop L3 - ${l3issue.key}"
    tmp = getTimeResolution(l3issue, time_resolved_l3)
    log.info tmp
    common_duration_l3 << tmp
}

def common_duration_l2_sum = common_duration_l2.sum()
def common_duration_l3_sum = common_duration_l3.sum()

def result_duration_l2 = df.formatDuration(common_duration_l2_sum?.toLong(), 'HH:mm:ss', true)
def result_duration_l3 = df.formatDuration(common_duration_l3_sum?.toLong(), 'HH:mm:ss', true)

def body = """<h2>Отчет по услуге DevOps as Service</h2></br>
           <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css\" rel=\"stylesheet\" integrity=\"sha384-EVSTQN3/azprG1Anm3QDgpJLIm9Nao0Yz1ztcQTwFspd3yD65VohhpuuCOmLASjC\" crossorigin=\"anonymous\">
            <style>
            /* Стили таблицы (IKSWEB) */
            table.iksweb{text-decoration: none;border-collapse:collapse;width:100%;text-align:center;}
            table.iksweb th{font-weight:normal;font-size:14px; color:#ffffff;background-color:#354251;}
            table.iksweb td{font-size:13px;color:#354251;}
            table.iksweb td,table.iksweb th{white-space:pre-wrap;padding:10px 5px;line-height:13px;vertical-align: middle;border: 1px solid #354251;}	table.iksweb tr:hover{background-color:#f9fafb}
            table.iksweb tr:hover td{color:#354251;cursor:default;}
            </style>
            <table class=\"iksweb\">
            <thead>
            <tr>
                <th colspan=\"3\">Свод</th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td>Всего запросов DevOps as Service</td>
                <td>${totalIssues.size()}</td>
            </tr>
            <tr>
                <td>В работе на L2</td>
                <td>${line2_inprogress.size()}</td>
            </tr>
            <tr>
                <td>Решено на L2</td>
                <td>${line2_resolved.size()}</td>
            </tr>
             <tr>
                <td>В работе на L3</td>
                <td>${line3_inprogress.size()}</td>
            </tr>
            <tr>
                <td>Решено на L3</td>
                <td>${line3_resolved.size()}</td>
            </tr>
            <tr>
                <td>Общее время решенных на L2</td>
                <td>${result_duration_l2}</td>
            </tr>
            <tr>
                <td>Общее время решенных на L3</td>
                <td>${result_duration_l3}</td>
            </tr>
            </tbody>
            </table>
            
            </br>

             <table class=\"iksweb\">
            <thead>
            <tr>
                <th colspan=\"3\">Информация по обращениям решенным на L2</th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td>ФИО инженера Prime Cloud</td>
                <td>Количество</td>
            </tr>
            <tr>
                <td>Иванов Иван</td>
                <td>5</td>
            </tr>
            </tbody>
            </table>

            </br>
            
            <table class=\"iksweb\">
            <thead>
            <tr>
                <th colspan=\"3\">Информация по обращениям решенным на L3</th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td>Наименование проектной команды</td>
                <td>Количество</td>
                <td>Время решения</td>
            </tr>
            <tr>
                <td>Prime Bereke</td>
                <td>2</td>
                <td>25h</td>
            </tr>
            <tr>
                <td>Техпод АО \"ForteBank\"</td>
                <td>3</td>
                <td>11h</td>
            </tr>
            <tr>
                <td>Техпод АО \"Техпод Bank RBK\"</td>
                <td>1</td>
                <td>7h</td>
            </tr>
            </tbody>
            </table>
           """

//mailSend("Test report", body, "e.chistyakov@p-s.kz")