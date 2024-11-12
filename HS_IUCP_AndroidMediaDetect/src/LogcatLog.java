public class LogcatLog {
    private StringBuilder log = new StringBuilder();

    public void addLog(String line) {
        log.append(line).append("\n");
    }

    public String getLog() { return log.toString(); }

    public String[] getLogLines() {
        return log.toString().split("\n");
    }
}

