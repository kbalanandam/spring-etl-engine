package com.etl.model.target;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "Departments")
@XmlAccessorType(XmlAccessType.FIELD)
public class Departments {

    @XmlElement(name = "Department")
    private List<Department> department;

    public Departments() {}

    public List<Department> getDepartment() {
        return department;
    }

    public void setDepartment(List<Department> department) {
        this.department = department;
    }

}
