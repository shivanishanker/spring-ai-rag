package com.example.springairag.dto;

import java.time.LocalDateTime;

public class TimeFilter {

    private LocalDateTime start;
    private LocalDateTime end;

    public TimeFilter() {}

    public TimeFilter(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }

    public void setStart(LocalDateTime start) { this.start = start; }
    public void setEnd(LocalDateTime end) { this.end = end; }
}