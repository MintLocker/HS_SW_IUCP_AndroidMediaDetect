import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class LogDate {
    private static final SimpleDateFormat logDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date startDate;
    private static Date endDate;
    private static Date startDateWithoutYear;
    private static Date endDateWithoutYear;

    private static int uptimeMin = 0;

    LogDate() throws Exception {
        Date now = new Date();

        // 10분 전 시간 계산
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.MINUTE, -30);
        Date halfHourAgo = cal.getTime();

        String startDateStr = dateFormat.format(halfHourAgo);
        String endDateStr = dateFormat.format(now);
        this.startDate = dateFormat.parse(startDateStr);
        this.endDate = dateFormat.parse(endDateStr);

        String startDateWithoutYearStr = logDateFormat.format(halfHourAgo);
        String endDateWithoutYearStr = logDateFormat.format(now);

        this.startDateWithoutYear = parseLogDate(startDateWithoutYearStr);
        this.endDateWithoutYear = parseLogDate(endDateWithoutYearStr);
    }

    //로그 시간 처리
    public static Date parseLogDate(String logDate) throws Exception {
        Calendar now = Calendar.getInstance(); // 현재 날짜
        String currentYear = String.valueOf(now.get(Calendar.YEAR)); // 현재 년도
        String fullDateStr = currentYear + "-" + logDate; // 로그에 현재 년도 추가

        SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date parsedDate = fullDateFormat.parse(fullDateStr);

        // 12월 31일과 1월 1일 로그가 섞여 있는지 확인 및 조정
        Calendar logCal = Calendar.getInstance();
        logCal.setTime(parsedDate);

        // 현재 날짜보다 이후라면 (예: 12월 31일에서 1월 1일로 넘어갔을 때)
        if (logCal.after(now) && now.get(Calendar.MONTH) == Calendar.JANUARY) {
            logCal.add(Calendar.YEAR, -1); // 로그의 년도를 작년으로 변경
            return logCal.getTime();
        }

        return parsedDate;
    }


    static boolean isRebooted() throws Exception {
        // ADB uptime command 실행
        ProcessBuilder processBuilder = new ProcessBuilder("adb", "shell", "uptime");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();

// "up "의 위치 찾기
        int upIndex = line.indexOf("up ");
        if (upIndex == -1) {
            throw new Exception();
        }
        int upTimeStart = upIndex + "up ".length();

        // " user" 또는 " users"의 위치 찾기
        int usersIndex = line.indexOf(" user", upTimeStart);
        if (usersIndex == -1) {
            throw new Exception();
        }

        // 업타임 문자열 추출
        String upTimeStr = line.substring(upTimeStart, usersIndex).trim();

        // 업타임 문자열을 ','로 분할
        String[] parts = upTimeStr.split(",");

        for (String part : parts) {
            part = part.trim();
            if (part.contains("min")) {
                // 분 단위 처리
                part = part.replace("min", "").trim();
                int minutes = Integer.parseInt(part);
                uptimeMin += minutes;
            } else if (part.contains("day")) {
                // 일 단위 처리
                part = part.replace("days", "").replace("day", "").trim();
                int days = Integer.parseInt(part);
                uptimeMin += days * 24 * 60;
            } else if (part.contains(":")) {
                // 시간과 분 처리
                String[] hm = part.split(":");
                int hours = Integer.parseInt(hm[0].trim());
                int minutes = Integer.parseInt(hm[1].trim());
                uptimeMin += hours * 60 + minutes;
            }
        }

        // 총 업타임이 30분 이내인지 확인
        return uptimeMin <= 30;
    }

    public int getUptimeMin(){
        return uptimeMin;
    }

    public String getDate(){
        return startDate + " ~ " + endDate + "\n";
    }

    //usagestats 로그 데이터에서 날짜 데이터 추출
    public static Date extractUsageStatsLogDate(String line) {
        try {
            int timeIndex = line.indexOf("time=\"");
            if (timeIndex != -1) {
                String timePart = line.substring(timeIndex + 6, timeIndex + 25).trim();
                Date logDate = dateFormat.parse(timePart);

                String formattedDate = logDateFormat.format(logDate);
                return parseLogDate(formattedDate);
            }
        } catch (Exception e) {}
        return null;
    }

    //audio, media.camera 로그 데이터에서 날짜 데이터 추출
    public static Date extractLogDate(String line) {
        try {
            if (line.length() >= 17) {
                String timePart = line.substring(0, 17).trim();
                return parseLogDate(timePart);
            }
        } catch (Exception e) {}
        return null;
    }

    public static Date getSDwY() { return startDate; }
    public static Date getEDwY() { return endDate; }
    public static Date getSD() { return startDateWithoutYear; }
    public static Date getED() { return endDateWithoutYear; }



}
