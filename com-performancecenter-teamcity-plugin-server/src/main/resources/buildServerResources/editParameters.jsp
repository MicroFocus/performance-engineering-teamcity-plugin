<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<jsp:useBean id="constants" class="com.performancecenter.teamcity.plugin.StringsConstants"/>


<style type="text/css">
    .runnerFormTable th {
        width: 20%;
    }

    .runnerFormTable td:first-child {
        width: 10%;
    }

    td.nobreackline {
        white-space: nowrap
    }
</style>


<script type="text/javascript">


    function hideElementByID(elemID, boolHide) {
        var testInstanceID = document.getElementById(elemID);
        if (boolHide)
            testInstanceID.hide();
        else
            testInstanceID.show();
    }

    function hideTrending() {
        var selectedPostRunAction = document.getElementById("selectPostRunOption");
        if (selectedPostRunAction.value.localeCompare("COLLATE_AND_ANALYZE") == 0) {
            hideElementByID("trendingtDiv", false);
        } else {
            hideElementByID("trendingtDiv", true);
        }
        // if(selectedPostRunAction.va)
        //hideElementByID("trendingtDiv",true)
    }


    $j(document).ready(function () {

        //Handling TestInstaceID on Load
        hideElementByID("t_i_id", true);
        var tiAuto = document.getElementById("tiAuto");
        var tiManual = document.getElementById("tiManual");
        if (!(tiAuto.checked || tiManual.checked))
            tiManual.checked = true;
        if (tiManual.checked)
            hideElementByID("t_i_id", false);

        //Handling TrendReportID on Load
        hideElementByID("trendReportDiv", true);
        var noTrend = document.getElementById("noTrend");
        var associated = document.getElementById("associated");
        var useID = document.getElementById("useID");
        if (!(noTrend.checked || associated.checked || useID.checked))
            noTrend.checked = true;
        else if (useID.checked)
            hideElementByID("trendReportDiv", false);

        //Handling timeslotRepeatParameters on Load
        hideElementByID("timeslotRepeatParameters", true);
        var doNotRepeat = document.getElementById("doNotRepeat");
        var repeatWithParameters = document.getElementById("repeatWithParameters");
        if (!(doNotRepeat.checked || repeatWithParameters.checked))
            doNotRepeat.checked = true;
        else if (repeatWithParameters.checked)
            hideElementByID("timeslotRepeatParameters", false);

        //Handling trendingtDiv on Load
        hideTrending();

        var minutes = document.getElementById('Minutes');
        minutes.value = "30";
        var hours = document.getElementById('Hours');
        hours.value = "0";


        $j('#tiAuto').click(function () {
            hideElementByID('t_i_id', true);
        });

        $j('#tiManual').click(function () {
            hideElementByID('t_i_id', false);
        });

        $j('#noTrend').click(function () {
            hideElementByID('trendReportDiv', true);
        });

        $j('#associated').click(function () {
            hideElementByID('trendReportDiv', true);
        });
        $j('#useID').click(function () {
            hideElementByID('trendReportDiv', false);
        });
        $j('#doNotRepeat').click(function () {
            hideElementByID('timeslotRepeatParameters', true);
        });
        $j('#repeatWithParameters').click(function () {
            hideElementByID('timeslotRepeatParameters', false);
        });


        $j('#selectPostRunOption').change(function () {
            hideTrending();
        });


    });


</script>

<l:settingsGroup title="Parameters">


    <c:if test="${'AUTO' == constants.testinstanceidoptions}">
        <c:set var="t_i_id" value="style='display: none'"/>
    </c:if>

    <tr>
        <th>
            <label>PC Server <bs:helpIcon iconTitle="
Enter the hostname or IP address of a PC server.<br>
Example: If the server URL is http://MY_SERVER:PORT/loadtest, enter MY_SERVER:PORT."/></label>
        </th>
        <td><props:textProperty name="${constants.pcserver}"/>
            <span class="error" id="error_${constants.pcserver}"></span>
            <span class="smallNote">Hostname or IP address</span>
            <props:checkboxProperty name="${constants.ishttps}"/>Use HTTPS Protocol

        </td>

    </tr>
    <tr>
        <td colspan="4">
            <props:checkboxProperty name="${constants.isAuthenticateWithToken}"/>Use Token For
            Authentication<bs:helpIcon iconTitle="
Depending on the authentication type required by your server, credentials can be a username and password, or an API key for SSO or LDAP authentication.
<ul>
<li>Username and password:
<ul>
<li>User name. Enter the user name required to connect to the server.</li>
<li>Password. Enter the password required to connect to the server.</li>
</ul>
</li>
<li>SSO or LDAP authentication.
<ul>
<li>Select Authenticate with token.</li>
<li>Respectively enter the Client ID and Secret key obtained from the site administration in the Id Key and Secret key fields.</li>
</ul>
</li>
</ul>
"/>
        </td>
    </tr>
    <tr>
        <th>
            <label>User Name / Client ID</label>
            <bs:helpIcon
                    iconTitle="User (username) or access Token's Credentials (ClientIdKey)."/>
        </th>

        <td><props:textProperty name="${constants.username}"/>
            <span class="error" id="error_${constants.username}"></span>
        </td>
    </tr>

    <tr>
        <th>
            <label>Password / Secret key</label>
            <bs:helpIcon
                    iconTitle="User's password or Token's Credentials (ClientSecretKey)."/>
        </th>
        <td><props:passwordProperty name="${constants.password}"/></td>
    </tr>
    <tr>
        <th>
            <label>Domain</label>
        </th>
        <td><props:textProperty name="${constants.domain}"/>
            <span class="error" id="error_${constants.domain}"></span>
        </td>

    </tr>
    <tr>
        <th>
            <label>PC Project</label>
        </th>
        <td><props:textProperty name="${constants.pcproject}"/>
            <span class="error" id="error_${constants.pcproject}"></span>
        </td>
    </tr>
    <tr>
        <th>
            <label>Test ID</label>

        </th>
        <td><props:textProperty name="${constants.testid}"/>
            <span class="error" id="error_${constants.testid}"></span>
        </td>
    </tr>

    <tr>
        <th><label>Test Instance ID</label></th>
        <td colspan="2">
            <props:radioButtonProperty id="tiAuto" name="${constants.testinstanceidoptions}" value="AUTO"/>Automatically
            select existing or create new if none exists (Performance Center 12.55 or later)<br/>
            <props:radioButtonProperty id="tiManual" name="${constants.testinstanceidoptions}" value="MANUAL"/>Manual
            selection<br/>
            <div id="t_i_id">
                <props:textProperty name="${constants.testinstanceid}"/>
                <span class="error" id="error_${constants.testinstanceid}"></span>
            </div>
        </td>
    </tr>

    <tr>
        <th>Local Proxy
                <%--<bs: shortHelp="text" urlPrefix="https://admhelp.microfocus.com/pc/en/12.56/online_help/Content/Resources/_TopNav/_TopNav_Home.htm" file=""/>--%>

            <bs:helpIcon iconTitle="Add your local proxy as following: <br><b>http(s)://host:port</b><br> or
                                                                                                        leave empty if not using a local proxy.<br>
                                                                                                        PAC (proxy auto-config) or Automatic configuration script are not supported."/>
        </th>
        <td><props:textProperty name="${constants.proxyurl}"/></td>
        <td>USER:<props:textProperty name="${constants.proxyuser}"/></td>
        <td>PASSWORD:<props:passwordProperty name="${constants.proxypassword}"/></td>
    </tr>

    <tr>
        <th><label>Post Run Action</label></th>
        <td colspan="3">
            <props:selectProperty id="selectPostRunOption" name="${constants.postrunaction}">
                <props:option value="COLLATE">Collate Results</props:option>
                <props:option value="COLLATE_AND_ANALYZE">Collate and Analyze</props:option>
                <props:option value="DO_NOTHING">Do Not Collate</props:option>
            </props:selectProperty>
        </td>
    </tr>
    <tr>

        <th><label>Trending</label></th>
        <td colspan="2">
            <div id="trendingtDiv">
                <props:radioButtonProperty id="noTrend" name="${constants.trendingoptions}" value="NO_TREND"/>Do Not
                Trend<br/>
                <props:radioButtonProperty id="associated" name="${constants.trendingoptions}" value="ASSOCIATED"/>Use
                trend report associated with the test - Performance Center 12.55 or later<br/>
                <props:radioButtonProperty id="useID" name="${constants.trendingoptions}" value="USE_ID"/>Add run to
                trend report with ID<br/>
                <div id="trendReportDiv">
                    <label>Trend report id </label><props:textProperty name="${constants.trendreportid}"/>
                    <span class="error" id="error_${constants.trendreportid}"></span>
                </div>
            </div>
        </td>

    </tr>


    <tr>
        <th>
            <label>Timeslot Duration</label>
        </th>
        <td style="vertical-align:middle">Hours:<props:textProperty name="${constants.timeslothours}" id="Hours"
                                                                    onchange="var hours = parseInt(this.value);
        value = (isNaN(hours) || (hours < 0)) ? 0 : ((hours > 480) ? 480 : hours);
        var minutes = document.getElementById('Minutes');
        if (value == 0 && minutes.value < 30) minutes.value = 30;
        else if (value == 480) minutes.value = 0;
        if (value == 480 && minutes.value > 0) minutes.value = 0"/>
            <input type="button" value=" /\ "
                   style="font-size:7px;margin:0;padding:0;width:20px;height:15px;vertical-align:middle"
                   onclick="var hours = document.getElementById('Hours');
                             var minutes = document.getElementById('Minutes');
                           var v = parseInt(hours.value);
                           v = (isNaN(v) || v &lt; 0) ? 0 : v + 1;
                           hours.value = (v &gt; 480) ? 480 : v;
                            if (hours.value == 480 && minutes.value > 0) minutes.value = 0"/>
            <input type="button" value=" \/ "
                   style="font-size:7px;margin:0;padding:0;width:20px;height:14px;vertical-align:middle"
                   onclick="var hours = document.getElementById('Hours');
                            var minutes = document.getElementById('Minutes');
                           var v = parseInt(hours.value);
                           v = isNaN(v) || v &lt; 1 ? 1 : hours.value = v - 1;
                           var minutes = document.getElementById('Minutes');
                           if (hours.value == 0 &amp;&amp; minutes.value &lt; 30)
                           minutes.value = 30;
                           if (hours.value == 480 && minutes.value > 0) minutes.value = 0;"/>

        </td>


        <td colspan="2" style="vertical-align:middle">Minutes:<props:textProperty name="${constants.timeslotminutes}"
                                                                                  id="Minutes" onchange="var v = parseInt(this.value);
										v = isNaN(v) || (v < 0) || (v > 59) ? 0 : v ;
										value = (v < 30 && document.getElementById('Hours').value == 0) ? 30 : v;
                                        if (value > 0 && document.getElementById('Hours').value == 480) document.getElementById('Minutes').value = 0"/>
            <input type="button" value=" /\ "
                   style="font-size:7px;margin:0;padding:0;width:20px;height:15px;vertical-align:middle"
                   onclick="var minutes = document.getElementById('Minutes');
                           var v = parseInt(minutes.value);
                           v = (v + 15) % 60; minutes.value = v - v % 15;
                           var hours = document.getElementById('Hours');
                           if (hours.value == 0 &amp;&amp; minutes.value &lt; 30)
                           minutes.value = 30;
                            if (hours.value == 480 && minutes.value > 0) minutes.value = 0;"/>
            <input type="button" value=" \/ "
                   style="font-size:7px;margin:0;padding:0;width:20px;height:14px;vertical-align:middle"
                   onclick="var minutes = document.getElementById('Minutes');
                            var hours = document.getElementById('Hours');
                           var v = parseInt(minutes.value);
                           v = (v + 45) % 60;
                           if (v % 15 != 0)
                           v = v + 15 - v % 15;
                           if (document.getElementById('Hours').value == 0 &amp;&amp; v &lt; 30)
                           v = 45;
                           minutes.value= v;
                           if (hours.value == 480 && minutes.value > 0) minutes.value = 0;"/>
        </td>


    </tr>


    <tr>
        <td colspan="4">
            <props:checkboxProperty name="${constants.isvuds}"/>Use VUDs <bs:helpIcon iconTitle="
A Virtual User Day (VUD) license provides you with a specified number of Vusers (VUDs) that you can run an unlimited number of times within a 24 hour period. Before using this option, make sure that VUDs licenses are applied in your environment."/>
        </td>
    </tr>

    <tr>
        <td colspan="4">
            <props:checkboxProperty name="${constants.issla}"/>Set step status according to SLA <bs:helpIcon iconTitle="
Select this option to set the build step status according to a predefined SLA (Service Level Agreement) configured within your performance test. Unless checked, the build-step will be labeled as Passed as long as no failures occurred."/>
        </td>
    </tr>

    <tr>
        <th>
            <label>On Timeslot creation failure</label>
            <bs:helpIcon
                    iconTitle="In case of timeslot creation failure, the task can be set to try several times (according to the value set in the 'Number of attempts' parameter) to recreate the timeslot running the test and wait a fixed delay (according to the value set to the 'Delay between attempts' parameter) between each failing attempt and the next attempt."/>
        </th>
        <td colspan="2">
            <div id="timeslotCreationFailureDiv">
                <props:radioButtonProperty id="doNotRepeat" name="${constants.timeslotcreationfailureoptions}"
                                           value="DO_NOT_REPEAT"/>Do Not Repeat<br/>
                <props:radioButtonProperty id="repeatWithParameters" name="${constants.timeslotcreationfailureoptions}"
                                           value="REPEAT_WITH_PARAMETERS"/>Repeat with the following parameters: <br/>
                <div id="timeslotRepeatParameters">
                    <table>
                        <tr>
                            <td class="nobreackline">
                                <label>Delay between attemps <bs:helpIcon
                                        iconTitle="Time (in minutes) to wait between a failed attempt and the next attempt (the minimum is 1 and the maximum is 10)."/></label>
                            </td>
                            <td>
                                <props:textProperty name="${constants.timeslotrepeatdelay}" onchange="var timeslotrepeatdelay = parseInt(this.value);
        value = (isNaN(timeslotrepeatdelay) || (timeslotrepeatdelay < 1)) ? 1 : ((timeslotrepeatdelay > 10) ? 10 : timeslotrepeatdelay);"/>
                                <span class="error" id="error_${constants.timeslotrepeatdelay}"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="nobreackline">
                                <label>Number of attempts <bs:helpIcon
                                        iconTitle="How many attempts to run the test (the minimum is 2 and the maximum is 10)."/></label>
                            </td>
                            <td>
                                <props:textProperty name="${constants.timeslotrepeatattempts}" onchange="var timeslotrepeatattempts = parseInt(this.value);
        value = (isNaN(timeslotrepeatattempts) || (timeslotrepeatattempts < 2)) ? 2 : ((timeslotrepeatattempts > 10) ? 10 : timeslotrepeatattempts);"/>
                                <span class="error" id="error_${constants.timeslotrepeatattempts}"/>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>
        </td>
    </tr>

    <tr>
        <th colspan="3">
            <c:url var="url"
                   value="https://admhelp.microfocus.com/pc/en/latest/online_help/Content/PC/Team-City-Plugin.htm"/>
            <div>For more information on configuring these settings, see the <a href="${url}" target="_blank">OpenText Enterprise Performance Engineering and TeamCity</a> documentation.
            </div>
        </th>
    </tr>


</l:settingsGroup>

