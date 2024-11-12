import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.io.BufferedWriter;
import java.io.FileWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AndroidMediaDetect {
    private static boolean isRebooted = false;
    private static LogDate logDate;

    static {
        try {
            logDate = new LogDate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static MediaCameraLog mediaCameraLog = new MediaCameraLog();
    private static AudioLog audioLog = new AudioLog();
    private static UsageStatsLog usageStatsLog = new UsageStatsLog();
    private static LogcatLog logcatLog = new LogcatLog();
    private static FilteredMediaCameraLog filteredMediaCameraLog = new FilteredMediaCameraLog();
    private static PackageLog packageLog = new PackageLog();
    private static String userid = "";

    //메인 함수
    public static void main(String[] args) throws Exception {
        mediaDetect();
    }
    public static void mediaDetect() throws Exception {

        BufferedWriter writer = new BufferedWriter(new FileWriter("log_output.txt", false));
        writer.write(logDate.getDate());

        try{
            isRebooted = LogDate.isRebooted();
        } catch (Exception e){
            writer.write("\nERROR!!! ADB 연결을 확인해주세요.\n");
            writer.flush();
            return;
        }

        writer.write("\n발견한 사용자 ID:\n" + findAllUserID());

        userid = findSecFolderUserID();

        if(isRebooted){
            int rebootTime = logDate.getUptimeMin();
            writer.write("약 " + rebootTime + "분 전에 재부팅 되었습니다.\n");

        }
        try {
            //정의한 시간 범위 로그 추출
            collectDumpsysLog("usagestats", usageStatsLog, logDate.getSD(), logDate.getED());
            collectDumpsysLog("media.camera", mediaCameraLog, logDate.getSD(), logDate.getED());
            collectDumpsysLog("audio", audioLog, logDate.getSD(), logDate.getED());

            if(mediaCameraLog.getLog().isEmpty() && audioLog.getLog().isEmpty() && logcatLog.getLog().isEmpty()){
                writer.write("\n최근 30분 이내 미디어 사용이 탐지되지 않았습니다.\n");
                if(isRebooted){
                    writer.write("재부팅으로 인해 이전 기록이 지워졌을 수 있으므로 정밀 조사가 필요합니다.\n");
                }
                writer.flush();
                return;
            }

            writer.write("카메라 ON/OFF 기록 :\n");
            writer.write(!mediaCameraLog.getLog().isEmpty() ? mediaCameraLog.getLog().toString() : "최근 30분 이내 카메라 실행 기록이 없습니다.\n");
            writer.newLine();

            findCorrespondingUsageStatsLogs(mediaCameraLog, usageStatsLog.getu0Log(), usageStatsLog.getu150Log(), filteredMediaCameraLog);
            writer.write("기본 카메라 앱 실행 전 종료된 앱 패키지 :\n");
            writer.write(filteredMediaCameraLog.getLog().toString());
            writer.newLine();

            writer.write("음성 녹음 기록 :\n");
            writer.write(!audioLog.getLog().isEmpty() ? audioLog.getLog().toString() : "최근 30분 이내 녹음 기록이 없습니다.\n");
            writer.newLine();

            writer.write("미디어 파일 생성 기록 :\n");
            if (isSMDevice()) {
                collectLogcatLog(logcatLog, logDate.getSD(), logDate.getED());
                writer.write(!logcatLog.getLog().isEmpty() ? logcatLog.getLog().toString() : "최근 30분 이내 미디어 생성 기록이 없습니다.\n");
            } else {
                ImageDBLog imageDBLog = new ImageDBLog();
                VideoDBLog videoDBLog = new VideoDBLog();
                AudioDBLog audioDBLog = new AudioDBLog();

                collectMediaDBLog("--uri content://media/external/images/media", "0", imageDBLog, logDate.getSDwY(), logDate.getEDwY());
                collectMediaDBLog("--uri content://media/external/video/media", "0", videoDBLog, logDate.getSDwY(), logDate.getEDwY());
                collectMediaDBLog("--uri content://media/external/audio/media", "0", audioDBLog, logDate.getSDwY(), logDate.getEDwY());

                collectMediaDBLog("--uri content://media/external/images/media", userid, imageDBLog, logDate.getSDwY(), logDate.getEDwY());
                collectMediaDBLog("--uri content://media/external/video/media", userid, videoDBLog, logDate.getSDwY(), logDate.getEDwY());
                collectMediaDBLog("--uri content://media/external/audio/media", userid, audioDBLog, logDate.getSDwY(), logDate.getEDwY());

                if (imageDBLog.getLog().isEmpty() && videoDBLog.getLog().isEmpty() && audioDBLog.getLog().isEmpty()) {
                    writer.write("\n최근 30분 이내 미디어 생성 기록이 없습니다.\n");
                } else {
                    writer.write("사진 DB 기록 :\n");
                    writer.write(imageDBLog.getLog().toString());
                    writer.newLine();
                    writer.write("동영상 DB 기록 :\n");
                    writer.write(videoDBLog.getLog().toString());
                    writer.newLine();
                    writer.write("음성 DB 기록 :\n");
                    writer.write(audioDBLog.getLog().toString());
                    writer.newLine();
                }
            }

            if(isRebooted){
                collectPackageLog(packageLog);
                writer.write("\n미디어 권한 보유중인 일반 패키지 :\n");
                writer.write(packageLog.getu0PermissionLogTxt() + "\n");

                writer.write("미디어 권한 보유중인 보안 폴더 패키지 :\n");
                writer.write(packageLog.getu150PermissionLogTxt() + "\n");

                writer.write("미디어 권한 보유중인 일반 패키지 실행 이력 :\n");
                writer.write(filterLogs(packageLog.getu0PermissionLog(), usageStatsLog.getu0Log()) + "\n");

                writer.write("미디어 권한 보유중인 보안 폴더 패키지 실행 이력 :\n");
                writer.write(filterLogs(packageLog.getu150PermissionLog(), usageStatsLog.getu150Log()) + "\n");
            }

            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //dumpsys 로그 획득
    private static void collectDumpsysLog(String command, Object logObject, Date startTime, Date endTime) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("adb", "shell", "dumpsys", command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        boolean captureLog = false;
        boolean isUser150Section = false;

        while ((line = reader.readLine()) != null) {
            Date logDate = null;
            if (logObject instanceof UsageStatsLog) {
                logDate = LogDate.extractUsageStatsLogDate(line);
            } else {
                logDate = LogDate.extractLogDate(line);
            }

            if (logDate != null && (logDate.before(startTime) || logDate.after(endTime))) {
                continue;
            }

            if (logObject instanceof UsageStatsLog) {
                if (line.contains("user=0")) {
                    captureLog = true;
                    isUser150Section = false;
                    ((UsageStatsLog) logObject).addU0Log(line);
                } else if (line.contains("user=" + userid)) {
                    captureLog = true;
                    isUser150Section = true;
                    ((UsageStatsLog) logObject).addU150Log(line);
                }
                if (captureLog) {
                    if (line.contains("type=ACTIVITY_RESUMED") || line.contains("type=ACTIVITY_PAUSED") || line.contains("type=ACTIVITY_STOPPED")) {
                        if (isUser150Section) {
                            line += " [SECURE FOLDER]";
                            ((UsageStatsLog) logObject).addU150Log(line);
                        } else ((UsageStatsLog) logObject).addU0Log(line);
                    }
                    if (line.trim().isEmpty()) {
                        captureLog = false;
                        isUser150Section = false;
                    }
                }
            } else if (logObject instanceof MediaCameraLog) {
                if (line.contains("== Camera service events log (most recent at top): ==")) {
                    captureLog = true;
                    continue;
                }
                if (captureLog) {
                    if (line.trim().isEmpty()) {
                        break;
                    }
                    if (line.contains("DISCONNECT") || line.contains("CONNECT") || line.contains("USER_SWITCH")) {
                        if (line.contains("DISCONNECT")) {
                            line += " [CAM OFF] " + findUsageStatsResumedInUser150(line, usageStatsLog.getu150Log(), "ACTIVITY_STOPPED");
                            ;
                        } else if(line.contains("CONNECT")){
                            line += " [CAM ON] " + findUsageStatsResumedInUser150(line, usageStatsLog.getu150Log(), "ACTIVITY_RESUMED");
                        }
                        ((MediaCameraLog) logObject).addLog(line);
                    }
                }
            } else if (logObject instanceof AudioLog) {
                if (line.contains("recording activity received by AudioService")) {
                    captureLog = true;
                    continue;
                }
                if (captureLog) {
                    if (line.trim().isEmpty()) {
                        break;
                    }
                    if (line.contains("src:MIC") || line.contains("src:CAMCORDER")) {
                        if (line.contains("uid:" + userid)) {
                            line += " [SECURE_FOLDER]";
                        }
                        ((AudioLog) logObject).addLog(line);
                    }
                }
            }
        }
    }

    //logcat 로그 획득
    private static void collectLogcatLog(Object logObject, Date startTime, Date endTime) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("adb", "logcat", "-d");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        while ((line = reader.readLine()) != null) {
            Date curLogDate = logDate.extractLogDate(line);
            if (curLogDate != null && (curLogDate.before(startTime) || curLogDate.after(endTime))) {
                continue;
            }

            if (line.contains("I PostProcessDBHelper: /storage/emulated/") || (line.contains("D MediaProvider: Open with FUSE. FilePath: /storage/emulated/") && line.contains("ShouldRedact: false. ShouldTranscode: false")) || line.contains("createTempFile: /storage/emulated/")) {
                if(line.contains(("/storage/emulated/150/"))){
                    line += " [SECURE FOLDER]";
                }
                ((LogcatLog) logObject).addLog(line);
            }
        }
    }

    // 패키지 로그 획득
    private static void collectPackageLog(Object logObject) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("adb", "shell", "dumpsys", "package", "packages");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        boolean isUser0Section = false;
        boolean isUser150Section = false;
        boolean isU0Permit = false;
        boolean isU150Permit = false;
        boolean isCamPermitted = false;
        boolean isAudioPermitted = false;

        String packageLine = "";  // 패키지 이름을 저장할 변수
        String rPackageLine = "";  // 패키지 이름을 저장할 변수
        String label = "";
        while ((line = reader.readLine()) != null) {
            if (line.contains("Package [")) {
                int startIndex = line.indexOf("Package [") + 9;
                int endIndex = line.indexOf("]", startIndex);
                if (endIndex > startIndex) {
                    packageLine = line.substring(startIndex, endIndex).trim();  // 패키지 이름 추출
                }
                ((PackageLog) logObject).addAllPackageLog(rPackageLine);
            }
            if (line.contains("appId") || line.contains("userId")) {
                rPackageLine = packageLine;
                label = "";
            }

            if (line.contains("User 0")) {
                isUser0Section = true;
                isUser150Section = false;
            } else if (line.contains("User " + userid)) {
                isUser0Section = false;
                isUser150Section = true;
                if(line.contains("installed=true") && !isBaseApp(rPackageLine)){
                    ((PackageLog) logObject).addGeneralLog(rPackageLine);
                }
            }
            if (isUser0Section || isUser150Section) {
                if(line.contains("runtime permissions:")){
                    isU0Permit = isUser0Section ? true : false;
                    isU150Permit = isUser150Section ? true : false;
                }
            }
            if (isU0Permit || isU150Permit) {
                if (line.contains("android.permission.CAMERA: granted=true")) {
                    isCamPermitted = true;
                } else if (line.contains("android.permission.RECORD_AUDIO: granted=true")) {
                    isAudioPermitted = true;
                } else if (line.contains("Package [") || line.contains("User " + userid)) {
                    if(isU0Permit){
                        label +=  isCamPermitted ? " [CAM]" : "";
                        label += isAudioPermitted ? " [AUDIO]" : "";
                        if(!label.equals("")) {
                            ((PackageLog) logObject).addU0PermissionLog("[" + rPackageLine + "]" + label);
                        }
                        isU0Permit = false;
                    } else if (isU150Permit){
                        label +=  isCamPermitted ? " [CAM]" : "";
                        label += isAudioPermitted ? " [AUDIO]" : "";
                    if(!label.equals("")) {
                            ((PackageLog) logObject).addU150PermissionLog("[" + rPackageLine + "]" + label + " [SECURE FOLDER]");
                        }
                        isU150Permit = false;
                    }
                    isAudioPermitted = false;
                    isCamPermitted = false;
                    label = "";
                }
            }
        }
    }

    //미디어 DB 로그 획득
    private static void collectMediaDBLog(String command, String userid, Object logObject, Date startTime, Date endTime) throws Exception {
        //date_added 항목 검색
        //getTime()/1000 하여 DB에 저장된 시간과 단위 맞춤
        String user = "--user " + userid;
        String where = "--where \'date_added BETWEEN " + (startTime.getTime()/1000) + " AND " + (endTime.getTime()/1000) + "\'";
        ProcessBuilder processBuilder = new ProcessBuilder("adb", "shell", "content", "query", user, command, where);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        String initLine = userid.equals("0") ? "User ID: " + userid : "\nUser ID: " + userid;
        if(logObject instanceof ImageDBLog){
            ((ImageDBLog) logObject).addLog(initLine);
        } else if (logObject instanceof VideoDBLog) {
            ((VideoDBLog) logObject).addLog(initLine);
        } else if (logObject instanceof AudioDBLog) {
            ((AudioDBLog) logObject).addLog(initLine);
        }

        while ((line = reader.readLine()) != null) {
            if(logObject instanceof ImageDBLog){
                ((ImageDBLog) logObject).addLog(line);
            } else if (logObject instanceof VideoDBLog) {
                ((VideoDBLog) logObject).addLog(line);
            } else if (logObject instanceof AudioDBLog) {
                ((AudioDBLog) logObject).addLog(line);
            }
        }
    }

    //usagestats 로그에서 보안 폴더 내에서 실행된 카메라 앱 패키지 식별하고 media.camera 로그에 반영(보안 폴더 내 카메라 앱 사용 판별에 사용)
    private static String findUsageStatsResumedInUser150(String mediaCameraLogLine, StringBuilder u150log, String activityType) {
        String ret = "";
        Date mediaLogDate = LogDate.extractLogDate(mediaCameraLogLine);

        if (mediaLogDate != null) {
            Date startRange = new Date(mediaLogDate.getTime() - TimeUnit.SECONDS.toMillis(3));
            Date endRange = new Date(mediaLogDate.getTime() + TimeUnit.SECONDS.toMillis(2));

            // u150log에 대한 로그 라인을 처리
            for (String usageStatsLine : u150log.toString().split("\n")) {
                // 보안 폴더에서 실행된 카메라 앱인지 확인
                if (usageStatsLine.contains("[SECURE FOLDER]") && usageStatsLine.contains(activityType) && usageStatsLine.contains(extractMediaCameraPackageName(mediaCameraLogLine))) {
                    Date usageLogDate = LogDate.extractUsageStatsLogDate(usageStatsLine);

                    if (usageLogDate != null && !usageLogDate.before(startRange) && !usageLogDate.after(endRange)) {
                        ret = "[SECURE FOLDER]";
                    }
                }
            }
        }

        return ret;
    }

    //usagestats 로그에서 기본 카메라 앱 이전에 PAUSED된 패키지를 식별하고 로그에 저장(기본 카메라 앱 실 사용 앱 판별에 사용)
    private static void findCorrespondingUsageStatsLogs(MediaCameraLog mediaCameraLog, StringBuilder u0log, StringBuilder u150log, FilteredMediaCameraLog filteredMediaCameraLog) {
        for (String mediaCameraLine : mediaCameraLog.getLogLines()) {
            //기본 카메라 앱 켜졌을 때의 로그 확인
            if (mediaCameraLine.contains("com.sec.android.app.camera") && mediaCameraLine.contains("[CAM ON]")) {
                Date mediaLogDate = LogDate.extractLogDate(mediaCameraLine);

                if (mediaLogDate != null) {
                    filteredMediaCameraLog.addLog(mediaCameraLine);

                    Date startRange = new Date(mediaLogDate.getTime() - TimeUnit.SECONDS.toMillis(2));
                    Date endRange = new Date(mediaLogDate.getTime());

                    // u0log와 u150log 모두에 대해 처리하기 위한 helper 함수 호출
                    processUsageStatsLogs(u0log.toString().split("\n"), startRange, endRange, filteredMediaCameraLog);
                    processUsageStatsLogs(u150log.toString().split("\n"), startRange, endRange, filteredMediaCameraLog);
                }
            }
        }
    }

    //usagestats 로그 한 줄에서 기본 카메라 앱 이전에 PAUSED된 패키지를 식별하고 로그에 저장(기본 카메라 앱 실 사용 앱 판별에 사용)
    private static void processUsageStatsLogs(String[] usageStatsLines, Date startRange, Date endRange, FilteredMediaCameraLog filteredMediaCameraLog) {
        String lastPausedPackage = null;

        for (String usageStatsLine : usageStatsLines) {
            Date usageLogDate = LogDate.extractUsageStatsLogDate(usageStatsLine);

            if (usageLogDate != null && !usageLogDate.before(startRange) && !usageLogDate.after(endRange)) {
                if (usageStatsLine.contains("type=ACTIVITY_PAUSED") && !usageStatsLine.contains("com.sec.android.app.camera")) {
                    lastPausedPackage = extractUsageStatsPackageName(usageStatsLine);
                }

                if (usageStatsLine.contains("type=ACTIVITY_RESUMED") && usageStatsLine.contains("com.sec.android.app.camera") && lastPausedPackage != null) {
                    filteredMediaCameraLog.addLog("  Last Paused Activity: " + lastPausedPackage + " (" + usageStatsLine.substring(10, 29) + ")");
                    lastPausedPackage = null;
                }
            }
        }
    }

    //usagestats 로그에서 패키지 명 추출 (기본 카메라 앱 실 사용 앱 판별에 사용)
    private static String extractUsageStatsPackageName(String log) {
        // "package=" 위치 찾기
        int packageIndex = log.indexOf("package=");
        if (packageIndex != -1) {
            // "package=" 뒤에서 공백까지 문자열 추출
            int startIndex = packageIndex + 8; // "package=" 길이
            int endIndex = log.indexOf(" ", startIndex); // 다음 공백까지
            if (endIndex == -1) {
                endIndex = log.length(); // 패키지 끝이 로그 끝인 경우
            }
            return log.substring(startIndex, endIndex); // 패키지 이름 추출
        }
        return null; // 패키지 정보가 없으면 null 반환
    }

    //카메라 ON/OFF 로그에서 패키지 명 추출 (카메라 앱 보안 폴더 내 실행 여부 판별에 사용)
    private static String extractMediaCameraPackageName(String log) {
        // "package=" 위치 찾기
        int packageIndex = log.indexOf("package ");
        if (packageIndex != -1) {
            // "package=" 뒤에서 공백까지 문자열 추출
            int startIndex = packageIndex + 8; // "package=" 길이
            int endIndex = log.indexOf(" ", startIndex); // 다음 공백까지
            if (endIndex == -1) {
                endIndex = log.length(); // 패키지 끝이 로그 끝인 경우
            }
            return log.substring(startIndex, endIndex); // 패키지 이름 추출
        }
        return null; // 패키지 정보가 없으면 null 반환
    }

    //logcat 사용을 위한 삼성 디바이스 확인 작업
    private static boolean isSMDevice() throws IOException {
        // ADB uptime command 실행
        ProcessBuilder processBuilder = new ProcessBuilder("adb", "shell", "getprop", "ro.product.model");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();

        return line.charAt(0) == 'S' && line.charAt(1) == 'M';
    }

    //usagestats 로그에서 미디어 권한 가진 패키지에 대한 로그만 필터링
    public static String filterLogs(StringBuilder packageLog, StringBuilder usagestatsLog) {
        // 패키지 로그의 패키지 이름과 권한 라벨을 저장할 Map
        Map<String, String> packagePermissions = new HashMap<>();

        // 패키지 로그에서 패키지 이름과 권한 라벨 추출
        for (String line : packageLog.toString().split("\n")) {
            // 패키지 이름과 권한 라벨 추출
            String[] parts = line.split(" ");
            String packageName = parts[0].replace("[", "").replace("]", "");  // 패키지 이름에서 대괄호 제거
            StringBuilder permissions = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                permissions.append(parts[i]).append(" ");
            }
            packagePermissions.put(packageName, permissions.toString().trim());  // 패키지 이름과 권한 라벨 저장
        }

        // 필터링된 로그를 저장할 StringBuilder
        StringBuilder filteredLogs = new StringBuilder();

        // usagestats 로그에서 패키지 이름이 포함된 로그 필터링하면서 권한 라벨 추가
        for (String activityLog : usagestatsLog.toString().split("\n")) {
            // B 로그에서 패키지 정보를 추출
            String[] logParts = activityLog.split(" ");
            String packageName = null;
            for (String part : logParts) {
                if (part.startsWith("package=")) {
                    packageName = part.split("=")[1];  // 패키지 이름 추출
                    break;
                }
            }

            if (packageName != null && packagePermissions.containsKey(packageName)) {
                // 권한 라벨을 덧붙여서 로그에 추가
                filteredLogs.append(activityLog).append(" ").append(packagePermissions.get(packageName)).append("\n");
            }
        }

        // StringBuilder를 String으로 변환하여 반환
        return filteredLogs.toString();
    }

    //보안 폴더 내 기본 앱 필터링
    static boolean isBaseApp(String line) {
        return (line.contains("com.sec.android") && line.contains("com.sec.android.app.camera")) || line.contains("com.android") || line.contains("com.google") || line.contains("com.samsung") || line.contains("[android]") || line.contains("com.qualcomm");
    }

    public static String findAllUserID() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("adb", "shell", "pm", "list", "users");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        StringBuilder log = new StringBuilder();

        // 사용자 ID를 추출하여 userSet에 저장
        while ((line = reader.readLine()) != null) {
            log.append(line).append("\n");
        }
        return log.toString();
    }

    public static String findSecFolderUserID() throws Exception {
        // usagestats 로그에서 특정 조건에 따라 [SEC] 라벨 추가
        ProcessBuilder usageStatsProcessBuilder = new ProcessBuilder("adb", "shell", "dumpsys", "usagestats");
        usageStatsProcessBuilder.redirectErrorStream(true);
        Process usageStatsProcess = usageStatsProcessBuilder.start();
        BufferedReader usageStatsReader = new BufferedReader(new InputStreamReader(usageStatsProcess.getInputStream()));

        String line;
        while ((line = usageStatsReader.readLine()) != null) {
            if (line.contains("user=") && !line.contains("user=0")) {  // "user=0"은 제외
                String userId = line.substring(line.indexOf("user=") + 5).trim();  // user ID 추출
                String nextLine = usageStatsReader.readLine();  // 다음 라인 읽기

                if (nextLine != null && nextLine.contains("Last 24 hour events")) {
                    return userId;
                }
            }
        }
        return "";
    }
}