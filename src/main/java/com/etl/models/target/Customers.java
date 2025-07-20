package com.etl.models.target;

import jakarta.xml.bind.annotation.*;

@XmlRootElement(name = "customers")
@XmlAccessorType(XmlAccessType.FIELD)
public class Customers {

    @XmlElement(name = "id")
    private int id;

    @XmlElement(name = "name")
    private String name;

    @XmlElement(name = "email")
    private String email;

    @XmlElement(name = "phone")
    private String phone;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

}
