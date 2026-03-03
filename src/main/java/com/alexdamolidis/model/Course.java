package com.alexdamolidis.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Course {
    private String  orgUnitId;
    private String  name;
    private Boolean isWorthCredits;
    private List<Assignment> assignments = new ArrayList<>();
    
    public Course(){}

    @JsonProperty("OrgUnit")
    private void unpackOrgUnit(Map<String, Object> orgUnit){
        this.orgUnitId      = orgUnit.get("Id").toString();
        this.name           = (String) orgUnit.get("Name");
        String code         = (String) orgUnit.get("Code");
        this.isWorthCredits = !code.contains("_VC");
    }

    public void setAssignments(List<Assignment> assignments){
        this.assignments = assignments;
    }

    public void addAssignment(Assignment assignment){
        this.assignments.add(assignment);
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }

    public String getName() {
        return name;
    }

    public Boolean getIsWorthCredits(){
        return isWorthCredits;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    

    public void setOrgUnitId(String orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIsWorthCredits(Boolean isWorthCredits) {
        this.isWorthCredits = isWorthCredits;
    }

    @Override
    public String toString() {
        return "Course [orgUnitId=" + orgUnitId + ", name=" + name + ", isWorthCredits=" + isWorthCredits
                + ", assignments=" + assignments + "]";
    }   

    
}
