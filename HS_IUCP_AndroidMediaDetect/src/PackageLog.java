class PackageLog {
    // 패키지 로그와 권한 확인 로그를 각각 저장하는 StringBuilder
    private StringBuilder generalLog = new StringBuilder();  // 보안 폴더 로그
    private StringBuilder u0permissionLog = new StringBuilder();  // 권한 관련 로그
    private StringBuilder u150permissionLog = new StringBuilder();  // 권한 및 보안폴더 관련 로그
    private StringBuilder allPackageLog = new StringBuilder(); //모든 패키지 로그

    // 보안 폴더 로그 추가
    public void addGeneralLog(String line) {
        generalLog.append(line).append("\n");
    }

    //모든 패키지 로그 추가
    public void addAllPackageLog(String line) {
        allPackageLog.append(line).append("\n");
    }

    // 권한 확인 및 보안폴더 관련 로그 추가
    public void addU0PermissionLog(String line) {
        u0permissionLog.append(line).append("\n");
    }

    // 권한 확인 및 보안폴더 관련 로그 추가
    public void addU150PermissionLog(String line) {
        u150permissionLog.append(line).append("\n");
    }

    // 일반 로그 반환
    public String getGeneralLog() {
        return generalLog.toString();
    }

    // 권한 로그 반환
    public String getu0PermissionLogTxt() {
        return u0permissionLog.toString();
    }

    public String getu150PermissionLogTxt() {
        return u150permissionLog.toString();
    }

    public StringBuilder getu0PermissionLog() {
        return u0permissionLog;
    }

    public StringBuilder getu150PermissionLog() {
        return u150permissionLog;
    }

    public StringBuilder getAllPackageLog(){
        return allPackageLog;
    }
}