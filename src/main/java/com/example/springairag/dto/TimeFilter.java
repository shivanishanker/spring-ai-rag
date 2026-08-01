package com.example.springairag.dto;

import java.time.LocalDateTime;

public class TimeFilter {

    private LocalDateTime start;
    private LocalDateTime end;

    // ✅ ADD THIS CONSTRUCTOR
    public TimeFilter(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    // ✅ REQUIRED: default constructor (for Jackson)
    public TimeFilter() {}

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }
}