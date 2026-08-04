package com.example.springairag.dto;

public class QueryIntent {

    private String type;
    private String object;
    private boolean useVector;
    private boolean needJoin;
    private String aggregation;
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
    public boolean isUseVector() {
        return useVector;
    }
    public void setUseVector(boolean useVector) {
        this.useVector = useVector;
    }
    public boolean isNeedJoin() {
        return needJoin;
    }
    public void setNeedJoin(boolean needJoin) {
        this.needJoin = needJoin;
    }
    public String getAggregation() {
        return aggregation;
    }
    public void setAggregation(String aggregation) {
        this.aggregation = aggregation;
    }

    // getters & setters
}