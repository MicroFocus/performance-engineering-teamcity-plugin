package com.performancecenter.teamcity.plugin;

import com.microfocus.adm.performancecenter.plugins.common.pcentities.*;
import com.microfocus.adm.performancecenter.plugins.common.rest.PcRestProxy;
import jetbrains.buildServer.RunBuildException;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.ArtifactsWatcher;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.microfocus.adm.performancecenter.plugins.common.pcentities.RunState.FINISHED;

/**
 * Created by bemh on 2/12/2018.
 */
public class PerformanceCenterProcess implements BuildProcess {

    public static final String pcReportArchiveName = "Reports.zip";
    public static final String pcReportFileName = "Report.html";
    public static final String COLLATE = "COLLATE";
    public static final String COLLATE_AND_ANALYZE = "COLLATE_AND_ANALYZE";
    public static final String DO_NOTHING = "DO_NOTHING";
    public static final String TRENDED = "Trended";
    public static final String PENDING = "Pending";
    public static final String PUBLISHING = "Publishing";
    public static final String ERROR = "Error";
    /**
     * Size of the buffer to read/write data
     */
    private static final int BUFFER_SIZE = 4096;
    final BuildProgressLogger logger;
    private final BuildRunnerContext buildRunnerContext;
    public PostRunAction postRunAction;
    PcRunResponse pcRunResponse = null;
    int runID = 0;
    String eventLogString = "";
    boolean trendReportReady = false;
    String pcReportFile;
    private AgentRunningBuild runningBuild;
    private ArtifactsWatcher artifactsWatcher;
    private PcRestProxy pcRestProxy;
    private Map<String, String> params;

    public PerformanceCenterProcess(@NotNull AgentRunningBuild build, @NotNull BuildRunnerContext context, ArtifactsWatcher artifactsWatcher) {
        runningBuild = build;
        buildRunnerContext = context;
        logger = build.getBuildLogger();
        this.artifactsWatcher = artifactsWatcher;
    }

    @Override
    public void start() throws RunBuildException {
        try {
            StringBuilder artifactsUrl = new StringBuilder();
            File file = buildRunnerContext.getWorkingDirectory();
            logger.message("getWorkingDirectory: " + file.toString());
            //String baseUrl = runningBuild.getArtifactsPaths() .get("teamcity.serverUrl");

            logger.message("Authenticating");
//            Map params4 = buildRunnerContext.getRunnerParameters();
//            logger.message("getRunnerParameters parameters: " + params4.toString());
            params = new HashMap<>(buildRunnerContext.getRunnerParameters());


            postRunAction = convertStringBackToPostRunAction(params.get(StringsConstants.POSTRUNACTION));
            pcRestProxy = new PcRestProxy(isHTTPSProtocol(), params.get(StringsConstants.PCSERVER), isAuthenticateWithToken(), params.get(StringsConstants.DOMAIN), params.get(StringsConstants.PCPROJECT), params.get(StringsConstants.PROXYURL), params.get(StringsConstants.PROXYUSER), params.get(StringsConstants.PROXYPASSWORD));

            if (params.get(StringsConstants.PROXYURL) != null && !params.get(StringsConstants.PROXYURL).isEmpty()) {
                logger.message("Using proxy: " + params.get(StringsConstants.PROXYURL));
            }

            boolean isLoggedIn = pcLogin();
            logger.message(String.format("Login %s", isLoggedIn ? "succeeded" : "failed"));
            if (!isLoggedIn)
                throw new PcException("Login Failed");


            logger.message("Executing Load Test:");
            logger.message("====================");
            logger.message("Domain: " + params.get(StringsConstants.DOMAIN));
            logger.message("Project: " + params.get(StringsConstants.PCPROJECT));
            logger.message("Test ID: " + params.get(StringsConstants.TESTID));
            if ("AUTO".equals(params.get(StringsConstants.TESTINSTANCEIDOPTIONS))) {
                logger.message("Test Instance ID: " + params.get(StringsConstants.TESTINSTANCEIDOPTIONS));
            } else {
                logger.message("Test Instance ID: " + params.get(StringsConstants.TESTINSTANCEID));
            }
            logger.message("Timeslot Duration: " + params.get(StringsConstants.TIMESLOTHOURS) + ":" + params.get(StringsConstants.TIMESLOTMINUTES) + " (h:mm)");
            if (DO_NOTHING.equals(params.get(StringsConstants.POSTRUNACTION))) {
                logger.message("Post Run Action: Do Not Collate");
            } else {
                logger.message("Post Run Action: " + params.get(StringsConstants.POSTRUNACTION));
            }

            logger.message("Use VUDS: " + ("true".equals(params.get(StringsConstants.ISVUDS.toLowerCase())) ? "true" : "false"));

            logger.message("====================");

            runID = startRun();
            pcRunResponse = waitForRunCompletion(runID, 5000);
            updateSLAStatus(pcRunResponse);
            Thread.sleep(3000); // this is for when no file is in Artifacts, the working directory is being deleted by TC automatically after few seconds so we are waiting 3 seconds
            if (buildRunnerContext.getWorkingDirectory().exists()) {
                FileUtils.cleanDirectory(buildRunnerContext.getWorkingDirectory());
            }
            if (pcRunResponse != null && RunState.get(pcRunResponse.getRunState()) == RunState.FINISHED && postRunAction.toString().equals(COLLATE_AND_ANALYZE)) {

                pcReportFile = publishRunReport(runID, String.valueOf(buildRunnerContext.getWorkingDirectory()));

                logger.message("View analysis report of run: " + runID + ", in the Artifacts Tab, from the path Reports.zip/Report.html.");

                // Adding the trend report section if ID has been set
                // Adding the trend report if the Associated Trend report is selected.
                if (((("ASSOCIATED").equals(params.get(StringsConstants.TRENDINGOPTIONS))) || (("USE_ID").equals(params.get(StringsConstants.TRENDINGOPTIONS)) && params.get(StringsConstants.TRENDREPORTID) != null)) && RunState.get(pcRunResponse.getRunState()) != RunState.RUN_FAILURE) {
                    Thread.sleep(5000);
                    addRunToTrendReport(runID, params.get(StringsConstants.TRENDREPORTID));
                    Thread.sleep(5000);
                    waitForRunToPublishOnTrendReport(runID, params.get(StringsConstants.TRENDREPORTID));
                    downloadTrendReportAsPdf(params.get(StringsConstants.TRENDREPORTID), getTrendReportsDirectory(runID));
                    trendReportReady = true;
                }

            } else if (pcRunResponse != null && RunState.get(pcRunResponse.getRunState()).ordinal() > RunState.FINISHED.ordinal()) {
                PcRunEventLog eventLog = getRunEventLog(runID);
                eventLogString = buildEventLogString(eventLog);
                throw new RunBuildException(String.format("While monitoring the run's state, the state '%s' was found instead of '%s'", RunState.get(pcRunResponse.getRunState()), RunState.FINISHED));
            }

        } catch (PcException e) {
            throw new RunBuildException(e.toString());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isInterrupted() {
        return false;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public void interrupt() {

    }

    @NotNull
    @Override
    public BuildFinishedStatus waitFor() throws RunBuildException {
        return null;
    }

    private boolean pcLogin() throws RunBuildException {
        String pass = "";
        try {
            if (params.get(StringsConstants.PASSWORD) != null)
                pass = params.get(StringsConstants.PASSWORD).toString();
            logger.message(String.format("Trying to login to Server '%s://%s/LoadTest/%s' with %s '%s']", isHTTPSProtocol(), pcRestProxy.GetPcServer(), pcRestProxy.GetTenant(), isAuthenticateWithToken() ? "ID Key" : "user", params.get(StringsConstants.USERNAME).toString()));
            pcRestProxy.authenticate(params.get(StringsConstants.USERNAME).toString(), pass);
            return true;
        } catch (PcException e) {
            throw new RunBuildException(e.toString());
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int startRun() throws IOException, PcException, RunBuildException, InterruptedException {
        int testID = Integer.parseInt(params.get(StringsConstants.TESTID));
        int testInstanceID = getCorrectTestInstanceID(testID);
        TimeslotDuration timeslotDuration = new TimeslotDuration(params.get(StringsConstants.TIMESLOTHOURS), params.get(StringsConstants.TIMESLOTMINUTES));

        setCorrectTrendReportID();
        PcRunResponse response = null;
        try {
            response = pcRestProxy.startRun(testID,
                    testInstanceID,
                    timeslotDuration,
                    postRunAction.getValue(),
                    isVuds(), 0
            );
            logger.message(String.format("\n%s (TestID: %s, RunID: %s, TimeslotID: %s))\n",
                    "Run started",
                    response.getTestID(),
                    response.getID(),
                    response.getTimeslotID()));
            return response.getID();
        } catch (NumberFormatException | PcException | IOException ex) {
            logger.message(String.format("%s. %s: %s", "The run failed to start", "Error", ex.getMessage()));
            if (!("REPEAT_WITH_PARAMETERS".equals(params.get(StringsConstants.TIMESLOT_CREATION_FAILURE_OPTIONS)))) {
                throw ex;
            }
        }
        return startRunRepeatIfNeeded(testID, testInstanceID, timeslotDuration, response);
    }

    private int startRunRepeatIfNeeded(int testID, int testInstanceID, TimeslotDuration timeslotDuration, PcRunResponse response) throws PcException, IOException, InterruptedException {
        if (("REPEAT_WITH_PARAMETERS".equals(params.get(StringsConstants.TIMESLOT_CREATION_FAILURE_OPTIONS)))) {
            int retryCount = 1;
            int retryDelay = getIntFromString(params.get(StringsConstants.TIMESLOT_REPEAT_DELAY), 1, 1, 10);
            int retryAttempts = getIntFromString(params.get(StringsConstants.TIMESLOT_REPEAT_ATTEMPTS), 2, 2, 10);
            //logger.message(String.format("Retry with parameter: retryDelay = %s, retryAttempts = %s",params.get(StringsConstants.TIMESLOT_REPEAT_DELAY), params.get(StringsConstants.TIMESLOT_REPEAT_ATTEMPTS)));
            while (retryCount < retryAttempts) {
                retryCount++;
                try {
                    if (retryCount <= retryAttempts) {
                        logger.message(String.format("%s %s - %s. %s %s %s. %s %s.",
                                "Attempt # ",
                                retryCount,
                                "Previous attempt failed",
                                "Attempting to start again in",
                                retryDelay,
                                "minute(s)",
                                retryAttempts - retryCount,
                                "remaining attempt(s)"
                        ));
                        Thread.sleep(retryDelay * 60 * 1000);
                    }
                    response = pcRestProxy.startRun(testID,
                            testInstanceID,
                            timeslotDuration,
                            postRunAction.getValue(),
                            isVuds(), 0
                    );
                    return response.getID();
                } catch (NumberFormatException | PcException | IOException ex) {

                    logger.message(String.format("%s %s - %s. %s: %s",
                            "Attempt # ",
                            retryCount,
                            "Attempt failed",
                            "Error",
                            ex.getMessage()));
                    if (retryCount == retryAttempts) {
                        throw ex;
                    }
                } catch (InterruptedException ex) {
                    logger.message("Wait was interrupted");
                    throw ex;
                }
            }
        }
        return response.getID();
    }

    private int getIntFromString(String strValue, int defaultValue, int minValue, int maxValue) {
        try {
            if (strValue == null || strValue.isEmpty())
                return defaultValue;
            int intValue = Integer.parseInt(strValue);
            return (intValue < minValue) ? minValue : (intValue > maxValue) ? maxValue : intValue;
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private int getCorrectTestInstanceID(int testID) throws RunBuildException {
        if ("AUTO".equals(params.get(StringsConstants.TESTINSTANCEIDOPTIONS))) {
            try {
                logger.message("Searching for available Test Instance");
                PcTestInstances pcTestInstances = pcRestProxy.getTestInstancesByTestId(testID);
                int testInstanceID = 0;
                if (pcTestInstances != null && pcTestInstances.getTestInstancesList() != null) {
                    PcTestInstance pcTestInstance = pcTestInstances.getTestInstancesList().get(pcTestInstances.getTestInstancesList().size() - 1);
                    testInstanceID = pcTestInstance.getInstanceId();
                    logger.message("Found testInstanceId: " + testInstanceID);
                } else {
                    logger.message("Could not find available TestInstanceID, Creating Test Instance.");
                    logger.message("Searching for available TestSet");
                    // Get a random TestSet
                    PcTestSets pcTestSets = pcRestProxy.GetAllTestSets();
                    if (pcTestSets != null && pcTestSets.getPcTestSetsList() != null) {
                        PcTestSet pcTestSet = pcTestSets.getPcTestSetsList().get(pcTestSets.getPcTestSetsList().size() - 1);
                        int testSetID = pcTestSet.getTestSetID();
                        logger.message(String.format("Creating Test Instance with testID: %s and TestSetID: %s", testID, testSetID));
                        testInstanceID = pcRestProxy.createTestInstance(testID, testSetID);
                        logger.message(String.format("Test Instance with ID : %s has been created successfully.", testInstanceID));
                    } else {
                        String msg = "No TestSetID available in project, please create a testset from application UI";
                        logger.message(msg);
                        throw new PcException(msg);
                    }
                }
                return testInstanceID;


            } catch (PcException e) {
                e.printStackTrace();
                throw new RunBuildException(e.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return Integer.parseInt(params.get(StringsConstants.TESTINSTANCEID));
    }

    private void setCorrectTrendReportID() throws IOException, PcException {
        // If the user selected "Use trend report associated with the test" we want the report ID to be the one from the test
        if (("ASSOCIATED").equals((params.get(StringsConstants.TRENDINGOPTIONS))) && postRunAction.toString().equals(COLLATE_AND_ANALYZE)) {
            PcTest pcTest = pcRestProxy.getTestData(Integer.parseInt(params.get(StringsConstants.TESTID)));
            logger.message("Test Name= " + pcTest.getTestName() + "Test ID= " + pcTest.getTestId());
            if (pcTest.getTrendReportId() > -1)
                setTrendReportId(String.valueOf(pcTest.getTrendReportId()));
            else {
                String msg = "No trend report ID is associated with the test.\n" +
                        "Please turn Automatic Trending on for the test through application's UI.\n" +
                        "Alternatively you can check 'Add run to trend report with ID' on configuration dialog.";
                throw new PcException(msg);
            }
        }
    }

    public PcRunResponse waitForRunCompletion(int runId, int interval) throws InterruptedException, ClientProtocolException, PcException, IOException {
        RunState state = RunState.UNDEFINED;
        if (postRunAction.toString().equals(DO_NOTHING)) {
            state = RunState.BEFORE_COLLATING_RESULTS;

        } else if (postRunAction.toString().equals(COLLATE)) {
            state = RunState.BEFORE_CREATING_ANALYSIS_DATA;

        } else if (postRunAction.toString().equals(COLLATE_AND_ANALYZE)) {
            state = RunState.FINISHED;

        }
        return waitForRunState(runId, state, interval);
    }

    private PcRunResponse waitForRunState(int runId, RunState completionState, int interval) throws InterruptedException,
            ClientProtocolException, PcException, IOException {

        int counter = 0;
        RunState[] states = {RunState.BEFORE_COLLATING_RESULTS, RunState.BEFORE_CREATING_ANALYSIS_DATA};
        PcRunResponse response = null;
        RunState lastState = RunState.UNDEFINED;
        do {
            response = pcRestProxy.getRunData(runId);
            RunState currentState = RunState.get(response.getRunState());
            if (lastState.ordinal() < currentState.ordinal()) {
                lastState = currentState;
                logger.message(String.format("RunID: %s - State = %s", runId, currentState.value()));
            }

            // In case we are in state before collate or before analyze, we will wait 1 minute for the state to change otherwise we exit
            // because the user probably stopped the run from PC or timeslot has reached the end.
            if (Arrays.asList(states).contains(currentState)) {
                counter++;
                Thread.sleep(1000);
                if (counter > 60) {
                    logger.message(String.format("RunID: %s  - Stopped from server side with state = %s", runId, currentState.value()));
                    break;
                }
            } else {
                counter = 0;
                Thread.sleep(interval);
            }
        } while (lastState.ordinal() < completionState.ordinal());
        return response;
    }

    public String publishRunReport(int runId, String reportDirectory) throws IOException, PcException, InterruptedException {


        PcRunResults runResultsList = pcRestProxy.getRunResults(runId);
        if (runResultsList.getResultsList() != null) {
            for (PcRunResult result : runResultsList.getResultsList()) {
                if (result.getName().equals(pcReportArchiveName)) {
                    File dir = new File(reportDirectory + File.separator + "Reports");// taskContext.getBuildContext().getBuildNumber());
                    dir.mkdirs();
                    String reportArchiveFullPath = dir.getCanonicalPath() + IOUtils.DIR_SEPARATOR + pcReportArchiveName;
                    logger.message("Publishing analysis report");
                    pcRestProxy.GetRunResultData(runId, result.getID(), reportArchiveFullPath);
                    File fp = new File(reportArchiveFullPath);
                    unzip(reportArchiveFullPath, fp.getParent().toString());
                    String reportFile = dir.getPath() + File.separator + pcReportFileName;
                    artifactsWatcher.addNewArtifactsPath("Reports\\Report => Reports\\Report");
                    //   artifactsWatcher.addNewArtifactsPath("Reports\\Report.html");
                    artifactsWatcher.addNewArtifactsPath("Reports\\Reports.zip");
                    //       publishHTMLReportToArtifact();
                    //Deleting the unziped report file
                    //  FileUtils.deleteDirectory(dir);
                    return reportFile;
                }
            }
        }
        logger.message("Failed to get run report");
        return null;
    }

    public void publishHTMLReportToArtifact() {
        //  ArtifactsBuilder builder = new ArtifactsBuilder();
//        final ArtifactsBuilder builder = new ArtifactsBuilder();
//        Map<String, String> config = new HashMap<>();
//        List<DeployDetailsArtifact> runnerSpecificDeployableArtifacts = getDeployableArtifacts();
//        ArtifactDefinitionContextImpl artifact = new ArtifactDefinitionContextImpl("Build_" + String.valueOf(taskContext.getBuildContext().getBuildNumber()) + "_reports",false,null);
//        artifact.setCopyPattern("**/*");
//        buildRunnerContext.get //getBuildContext().getArtifactContext(). .getDefinitionContexts().add(artifact);
    }

    public void addRunToTrendReport(int runId, String trendReportId) {

        TrendReportRequest trRequest = new TrendReportRequest(params.get(StringsConstants.PCPROJECT), runId, null);
        logger.message("Adding run: " + runId + " to trend report: " + trendReportId);
        try {
            pcRestProxy.updateTrendReport(trendReportId, trRequest);
            logger.message("Publishing run: " + runId + " on trend report: " + trendReportId);
        } catch (PcException e) {
            logger.message("Failed to add run to trend report: " + e.getMessage());
        } catch (IOException e) {
            logger.message("Failed to add run to trend report: Problem connecting to PC Server");
        }
    }

    public void waitForRunToPublishOnTrendReport(int runId, String trendReportId) throws PcException, IOException, InterruptedException {

        ArrayList<PcTrendedRun> trendReportMetaDataResultsList;
        boolean publishEnded = false;
        int counter = 0;

        do {
            trendReportMetaDataResultsList = pcRestProxy.getTrendReportMetaData(trendReportId);

            if (trendReportMetaDataResultsList.isEmpty()) break;

            for (PcTrendedRun result : trendReportMetaDataResultsList) {

                if (result.getRunID() != runId) continue;

                if (result.getState().equals(TRENDED) || result.getState().equals(ERROR)) {
                    publishEnded = true;
                    logger.message("Run: " + runId + " publishing status: " + result.getState());
                    break;
                } else {
                    Thread.sleep(5000);
                    counter++;
                    if (counter >= 120) {
                        String msg = "Error: Publishing didn't ended after 10 minutes, aborting...";
                        throw new PcException(msg);
                    }
                }
            }

        } while (!publishEnded && counter < 120);
    }

    /**
     * Extracts a zip file specified by the zipFilePath to a directory specified by
     * destDirectory (will be created if does not exists)
     *
     * @param zipFilePath
     * @param destDirectory
     * @throws IOException
     */
    public void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdir();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    /**
     * Extracts a zip entry (file entry)
     *
     * @param zipIn
     * @param filePath
     * @throws IOException
     */
    private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    private void setTrendReportId(String trendReportId) {
        params.put(StringsConstants.TRENDREPORTID, trendReportId);
    }

    public boolean downloadTrendReportAsPdf(String trendReportId, String directory) throws PcException {


        try {
            logger.message("Downloading trend report: " + trendReportId + " in PDF format");
            InputStream in = pcRestProxy.getTrendingPDF(trendReportId);
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdirs();
            } else {
                FileUtils.deleteDirectory(dir);
                dir.mkdirs();
            }
            String filePath = directory + IOUtils.DIR_SEPARATOR + "trendReport" + trendReportId + ".pdf";
            Path destination = Paths.get(filePath);
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            logger.message("Trend report: " + trendReportId + " was successfully downloaded");
            logger.message("View trend report in the Artifacts Tab.");
            artifactsWatcher.addNewArtifactsPath("Reports\\TrendReports => Reports\\TrendReports");
        } catch (Exception e) {

            logger.message("Failed to download trend report: " + e.getMessage());
            throw new PcException(e.getMessage());
        }

        return true;

    }

    private String getTrendReportsDirectory(int runId) {
//        return String.valueOf(taskContext.getWorkingDirectory())  + File.separator + taskContext.getBuildContext().getBuildNumber() +  File.separator + "TrendReports";
        return String.valueOf(buildRunnerContext.getWorkingDirectory()) + File.separator + "Reports" + File.separator + "TrendReports";
    }

    private Boolean isVuds() {
        if (params.get(StringsConstants.ISVUDS) != null)
            return true;
        return false;
    }

    private Boolean isSLA() {
        if (params.get(StringsConstants.ISSLA) != null)
            return true;
        return false;
    }

    private String isHTTPSProtocol() {
        if (params.get(StringsConstants.ISHTTPS) != null)
            return "https";
        return "http";
    }

    private boolean isAuthenticateWithToken() {
        if (params.get(StringsConstants.ISAUTHENTICATEWITHTOKEN) != null)
            return true;
        return false;
    }

    private PostRunAction convertStringBackToPostRunAction(String postRunAction) {
        for (PostRunAction p : PostRunAction.values()) {
            if (postRunAction.equals(p.toString())) {
                return p;
            }
        }
        return PostRunAction.DO_NOTHING;

    }


    private String buildEventLogString(PcRunEventLog eventLog) {

        String logFormat = "%-5s | %-7s | %-19s | %s\n";
        StringBuilder eventLogStr = new StringBuilder("Event Log:\n\n" + String.format(logFormat, "ID", "TYPE", "TIME", "DESCRIPTION"));
        for (PcRunEventLogRecord record : eventLog.getRecordsList()) {
            eventLogStr.append(String.format(logFormat, record.getID(), record.getType(), record.getTime(), record.getDescription()));
        }
        return eventLogStr.toString();
    }

    public PcRunEventLog getRunEventLog(int runId) {
        try {
            return pcRestProxy.getRunEventLog(runId);
        } catch (PcException e) {
            logger.message(e.getMessage());
        } catch (Exception e) {
            logger.message(String.valueOf(e));
        }
        return null;
    }

    private void updateSLAStatus(PcRunResponse response) {
        RunState runState = RunState.get(response.getRunState());
        if (isSLA() && runState == FINISHED && !(response.getRunSLAStatus().equalsIgnoreCase("passed"))) {
            logger.error("Run measurements did not reach SLA criteria. Run SLA Status: " + response.getRunSLAStatus());

        } else if (isSLA() && runState == FINISHED && (response.getRunSLAStatus().equalsIgnoreCase("passed"))) {
            logger.message("Run measurements has reached SLA criteria. Run SLA Status: " + response.getRunSLAStatus());
        }
    }
}
