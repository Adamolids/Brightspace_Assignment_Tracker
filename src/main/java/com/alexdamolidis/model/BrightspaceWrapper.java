package com.alexdamolidis.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BrightspaceWrapper<T> {


    @JsonProperty("Items")
    private List<T> items;

    public void setItems(List<T> items){
        this.items = items;
    }

    public List<T> getItems(){
        return items;
    }
    
}
