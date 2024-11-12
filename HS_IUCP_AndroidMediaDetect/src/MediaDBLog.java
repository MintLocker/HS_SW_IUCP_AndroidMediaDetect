class ImageDBLog {
    private StringBuilder log = new StringBuilder();

    public void addLog(String line) {
        log.append(line).append("\n");
    }

    public String getLog() {
        return log.toString();
    }
}

class  VideoDBLog{
    private StringBuilder log = new StringBuilder();

    public void addLog(String line) {
        log.append(line).append("\n");
    }

    public String getLog() {
        return log.toString();
    }
}

class AudioDBLog {
    private StringBuilder log = new StringBuilder();

    public void addLog(String line) {
        log.append(line).append("\n");
    }

    public String getLog() {
        return log.toString();
    }
}
