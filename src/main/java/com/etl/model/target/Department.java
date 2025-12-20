package com.etl.model.target;

import jakarta.xml.bind.annotation.*;

@XmlRootElement(name = "department")
@XmlAccessorType(XmlAccessType.FIELD)
public class Department {

    public Department() {
        // Default constructor required by JAXB
    }

    @XmlElement(name = "id")
    private int id;

    @XmlElement(name = "name")
    private String name;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
