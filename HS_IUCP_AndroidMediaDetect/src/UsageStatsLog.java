class UsageStatsLog {
    private StringBuilder u0log = new StringBuilder();
    private StringBuilder u150log = new StringBuilder();

    public void addU0Log(String line) {
        u0log.append(line).append("\n");
    }

    public void addU150Log(String line) {
        u150log.append(line).append("\n");
    }

    public String getLog() {
        return u0log.toString() + u150log.toString();
    }

    public StringBuilder getu0Log() {
        return u0log;
    }

    public StringBuilder getu150Log() {
        return u150log;
    }

    public String[] getu0LogLines() {
        return u0log.toString().split("\n");
    }

    public String[] getu150LogLines() {
        return u150log.toString().split("\n");
    }

}
