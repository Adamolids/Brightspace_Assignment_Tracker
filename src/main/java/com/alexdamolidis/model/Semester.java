package com.alexdamolidis.model;

import java.util.ArrayList;
import java.util.List;

public class Semester {
    private String       name;
    private List<Course> courses;

    public Semester(){}

    public Semester(String name){
        this.name = name;
        this.courses = new ArrayList<>();
    }

    public List<Course> getCourses(){
        return this.courses;
    }

    public void setCourses(List<Course> courses){
        this.courses = courses;
    }

    public void addCourse(Course course) {
        courses.add(course);
    }

    public Course getCourseById(int id) {
        return courses.get(id); 
    }

    public String getName(){
        return name;
    }

    @Override
    public String toString() {
        return "Semester [name=" + name + ", courses=" + courses + "]";
    }   
}