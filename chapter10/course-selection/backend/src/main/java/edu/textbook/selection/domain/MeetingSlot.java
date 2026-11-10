package edu.textbook.selection.domain;

import jakarta.persistence.*;

/** One weekly inclusive period range, shared by the whole semester. */
@Embeddable
public class MeetingSlot {
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_period", nullable = false)
    private int startPeriod;

    @Column(name = "end_period", nullable = false)
    private int endPeriod;

    protected MeetingSlot() {}

    public MeetingSlot(int dayOfWeek, int startPeriod, int endPeriod) {
        if (dayOfWeek < 1
                || dayOfWeek > 7
                || startPeriod < 1
                || endPeriod < startPeriod
                || endPeriod > 12) throw new IllegalArgumentException("上课时间范围无效");
        this.dayOfWeek = dayOfWeek;
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
    }

    public boolean overlaps(MeetingSlot other) {
        return dayOfWeek == other.dayOfWeek
                && startPeriod <= other.endPeriod
                && other.startPeriod <= endPeriod;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getStartPeriod() {
        return startPeriod;
    }

    public int getEndPeriod() {
        return endPeriod;
    }
}
