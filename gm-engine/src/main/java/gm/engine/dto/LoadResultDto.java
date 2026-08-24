package gm.engine.dto;

public class LoadResultDto {

    private final int eventCount;
    private final int userCount;

    public LoadResultDto(int eventCount, int userCount) {
        this.eventCount = eventCount;
        this.userCount = userCount;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getUserCount() {
        return userCount;
    }
}
