package com.alexdamolidis.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A course's Content section table of contents, as returned by Brightspace's content/toc route.
 * Only the fields used to find assignment instructions are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentToc(@JsonProperty("Modules") List<ContentModule> modules){

    public ContentToc{
        modules = modules != null ? modules : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentModule(
        @JsonProperty("ModuleId") long                moduleId,
        @JsonProperty("Title")    String              title,
        @JsonProperty("Modules")  List<ContentModule> modules,
        @JsonProperty("Topics")   List<ContentTopic>  topics
    ){
        public ContentModule{
            modules = modules != null ? modules : List.of();
            topics  = topics  != null ? topics  : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentTopic(
        @JsonProperty("TopicId")          long    topicId,
        @JsonProperty("Title")            String  title,
        @JsonProperty("TypeIdentifier")   String  typeIdentifier,
        @JsonProperty("Url")              String  url,
        @JsonProperty("IsHidden")         boolean isHidden,
        @JsonProperty("IsBroken")         boolean isBroken,
        @JsonProperty("LastModifiedDate") String  lastModifiedDate
    ){}
}
