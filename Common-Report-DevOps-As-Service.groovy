/*Подключение необходимых библиотек*/
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
import groovy.transform.Field

/*Объявление общих глобальных переменных, которые многокрантно используются в функциях*/
    @Field def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser) //для JQL запросов
    @Field def searchProvider = ComponentAccessor.getComponent(SearchService.class) //для вызова поиска по JQL запросу 
    @Field def issueManager = ComponentAccessor.getIssueManager() //для получения объекта заявки
    @Field def user = ComponentAccessor.userManager.getUserByName("jiradmin") //для получения пользователя с правами на просмотр всех заявок
    @Field def query //переменная - строка с JQL запросом

    /*********************Даты для выгрузки за указанный период************************/
    @Field def start_date = "2023-10-01" //дата начала, задается в формате yyyy-MM-dd
    @Field def end_date = "2023-10-09"  //дата окончания, задается в формате yyyy-MM-dd
    /**********************************************************************************/
    log.info start_date
    log.info end_date
/*Объявление переменных*/
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
def body
ArrayList assignee_list = []
ArrayList project_teams_list = []
Map result_map_assignee_issues = [:]
Map result_map_project_teams_issues = [:]
Map result_map_project_teams_timeresolution = [:]
TimeDuration duration_l2
TimeDuration duration_l3
DurationFormatUtils df = new DurationFormatUtils()
def common_duration_l2 = []
def common_duration_l3 = []
//def time_resolved_l2 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("DevOps as Service. Время решения")
def time_resolved_l2 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("DevOps as Service. Время решения L2")
def time_resolved_l3 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("DevOps as Service. Время решения L3")
def cf_project_teams = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Проектная команда")

/*функция для отбора общего количества задач с типом - DevOps as Service*/
public ArrayList<Issue> selectTotalIssues(){
    ArrayList<Issue> totalIssueList = new ArrayList()
    if (start_date != "" && end_date != ""){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and created >= ${start_date} and created <= ${end_date}")
    }
    else {
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\"")
    }
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())
    //log.info ("total all issues ${results.total}")
    for (Issue reqIssue: results.getResults()){
        totalIssueList.add(reqIssue)
    }
    return totalIssueList
}

/*функция для отбора общего количества решенных задач на указанной линии поддержки, с типом - DevOps as Service. Принимает номер линии поддержки - 2 или 3*/
public ArrayList<Issue> selectLineResolvedIssues(String lineNumber){
    ArrayList<Issue> issueList = new ArrayList()
    if (lineNumber == "2" && (start_date != "" && end_date != "")){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed FROM \"${lineNumber} линия\" to \"Решен\" and status was not in (\"3 линия\") and status = Решен and resolved >= ${start_date} and resolved <= ${end_date}")
    }
    else if (lineNumber == "2" && (start_date == "" && end_date == "")){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed FROM (\"${lineNumber} линия\") to \"Решен\" and status was not in \"3 линия\" and status = Решен")
        }
    else if (lineNumber == "3" && (start_date != "" && end_date != "")){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed FROM (\"${lineNumber} линия\") to \"Решен\" and status = Решен and resolved >= ${start_date} and resolved <= ${end_date}")
    }
    else if (lineNumber == "3" && (start_date == "" && end_date == "")){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed FROM (\"${lineNumber} линия\") to \"Решен\" and status = Решен")
    }
    else {
        throw new Exception("Передан неверный параметр, ожидалось 2 или 3")
    }
    log.info query.getQueryString()
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())

    //log.info ("total L${lineNumber} issues ${results.total}")
    for (Issue reqIssue: results.getResults()){
        issueList.add(reqIssue)
    }
    return issueList
}

/*функция для отбора общего количества нерешенных задач на указанной линии поддержки, с типом - DevOps as Service. Принимает номер линии поддержки - 2 или 3*/
public ArrayList<Issue> selectLineNonResolvedIssues(String lineNumber){
    ArrayList<Issue> issueList = new ArrayList()
    if (start_date != "" && end_date != ""){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status = \"${lineNumber} линия\" and created >= ${start_date} and created <= ${end_date}")
    }
    else {
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status = \"${lineNumber} линия\"")
    }
    //log.info query.getQueryString()
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())

    //log.info ("total not resolved issues ${results.total}")
    for (Issue reqIssue: results.getResults()){
        issueList.add(reqIssue)
    }
    return issueList
}

/*функция для отправки письма с отчетом по почте. Принимает тему письма, тело письма, список емайл адресов получателей*/
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
}

/*функция для вычисления времени решения по заявке, получает код заявки и поле с названием решения (L2 или L3)*/
def getTimeResolution(Issue issue, CustomField time_resolution){
    try {
        def sum_time = 0
        DurationFormatUtils df = new DurationFormatUtils()
        issue.getCustomFieldValue(time_resolution).getCompleteSLAData().each { sum_time += it.getElapsedTime() }
        //log.info "DEBUG " + issue.key + " " + time_resolution + " " + sum_time
        return sum_time
    } 
    catch (Exception e) {
        return "Ошибка расчета данных"
    }
}
/*функция для вычисления времени решения по проектной команде, получает название проектной команды и поле с названием решения (L2 или L3)*/
def getTimeResolutionByProjectTeam(String project_team, CustomField time_resolution){
    try {
        def sum_time = 0
        Map result_list = [:]
        DurationFormatUtils df = new DurationFormatUtils()
        project_team = project_team.replaceAll('"', '\\\\"')
        if (project_team != "empty" && start_date != "" && end_date != ""){
            query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"3 линия\" to \"Решен\" and status = \"Решен\" and \"Проектная команда\" = \"${project_team}\" and resolved >= ${start_date} and resolved <= ${end_date}")
        }
        else if (project_team != "empty" && start_date == "" && end_date == ""){
            query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"3 линия\" to \"Решен\" and status = \"Решен\" and \"Проектная команда\" = \"${project_team}\"")
        }
        else{
            query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"3 линия\" to \"Решен\" and status = \"Решен\" and \"Проектная команда\" is empty")
        }
        //log.info query.getQueryString()
        def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())
        //log.info "Total count issues by ${project_team} = ${results.total}"
        for (Issue issue: results.getResults()){
            issue.getCustomFieldValue(time_resolution).getCompleteSLAData().each { sum_time += it.getElapsedTime() }
        }
        return df.formatDuration(sum_time?.toLong(), 'HH:mm:ss', true)
    } 
    catch (Exception e) {
        return "Ошибка расчета данных"
    }
}

/*функция для вычисления количества решенных заявок указанным исполнителем, принимает логин исполнителя, и названия статусов из какого статуса и в какой, к примеру для вычисления
количества заявок решенных на L2 конкретным исполнителем, без передачи на L3*/
def getCountResolvedIssuesByAssignee(String assignee, String fromStatusName, String toStatusName){
    if (start_date != "" && end_date != ""){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"${fromStatusName}\" to \"${toStatusName}\" and status was not in (\"3 линия\") and status = \"Решен\" and assignee = ${assignee} and resolved >= ${start_date} and resolved <= ${end_date}")
    }
    else {
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"${fromStatusName}\" to \"${toStatusName}\" and status was not in (\"3 линия\") and status = \"Решен\" and assignee = ${assignee}")
    }
    //log.info query.getQueryString()
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())

    //log.info ("total not resolved issues ${results.total}")
    return results.total
}

/*функция для вычисления количества решенных заявок по проеткной команде, принимает название проектной команды*/
def getCountResolvedIssuesByProjectTeams(String project_team){
    project_team = project_team.replaceAll('"', '\\\\"')
    //log.info "project team = " + project_team
    if (project_team != "empty" && start_date != "" && end_date != ""){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"3 линия\" to \"Решен\" and status = \"Решен\" and \"Проектная команда\" = \"${project_team}\" and resolved >= ${start_date} and resolved <= ${end_date}")
    }
    else if (project_team != "empty" && start_date == "" && end_date == ""){
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"3 линия\" to \"Решен\" and status = \"Решен\" and \"Проектная команда\" = \"${project_team}\"")
    }
    else{
        query = jqlQueryParser.parseQuery("project = PCLOUD and issuetype = \"Запрос проектной команды (DevOps as Service)\" and status changed from \"3 линия\" to \"Решен\" and status = \"Решен\" and \"Проектная команда\" is empty")
    }
    //log.info query.getQueryString()
    def results = searchProvider.search(user, query, PagerFilter.getUnlimitedFilter())

    //log.info ("total not resolved issues ${results.total}")
    return results.total
}


/*log.info getTimeResolution(issue, time_resolved_l2)
log.info getTimeResolution(issue, time_resolved_l3)
*/

/*Цикл для перебора заявок решенных на L2 и формирования списка исполнителей, решавших данные заявки*/
for (Issue l2issue: line2_resolved){
    //log.info "\n \t \t \t \t \t \t \t loop L2 - ${l2issue.key}"
    tmp = getTimeResolution(l2issue, time_resolved_l2)
    common_duration_l2 << tmp
    if (l2issue?.assignee){
    //    log.info l2issue.getAssignee().getName()
        assignee_list.add(l2issue.getAssignee().getName())
    }
}

/*Цикл для перебора уникального списка исполнителей из пред.этапа, подсчета решенных им задач и формирования справочника в формате исполнитель:кол-во задач*/
for (f in assignee_list.unique()){
    //log.info "assignee = " + f
    def count = getCountResolvedIssuesByAssignee(f.toString(), "2 линия", "Решен")
    //log.info "count = " + count
    def assignee_displayName = ComponentAccessor.userManager.getUserByName(f.toString())
    result_map_assignee_issues.putAt(assignee_displayName.getDisplayName(), count.toString())
}
//log.info "result_map_assignee_issues = " + result_map_assignee_issues

/*Цикл для перебора заявок решенных на L3 и формирования списка проектных команд, подавших данные заявки*/
for (Issue l3issue: line3_resolved){
    //log.info "\n \t \t \t \t \t \t \t loop L3 - ${l3issue.key}"
    tmp = getTimeResolution(l3issue, time_resolved_l3)
    common_duration_l3 << tmp
    if (l3issue?.getCustomFieldValue(cf_project_teams)){
        project_teams_list.add(l3issue?.getCustomFieldValue(cf_project_teams).toString())
    }
    else {
        project_teams_list.add("empty")
    }
}

/*Цикл для перебора уникального списка проектных команд из пред.этапа, подсчета их задач и формирования справочника в формате проект.команда:кол-во задач*/
for (f in project_teams_list.unique()){
    def count = 0
    if (f != "empty"){
        count = getCountResolvedIssuesByProjectTeams(f.toString())
        result_map_project_teams_issues.putAt(f.toString(), count,)
    }
    else {
        count = getCountResolvedIssuesByProjectTeams("empty")
        result_map_project_teams_issues.putAt("Проектная команда не указана", count)
    }
}
//log.info "result_map_project_teams_issues = " + result_map_project_teams_issues

/*Вычисление общей суммы по времени решения на Л2 и Л3 */
def common_duration_l2_sum = common_duration_l2.sum()
def common_duration_l3_sum = common_duration_l3.sum()

/*Преобразование сумм по времени в читаемый формат*/
def result_duration_l2 = df.formatDuration(common_duration_l2_sum?.toLong(), 'HH:mm:ss', true)
def result_duration_l3 = df.formatDuration(common_duration_l3_sum?.toLong(), 'HH:mm:ss', true)

/*Формирование тела письма*/
if (start_date != "" && end_date != "")
    body = "<h2>Отчет по услуге DevOps as Service за период с ${start_date} по ${end_date}.</h2></br>"
else body = "<h2>Отчет по услуге DevOps as Service за прошедшую неделю.</h2></br>"
        body += """<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css\" rel=\"stylesheet\" integrity=\"sha384-EVSTQN3/azprG1Anm3QDgpJLIm9Nao0Yz1ztcQTwFspd3yD65VohhpuuCOmLASjC\" crossorigin=\"anonymous\">
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
                <td><b>Всего запросов DevOps as Service</b></td>
                <td>${totalIssues.size()}</td>
            </tr>
            <tr>
                <td><b>В работе на L2</b></td>
                <td>${line2_inprogress.size()}</td>
            </tr>
            <tr>
                <td><b>Решено на L2</b></td>
                <td>${line2_resolved.size()}</td>
            </tr>
             <tr>
                <td><b>В работе на L3</b></td>
                <td>${line3_inprogress.size()}</td>
            </tr>
            <tr>
                <td><b>Решено на L3</b></td>
                <td>${line3_resolved.size()}</td>
            </tr>
            <tr>
                <td><b>Общее время решенных на L2</b></td>
                <td>${result_duration_l2} (HH:MM:SS)</td>
            </tr>
            <tr>
                <td><b>Общее время решенных на L3</b></td>
                <td>${result_duration_l3} (HH:MM:SS)</td>
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
                <td><b>ФИО инженера Prime Cloud</b></td>
                <td><b>Количество</b></td>
            </tr>"""
            for (f in result_map_assignee_issues){
            body += """<tr>
                <td>${f.getKey()}</td>
                <td>${f.getValue()}</td>
            </tr>"""
            }
            body += """</tbody>
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
                <td><b>Наименование проектной команды</b></td>
                <td><b>Количество</b></td>
                <td><b>Время решения</b></td>
            </tr>"""
            for (f in result_map_project_teams_issues){
            body += """<tr>
                <td>${f.getKey()}</td>
                <td>${f.getValue()}</td>
                <td>${getTimeResolutionByProjectTeam(f.getKey(), time_resolved_l3)} (HH:MM:SS)</td>
            </tr>"""
            }
            body += """</tbody>
            </table>
           """
/*Вызов функции по отправке почты*/
mailSend("Test report", body, "e.chistyakov@p-s.kz")