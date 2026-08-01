package com.example.springairag.dto;

import java.time.LocalDateTime;

public class QueryIntent {

    private String type;         // COUNT / LIST / SUMMARY / SIMILAR / AGG
    private String object;       // gun, fire, knife
    private LocalDateTime start;
    private LocalDateTime end;
    private boolean useVector;
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getObject() {
        return object;
    }
    public void setObject(String object) {
        this.object = object;
    }
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
    public boolean isUseVector() {
        return useVector;
    }
    public void setUseVector(boolean useVector) {
        this.useVector = useVector;
    }

    // getters & setters
}