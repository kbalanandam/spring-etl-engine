package com.etl.model.source.xml;

import jakarta.xml.bind.annotation.*;

@XmlRootElement(name = "Department")
@XmlAccessorType(XmlAccessType.FIELD)
public class Department {

    public Department() {}

    @XmlElement(name = "id")
    private Integer id;

    @XmlElement(name = "name")
    private String name;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
